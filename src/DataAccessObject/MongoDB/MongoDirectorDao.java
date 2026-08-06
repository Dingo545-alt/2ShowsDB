package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.DirectorDao;
import Model.Director;
import Model.Movie;
import Model.Photo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.List;

public class MongoDirectorDao implements DirectorDao {
    private final MongoCollection<Document> directorCollection;

    public MongoDirectorDao() {
        MongoClient   mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database    = mongoClient.getDatabase("moviedb");
        this.directorCollection   = database.getCollection("directors");
    }

    @Override
    public Director getDirectorById(String id) {
        Document doc = directorCollection.find(Filters.eq("_id", id)).first();
        if (doc == null) return null;

        Director director = new Director();
        director.setId(doc.getString("_id"));
        director.setName(doc.getString("name"));

        // dob is an ISO 8601 date string ("YYYY-MM-DD"); null when not known
        director.setDob(doc.getString("dob"));

        Document photoDoc = doc.get("photo", Document.class);
        if (photoDoc != null) {
            Photo photo = new Photo();
            photo.setPath(photoDoc.getString("path"));
            Document sizes = photoDoc.get("sizes", Document.class);
            if (sizes != null) {
                photo.setW185(sizes.getString("w185"));
                photo.setOriginal(sizes.getString("original"));
            }
            director.setPhoto(photo);
        }

        // Embedded movies array - already sorted year DESC, title ASC by the ETL
        List<Document> rawMovies = doc.getList("movies", Document.class);
        if (rawMovies != null) {
            for (Document movieDoc : rawMovies) {
                Movie movie = new Movie();
                movie.setId(movieDoc.getString("id"));
                movie.setTitle(movieDoc.getString("title"));
                movie.setYear(movieDoc.getInteger("year", 0));
                director.getMovies().add(movie);
            }
        }

        return director;
    }
}