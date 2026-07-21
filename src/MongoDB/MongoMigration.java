package MongoDB;

import Config.AppConfig;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

import java.sql.*;
import java.util.*;

/**
 *  Migrates MqSQL to MongoDB
 *
 *  Creates the following collections:
 *      movies     - embedded genres (string[]) + stars({id, name}[]), rating fields
 *      stars      - embedded movies ({id,title,year}[])
 *      customers  - embedded credit_card document
 *      sales      - references customer_id + movie_id, includes price_at_sale snapshot
 *      employees  - flat document, email is _id
 *
 *  Run order is important:
 *     1. movies  (needs genres & ratings joined in)
 *     2. stars   (needs stars_in_movies + movies for the embedded movie list)
 *     3. customers (needs credit_cards joined in)
 *     4. sales
 *     5. employees
 */

public class MongoMigration {
    // ------------------------ Config ------------------------
    // All values below come from environment variables or config.properties (see config.properties.example).
    private static final String MYSQL_URL = AppConfig.get("MYSQL_URL",
            "jdbc:mysql://localhost:3306/moviedb?useSSL=false&allowPublicKeyRetrieval=true");
    private static final String MYSQL_USER = AppConfig.require("MYSQL_USER");
    private static final String MYSQL_PASS = AppConfig.require("MYSQL_PASS");

    private static final String MONGO_URI  = AppConfig.get("MONGO_URI", "mongodb://localhost:27017");
    private static final String MONGO_DB   = AppConfig.get("MONGO_DB", "moviedb");

    private static final int BATCH_SIZE = 500;

    public static void main(String[] args) {
        System.out.println("=== MySQL → MongoDB Migration ===\n");

        try (Connection mysql = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
             MongoClient mongo = MongoClients.create(MONGO_URI)) {

            MongoDatabase db = mongo.getDatabase(MONGO_DB);

            // Drop existing collections
            System.out.println("[Setup] Dropping existing collections...");
            for (String name : List.of("movies", "stars", "customers", "sales", "employees")) {
                db.getCollection(name).drop();
            }

            migrateMovies(mysql, db);
            migrateStars(mysql, db);
            migrateCustomers(mysql, db);
            migrateSales(mysql, db);
            migrateEmployees(mysql, db);
            createIndexes(db);

            System.out.println("\n=== Migration complete. ===");

        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ------------------ movies ------------------
     * Example document inserted into the "movies" collection:
     *
     * {
     *   "_id":      "tt0111161",
     *   "title":    "The Shawshank Redemption",
     *   "year":     1994,
     *   "director": "Frank Darabont",
     *   "price":    14.99,
     *   "rating":   9.3,
     *   "vote_count": 2900000,
     *   "genres": ["Drama"],
     *   "stars": [
     *     { "id": "nm0000209", "name": "Morgan Freeman"  },
     *     { "id": "nm0000151", "name": "Tim Robbins"     }
     *   ]
     * }
     *
     * Notes:
     *   - rating and vote_count are null for movies with no entry in the ratings table.
     *   - genres is alphabetically sorted.
     *   - stars are ordered by total career movie count DESC, then name ASC.
     */
    private static void migrateMovies(Connection mysql, MongoDatabase db) throws SQLException {
        System.out.println("[movies] Starting...");
        MongoCollection<Document> col = db.getCollection("movies");

        // ---- 1. Core movie data + rating (single pass, LEFT JOIN) ----
        String movieSql =
                "SELECT m.id, m.title, m.year, m.director, m.price, " +
                        "       r.rating, r.vote_count " +
                        "FROM movies m LEFT JOIN ratings r ON m.id = r.movie_id " +
                        "ORDER BY m.id";

        Map<String, Document> movieMap = new LinkedHashMap<>();

        try (PreparedStatement ps = mysql.prepareStatement(movieSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document doc = new Document("_id", rs.getString("id"))
                        .append("title",    rs.getString("title"))
                        .append("year",     rs.getInt("year"))
                        .append("director", rs.getString("director"))
                        .append("price",    rs.getDouble("price"))
                        .append("genres",   new ArrayList<String>())
                        .append("stars",    new ArrayList<Document>());

                float rating = rs.getFloat("rating");
                if (rs.wasNull()) {
                    doc.append("rating",     null);
                    doc.append("vote_count", null);
                } else {
                    doc.append("rating",     rating);
                    doc.append("vote_count", rs.getInt("vote_count"));
                }
                movieMap.put(rs.getString("id"), doc);
            }
        }
        System.out.println("  Loaded " + movieMap.size() + " movies from MySQL.");

        // ---- 2. Attach genres (alphabetical per movie) ----
        String genreSql =
                "SELECT gim.movie_id, g.name " +
                        "FROM genres_in_movies gim JOIN genres g ON gim.genre_id = g.id " +
                        "ORDER BY gim.movie_id, g.name";

        try (PreparedStatement ps = mysql.prepareStatement(genreSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document movie = movieMap.get(rs.getString("movie_id"));
                if (movie != null) {
                    movie.getList("genres", String.class).add(rs.getString("name"));
                }
            }
        }

        // ---- 3. Attach top stars (by movie-count DESC, name ASC per movie) ----
        String starsSql =
                "SELECT sim.movie_id, s.id AS star_id, s.name AS star_name, " +
                        "       COUNT(sim2.movie_id) AS movie_count " +
                        "FROM stars_in_movies sim " +
                        "JOIN stars s              ON s.id  = sim.star_id " +
                        "JOIN stars_in_movies sim2 ON s.id  = sim2.star_id " +
                        "GROUP BY sim.movie_id, s.id, s.name " +
                        "ORDER BY sim.movie_id, movie_count DESC, s.name ASC";

        try (PreparedStatement ps = mysql.prepareStatement(starsSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document movie = movieMap.get(rs.getString("movie_id"));
                if (movie != null) {
                    Document starStub = new Document("id",   rs.getString("star_id"))
                            .append("name", rs.getString("star_name"));
                    movie.getList("stars", Document.class).add(starStub);
                }
            }
        }

        // ---- 4. Batch insert into MongoDB ----
        insertInBatches(col, new ArrayList<>(movieMap.values()), "movies");
    }

    /**
     * ------------------ stars ------------------
     * Example document inserted into the "stars" collection:
     *
     * {
     *   "_id":        "nm0000151",
     *   "name":       "Tim Robbins",
     *   "birth_year": 1958,
     *   "movies": [
     *     { "id": "tt0111161", "title": "The Shawshank Redemption", "year": 1994 },
     *     { "id": "tt0245429", "title": "Mystic River",             "year": 2003 }
     *   ]
     * }
     *
     * Notes:
     *   - birth_year is null when not present in the source data.
     *   - movies are ordered by year DESC, then title ASC.
     */
    private static void migrateStars(Connection mysql, MongoDatabase db) throws SQLException {
        System.out.println("[stars] Starting...");
        MongoCollection<Document> col = db.getCollection("stars");

        // ---- 1. Core star data ----
        Map<String, Document> starMap = new LinkedHashMap<>();
        try (PreparedStatement ps = mysql.prepareStatement(
                "SELECT id, name, birth_year FROM stars ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document doc = new Document("_id",  rs.getString("id"))
                        .append("name",       rs.getString("name"))
                        .append("movies",     new ArrayList<Document>());

                int by = rs.getInt("birth_year");
                doc.append("birth_year", rs.wasNull() ? null : by);

                starMap.put(rs.getString("id"), doc);
            }
        }
        System.out.println("  Loaded " + starMap.size() + " stars from MySQL.");

        // ---- 2. Attach movie stubs (year DESC, title ASC) ----
        String moviesSql =
                "SELECT sim.star_id, m.id AS movie_id, m.title, m.year " +
                        "FROM stars_in_movies sim JOIN movies m ON sim.movie_id = m.id " +
                        "ORDER BY sim.star_id, m.year DESC, m.title ASC";

        try (PreparedStatement ps = mysql.prepareStatement(moviesSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document star = starMap.get(rs.getString("star_id"));
                if (star != null) {
                    Document movieStub = new Document("id",    rs.getString("movie_id"))
                            .append("title", rs.getString("title"))
                            .append("year",  rs.getInt("year"));
                    star.getList("movies", Document.class).add(movieStub);
                }
            }
        }

        insertInBatches(col, new ArrayList<>(starMap.values()), "stars");
    }

    /**
     * ------------------ customers ------------------
     * Example document inserted into the "customers" collection:
     *
     * {
     *   "_id":        1,
     *   "first_name": "Jane",
     *   "last_name":  "Doe",
     *   "email":      "jane@example.com",
     *   "password":   "$2a$10$encryptedPasswordHash...",
     *   "address":    "123 Main St, Los Angeles, CA",
     *   "credit_card": {
     *     "id":         "4111111111111111",
     *     "first_name": "Jane",
     *     "last_name":  "Doe",
     *     "expiration": "2027-06-30"
     *   }
     * }
     *
     * Notes:
     *   - credit_card is fully embedded since it is never queried independently of its owner.
     *   - expiration is stored as a "YYYY-MM-DD" string to match the format PaymentServlet expects.
     */
    private static void migrateCustomers(Connection mysql, MongoDatabase db) throws SQLException {
        System.out.println("[customers] Starting...");
        MongoCollection<Document> col = db.getCollection("customers");

        String sql =
                "SELECT c.id, c.first_name, c.last_name, c.email, c.password, c.address, " +
                        "       cc.id AS cc_id, cc.first_name AS cc_first, cc.last_name AS cc_last, " +
                        "       cc.expiration " +
                        "FROM customers c JOIN credit_cards cc ON c.credit_card_id = cc.id " +
                        "ORDER BY c.id";

        List<Document> docs = new ArrayList<>();
        try (PreparedStatement ps = mysql.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document creditCard = new Document("id",         rs.getString("cc_id"))
                        .append("first_name",  rs.getString("cc_first"))
                        .append("last_name",   rs.getString("cc_last"))
                        .append("expiration",  rs.getString("expiration")); // keep as "YYYY-MM-DD" string

                Document doc = new Document("_id",        rs.getInt("id"))
                        .append("first_name",  rs.getString("first_name"))
                        .append("last_name",   rs.getString("last_name"))
                        .append("email",       rs.getString("email"))
                        .append("password",    rs.getString("password"))
                        .append("address",     rs.getString("address"))
                        .append("credit_card", creditCard);

                docs.add(doc);
            }
        }

        insertInBatches(col, docs, "customers");
    }

    /**
     * ------------------ sales ------------------
     * Example document inserted into the "sales" collection:
     *
     * {
     *   "_id":          42,
     *   "customer_id":  1,
     *   "movie_id":     "tt0111161",
     *   "sale_date":    "2024-03-15",
     *   "quantity":     2,
     *   "price_at_sale": 14.99
     * }
     *
     * Notes:
     *   - customer_id and movie_id are references, not embedded documents.
     *     Embedding would risk stale data if prices or titles change after purchase.
     *   - price_at_sale is snapshotted from movies.price at migration time so the
     *     historical price is preserved even if the movie's price is updated later.
     */
    private static void migrateSales(Connection mysql, MongoDatabase db) throws SQLException {
        System.out.println("[sales] Starting...");
        MongoCollection<Document> col = db.getCollection("sales");

        String sql =
                "SELECT s.id, s.customer_id, s.movie_id, s.sale_date, s.quantity, m.price " +
                        "FROM sales s JOIN movies m ON s.movie_id = m.id " +
                        "ORDER BY s.id";

        List<Document> docs = new ArrayList<>();
        try (PreparedStatement ps = mysql.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document doc = new Document("_id",          rs.getInt("id"))
                        .append("customer_id",   rs.getInt("customer_id"))
                        .append("movie_id",      rs.getString("movie_id"))
                        .append("sale_date",     rs.getString("sale_date"))
                        .append("quantity",      rs.getInt("quantity"))
                        .append("price_at_sale", rs.getDouble("price"));
                docs.add(doc);
            }
        }

        insertInBatches(col, docs, "sales");
    }

    // ------------------------------------------------------------------ employees
    /**
     * Example document inserted into the "employees" collection:
     *
     * {
     *   "_id":      "admin@example.com",
     *   "password": "$2a$10$encryptedPasswordHash...",
     *   "fullname": "Admin User"
     * }
     *
     * Notes:
     *   - email is used as _id since it is already the natural primary key in MySQL.
     */
    private static void migrateEmployees(Connection mysql, MongoDatabase db) throws SQLException {
        System.out.println("[employees] Starting...");
        MongoCollection<Document> col = db.getCollection("employees");

        List<Document> docs = new ArrayList<>();
        try (PreparedStatement ps = mysql.prepareStatement(
                "SELECT email, password, fullname FROM employees");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Document doc = new Document("_id",      rs.getString("email"))
                        .append("password", rs.getString("password"))
                        .append("fullname", rs.getString("fullname"));
                docs.add(doc);
            }
        }

        insertInBatches(col, docs, "employees");
    }

    /**
     * Creates indexes to match the query patterns in the existing servlets:
     */
    private static void createIndexes(MongoDatabase db) {
        System.out.println("[indexes] Creating indexes...");

        MongoCollection<Document> movies = db.getCollection("movies");
        movies.createIndex(Indexes.ascending("title"));
        movies.createIndex(Indexes.ascending("year"));
        movies.createIndex(Indexes.ascending("director"));
        movies.createIndex(Indexes.ascending("stars.id"));
        movies.createIndex(Indexes.ascending("genres"));
        // Compound index for the two sort axes used by MovieListServlet
        movies.createIndex(Indexes.compoundIndex(
                Indexes.descending("rating"),
                Indexes.ascending("title")));

        movies.createIndex(Indexes.text("title"));

        MongoCollection<Document> stars = db.getCollection("stars");
        stars.createIndex(Indexes.ascending("name"));
        stars.createIndex(Indexes.ascending("movies.id"));

        MongoCollection<Document> customers = db.getCollection("customers");
        customers.createIndex(Indexes.ascending("email"),
                new IndexOptions().unique(true));
        customers.createIndex(Indexes.ascending("credit_card.id"));

        MongoCollection<Document> sales = db.getCollection("sales");
        sales.createIndex(Indexes.ascending("customer_id"));
        sales.createIndex(Indexes.ascending("movie_id"));

        System.out.println("[indexes] Done.");
    }

    private static void insertInBatches(MongoCollection<Document> col,
                                        List<Document> docs,
                                        String label) {
        if (docs.isEmpty()) {
            System.out.println("  [" + label + "] No documents to insert.");
            return;
        }

        int total = 0;
        for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
            List<Document> batch = docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()));
            col.insertMany(batch);
            total += batch.size();
        }
        System.out.println("  [" + label + "] Inserted " + total + " documents.");
    }
}