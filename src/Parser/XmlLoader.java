package Parser;
import Config.AppConfig;
import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/*
 * This is the file to run to parse XML and load it into Database
 *
 * BEFORE RUNNING:
 *     1) Run GenreCodeScanner.java to see all the <cat> codes in the XML
 *     2) Fill in genre_code_map in stored_procedures.sql for any unmapped codes
 *     3) Run stored_procedures.sql against our database
 *     4) Set MYSQL_USER / MYSQL_PASS (env var or config.properties, see config.properties.example)
 *
 * Loads in 2 phases
 *   Phase 1
 *     - movies + actors first
 *     - runs in parallel in different threads
 *     - each stops after 2 poison pills (1 per)
 *   Phase 2
 *     - casts
 *     - one thread, stops after 1 poison pill
 */
public class XmlLoader {
    private static final String ACTORS_XML = "XmlDataFiles/actors63.xml";
    private static final String MAINS_XML  = "XmlDataFiles/mains243.xml";
    private static final String CASTS_XML  = "XmlDataFiles/casts124.xml";

    private static final int NUM_DB_WRITERS = 8;
    private static final int QUEUE_CAPACITY = 5000;

    public static void main(String[] args) throws InterruptedException {

        DataSource dataSource = buildDataSource(
                AppConfig.get("MYSQL_HOST", "localhost"),
                Integer.parseInt(AppConfig.get("MYSQL_PORT", "3306")),
                AppConfig.get("MYSQL_DB", "moviedb"),
                AppConfig.require("MYSQL_USER"),
                AppConfig.require("MYSQL_PASS")
        );

        // ----------------------------- Phase 1: movies + actors -----------------------------
        System.out.println("\n=== PHASE 1: Loading movies(just movies for now) and actors ===");
        BlockingQueue<DataRecords.DataRecord> phase1Queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        List<Thread> writers1 = startWriters(phase1Queue, dataSource, 2);
        Thread movieProducer = new Thread(
                new MovieSaxParser(MAINS_XML,  phase1Queue, NUM_DB_WRITERS), "movie-producer");
        Thread actorProducer = new Thread(
                new ActorSaxParser(ACTORS_XML, phase1Queue, NUM_DB_WRITERS), "actor-producer");
        movieProducer.start();
        actorProducer.start();
        movieProducer.join();
        actorProducer.join();

        System.out.println("[Main] Phase 1 producers done. Waiting for writers...");
        for (Thread w : writers1) w.join();
        System.out.println("[Main] Phase 1 complete.\n");

        // ----------------------------- PHASE 2: casts -----------------------------
        System.out.println("=== PHASE 2: Loading cast relationships ===");
        BlockingQueue<DataRecords.DataRecord> phase2Queue =
                new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        List<Thread> writers2 = startWriters(phase2Queue, dataSource, 1);

        Thread castProducer = new Thread(
                new CastSaxParser(CASTS_XML, phase2Queue, NUM_DB_WRITERS), "cast-producer");
        castProducer.start();
        castProducer.join();

        System.out.println("[Main] Phase 2 producer done. Waiting for writers...");
        for (Thread w : writers2) w.join();
        System.out.println("[Main] Phase 2 complete.\n");

        InconsistencyLogger.INSTANCE.writeReport("load_report.txt");

        System.out.printf("[Main] Total inconsistencies recorded: %,d (see load_report.txt)%n",
                InconsistencyLogger.INSTANCE.totalCount());

        System.out.println("=== XML loading finished. ===");

    }

    private static List<Thread> startWriters(
            BlockingQueue<DataRecords.DataRecord> queue,
            DataSource ds, int producerCount) {

        List<Thread> writers = new ArrayList<>();
        for (int i = 0; i < NUM_DB_WRITERS; i++) {
            Thread t = new Thread(
                    new DbWriter(queue, ds, i + 1, producerCount),
                    "db-writer-" + (i + 1));
            writers.add(t);
            t.start();
        }
        return writers;
    }

    private static DataSource buildDataSource(String host, int port, String db,
                                              String user, String pass) {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setServerName(host);
        ds.setPort(port);
        ds.setDatabaseName(db);
        ds.setUser(user);
        ds.setPassword(pass);

        try {
            ds.setRewriteBatchedStatements(true);
            ds.setUseSSL(false);
            ds.setAllowPublicKeyRetrieval(true);
            ds.setConnectTimeout(10_000);
            ds.setSocketTimeout(120_000);
        } catch (Exception e) {
            System.err.println("[Main] DataSource warning: " + e.getMessage());
        }
        return ds;
    }
}