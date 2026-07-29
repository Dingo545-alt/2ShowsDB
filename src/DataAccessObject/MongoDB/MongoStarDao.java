package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.StarDao;
import Model.Movie;
import Model.Photo;
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

        Document photoDoc = doc.get("photo", Document.class);
        if (photoDoc != null) {
            Photo photo = new Photo();
            photo.setPath(photoDoc.getString("path"));
            Document sizes = photoDoc.get("sizes", Document.class);
            if (sizes != null) {
                photo.setW185(sizes.getString("w185"));
                photo.setOriginal(sizes.getString("original"));
            }
            star.setPhoto(photo);
        }

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
