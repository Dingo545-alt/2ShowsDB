package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.StarDao;
import Model.Movie;
import Model.Star;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.List;

public class MongoStarDao implements StarDao {
    private final MongoCollection<Document> starCollection;

    public MongoStarDao() {
        MongoClient   mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database    = mongoClient.getDatabase("moviedb");
        this.starCollection       = database.getCollection("stars");
    }

    @Override
    public Star getStarById(String id) {
        Document doc = starCollection.find(Filters.eq("_id", id)).first();
        if (doc == null) return null;

        Star star = new Star();
        star.setId(doc.getString("_id"));
        star.setName(doc.getString("name"));

        // dob is an ISO 8601 date string ("YYYY-MM-DD"); null when not known
        star.setDob(doc.getString("dob"));

        // Embedded movies array - alr sorted year DESC, title ASC by migration
        List<Document> rawMovies = doc.getList("movies", Document.class);
        if (rawMovies != null) {
            for (Document movieDoc : rawMovies) {
                Movie movie = new Movie();
                movie.setId(movieDoc.getString("id"));
                movie.setTitle(movieDoc.getString("title"));
                movie.setYear(movieDoc.getInteger("year", 0));
                star.getMovies().add(movie);
            }
        }

        return star;
    }
}
