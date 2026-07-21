package Parser;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DbWriter implements Runnable{
    private static final int BATCH_COMMIT_SIZE = 1000;
    private static final int MAX_DEADLOCK_RETRIES = 5;

    private final BlockingQueue<DataRecords.DataRecord> queue;
    private final DataSource dataSource;
    private final int writerId;
    private final int totalProducerCount;

    private static final AtomicInteger globalTotal = new AtomicInteger(0);

    public DbWriter(BlockingQueue<DataRecords.DataRecord> queue,
                    DataSource dataSource,
                    int writerId,
                    int totalProducerCount) {
        this.queue              = queue;
        this.dataSource         = dataSource;
        this.writerId           = writerId;
        this.totalProducerCount = totalProducerCount;
    }

    @Override
    public void run() {
        System.out.printf("[Writer-%d] Started.%n", writerId);
        int localCount  = 0;
        int poisonsSeen = 0;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            while (poisonsSeen < totalProducerCount) {
                DataRecords.DataRecord record = queue.take();

                if (record.isPoisonPill) {
                    conn.commit();
                    poisonsSeen++;
                    System.out.printf("[Writer-%d] Poison pill %d/%d received.%n",
                            writerId, poisonsSeen, totalProducerCount);
                    continue;
                }

                Savepoint sp = null;
                try {
                    sp = conn.setSavepoint();
                    processRecordWithRetry(conn, record);
                    localCount++;

                    if (localCount % BATCH_COMMIT_SIZE == 0) {
                        conn.commit();
                        System.out.printf("[Writer-%d] Committed batch. Global total: %d%n",
                                writerId, globalTotal.addAndGet(BATCH_COMMIT_SIZE));
                    }
                } catch (Exception e) {
                    System.err.printf("[Writer-%d] Error on %s: %s%n",
                            writerId, record, e.getMessage());
                    InconsistencyLogger.INSTANCE.log("db",
                            "Failed insert: " + record + " — " + e.getMessage());

                    // Roll back ONLY to the savepoint, preserving everything before it in the current transaction.
                    if (sp != null) {
                        try {
                            conn.rollback(sp);
                        } catch (Exception re) {
                            // If savepoint rollback fails (rare — usually means
                            // the connection itself is in a bad state), fall
                            // back to a full rollback as a safety net.
                            try { conn.rollback(); } catch (Exception ignored) {}
                        }
                    }
                }
            }

            conn.commit(); // final partial batch
            int rem = localCount % BATCH_COMMIT_SIZE;
            if (rem != 0) globalTotal.addAndGet(rem);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.printf("[Writer-%d] Fatal DB error: %s%n", writerId, e.getMessage());
        }

        System.out.printf("[Writer-%d] Finished. Records written: %d%n", writerId, localCount);
    }

    // -- dispatcher ------------------------------------------------------------------------------------
    private void processRecord(Connection conn, DataRecords.DataRecord r) throws Exception {
        if      (r instanceof DataRecords.ActorRecord) processActor(conn, (DataRecords.ActorRecord) r);
        else if (r instanceof DataRecords.MovieRecord) processMovie(conn, (DataRecords.MovieRecord) r);
        else if (r instanceof DataRecords.CastRecord)  processCast (conn, (DataRecords.CastRecord)  r);
    }

    // -- stored procedure calls -------------------------------------------------------------------------
    private void processActor(Connection conn, DataRecords.ActorRecord r) throws Exception {
        try (CallableStatement cs = conn.prepareCall("{CALL upsert_star(?, ?)}")) {
            cs.setString(1, r.stageName);
            setIntOrNull(cs, 2, r.birthYear);
            cs.execute();
        }
    }

    private void processMovie(Connection conn, DataRecords.MovieRecord r) throws Exception {
        try (CallableStatement cs = conn.prepareCall("{CALL upsert_movie(?, ?, ?)}")) {
            setStringOrNull(cs, 1, r.title);
            setIntOrNull   (cs, 2, r.year);
            setStringOrNull(cs, 3, r.director);
            cs.execute();
        }
        for (String code : r.rawGenreCodes) {
            linkGenre(conn, r.title, code);
        }
    }

    private void linkGenre(Connection conn, String movieTitle, String genreCode) throws Exception {
        try (CallableStatement cs = conn.prepareCall("{CALL link_genre_to_movie(?, ?)}")) {
            cs.setString(1, movieTitle);
            cs.setString(2, genreCode);
            cs.execute();
        }
    }

    private void processCast(Connection conn, DataRecords.CastRecord r) throws Exception {
        // Verify the movie exists before attempting the link
        boolean movieExists;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM movies WHERE title = ? LIMIT 1")) {
            ps.setString(1, r.movieTitle);
            try (ResultSet rs = ps.executeQuery()) {
                movieExists = rs.next();
            }
        }

        if (!movieExists) {
            InconsistencyLogger.INSTANCE.log("cast",
                    "Movie '" + r.movieTitle + "' not in DB — cannot link actor '"
                            + r.actorName + "'");
            return;
        }

        try (CallableStatement cs = conn.prepareCall("{CALL upsert_cast(?, ?)}")) {
            cs.setString(1, r.movieTitle);
            cs.setString(2, r.actorName);
            cs.execute();
        }
    }


    private static void setStringOrNull(CallableStatement cs, int i, String v) throws Exception {
        if (v != null) cs.setString(i, v); else cs.setNull(i, Types.VARCHAR);
    }

    private static void setIntOrNull(CallableStatement cs, int i, Integer v) throws Exception {
        if (v != null) cs.setInt(i, v); else cs.setNull(i, Types.INTEGER);
    }

    private void processRecordWithRetry(java.sql.Connection conn,
                                        DataRecords.DataRecord record) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                processRecord(conn, record);
                return; // success
            } catch (java.sql.SQLException e) {
                if (!isDeadlock(e) || attempt >= MAX_DEADLOCK_RETRIES) {
                    throw e; // not a deadlock, or out of retries — let outer catch handle it
                }

                attempt++;
                // Roll back so the transaction is in a clean state before retrying
                try { conn.rollback(); } catch (Exception ignored) {}

                // Log the retry so we can see how often deadlocks happen
                InconsistencyLogger.INSTANCE.log("deadlock",
                        String.format("Retry %d/%d for %s — %s",
                                attempt, MAX_DEADLOCK_RETRIES, record, e.getMessage()));

                System.err.printf("[Writer-%d] Deadlock detected, retry %d/%d for %s%n",
                        writerId, attempt, MAX_DEADLOCK_RETRIES, record);

                // Brief randomised backoff (10-60 ms) so retries don't immediately collide
                try {
                    Thread.sleep(10 + (long)(Math.random() * 50));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /**
     * True if the SQLException represents a deadlock or lock-wait timeout that can be safely retried.
     */
    private static boolean isDeadlock(java.sql.SQLException e) {

        java.sql.SQLException current = e;
        while (current != null) {
            if ("40001".equals(current.getSQLState())) return true;
            int code = current.getErrorCode();
            if (code == 1213 || code == 1205) return true;
            current = current.getNextException();
        }
        return false;
    }

}
