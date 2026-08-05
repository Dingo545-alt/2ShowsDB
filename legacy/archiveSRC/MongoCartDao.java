package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.CartDao;
import Model.CartItemDetails;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import org.bson.Document;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MongoCartDao implements CartDao {

    private final MongoCollection<Document> movieCollection;

    public MongoCartDao() {
        MongoClient   mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database    = mongoClient.getDatabase("moviedb");
        this.movieCollection      = database.getCollection("movies");
    }

    @Override
    public Map<String, CartItemDetails> getCartItemDetails(Collection<String> movieIds){
        Map<String, CartItemDetails> result = new HashMap<>();

        if (movieIds == null || movieIds.isEmpty()) return result;

        movieCollection
                .find(Filters.in("_id", movieIds))
                .projection(Projections.include("_id", "title", "price"))
                .forEach(doc -> {
                    String id    = doc.getString("_id");
                    String title = doc.getString("title");
                    Double price = doc.getDouble("price");
                    result.put(id, new CartItemDetails(
                            title != null ? title : "(unknown)",
                            price != null ? price : 0.0
                    ));
                });

        return result;
    }
}
