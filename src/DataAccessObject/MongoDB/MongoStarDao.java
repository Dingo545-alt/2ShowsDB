package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.StarDao;
import Model.Movie;
import Model.Photo;
import Model.Poster;
import Model.Star;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MongoStarDao implements StarDao {
    private final MongoCollection<Document> starCollection;
    private final MongoCollection<Document> movieCollection;

    public MongoStarDao() {
        MongoClient   mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database    = mongoClient.getDatabase("moviedb");
        this.starCollection       = database.getCollection("stars");
        this.movieCollection      = database.getCollection("movies");
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

        star.setBiography(doc.getString("biography"));

        // Embedded movies array - alr sorted year DESC, title ASC by migration.
        // Only id/title/year live on the star doc, so posters are backfilled with a
        // single batch lookup against the movies collection (same join-on-read pattern
        // MongoMovieDao uses to enrich its embedded stars[] with photo/movie count).
        List<Document> rawMovies = doc.getList("movies", Document.class);
        if (rawMovies != null && !rawMovies.isEmpty()) {
            List<String> movieIds = new ArrayList<>();
            for (Document movieDoc : rawMovies) {
                movieIds.add(movieDoc.getString("id"));
            }

            Map<String, Poster> postersByMovieId = new HashMap<>();
            for (Document fullMovieDoc : movieCollection.find(Filters.in("_id", movieIds))) {
                postersByMovieId.put(fullMovieDoc.getString("_id"), parsePoster(fullMovieDoc.get("poster", Document.class)));
            }

            for (Document movieDoc : rawMovies) {
                Movie movie = new Movie();
                movie.setId(movieDoc.getString("id"));
                movie.setTitle(movieDoc.getString("title"));
                movie.setYear(movieDoc.getInteger("year", 0));
                movie.setPoster(postersByMovieId.get(movie.getId()));
                star.getMovies().add(movie);
            }
        }

        return star;
    }

    private Poster parsePoster(Document posterDoc) {
        if (posterDoc == null) return null;
        Poster poster = new Poster();
        poster.setPath(posterDoc.getString("path"));
        Document sizes = posterDoc.get("sizes", Document.class);
        if (sizes != null) {
            poster.setW342(sizes.getString("w342"));
            poster.setOriginal(sizes.getString("original"));
        }
        return poster;
    }
}
