package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.User;
import com.mongodb.DuplicateKeyException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;

public class MongoUserDao implements UserDao {
    private final MongoCollection<Document> userCollection;

    public MongoUserDao() {
        MongoClient mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("moviedb");
        this.userCollection = database.getCollection("users");
    }

    @Override
    public boolean createUser(User user) {
        Document doc = new Document("_id", user.getUsername())
                .append("password", user.getPassword());
        try {
            userCollection.insertOne(doc);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public String getPasswordForUsername(String username) {
        Document doc = userCollection.find(Filters.eq("_id", username))
                .projection(Projections.include("password"))
                .first();
        return doc != null ? doc.getString("password") : null;
    }

    @Override
    public User getUserByUsername(String username) {
        Document doc = userCollection.find(Filters.eq("_id", username)).first();
        if (doc == null) return null;
        return new User(doc.getString("_id"), doc.getString("password"));
    }
}