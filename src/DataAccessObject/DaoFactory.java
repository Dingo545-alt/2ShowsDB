package DataAccessObject;

import Config.AppConfig;
import DataAccessObject.Interfaces.*;
import DataAccessObject.MongoDB.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public class DaoFactory {
    private static final String DATABASE_TYPE = "MONGODB";
    private static MongoClient mongoClient = null;

    public static synchronized MongoClient getMongoClient() {
        if (mongoClient == null) {
            // Defaults to localhost for local dev; set MONGO_URI (env var or config.properties)
            // to point at a real cluster — that's where a real connection string with
            // credentials would live, so keep it out of source control.
            mongoClient = MongoClients.create(AppConfig.get("MONGO_URI", "mongodb://localhost:27017"));
        }
        return mongoClient;
    }

    public static UserDao getUserDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoUserDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }

    public static GenreDao getGenreDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoGenreDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }

    public static MovieDao getMovieDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoMovieDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }

    public static MovieListDao getMovieListDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoMovieListDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }

    public static StarDao getStarDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoStarDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }

    public static CartDao getCartDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoCartDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }

    public static PaymentDao getPaymentDao() {
        if (DATABASE_TYPE.equals("MONGODB")) {
            return new MongoPaymentDao();
        }
        throw new IllegalArgumentException("Database type not supported");
    }
}