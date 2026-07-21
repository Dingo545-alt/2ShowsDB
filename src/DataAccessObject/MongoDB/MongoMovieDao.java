package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieDao;
import Model.Genre;
import Model.Movie;
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

public class MongoMovieDao implements MovieDao {
    private final MongoCollection<Document> movieCollection;
    private final MongoCollection<Document> starCollection;

    public MongoMovieDao() {
        MongoClient mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("moviedb");
        this.movieCollection = database.getCollection("movies");
        this.starCollection = database.getCollection("stars");
    }

    @Override
    public Movie getMovieById(String id) {
        Document doc = movieCollection.find(Filters.eq("_id", id)).first();
        if  (doc == null) return null;

        Movie movie = new Movie();
        movie.setId(doc.getString("_id"));
        movie.setTitle(doc.getString("title"));
        movie.setYear(doc.getInteger("year"));
        movie.setDirector(doc.getString("director"));
        movie.setPrice(doc.getDouble("price"));

        Double ratingVal = doc.getDouble("rating");
        movie.setRating(ratingVal != null ? ratingVal.floatValue() : null);

        Integer votesVal = doc.getInteger("vote_count");
        movie.setVoteCount(votesVal != null ? votesVal : 0);

        List<String> rawGenres = doc.getList("genres", String.class);
        if (rawGenres != null) {
            for (String genreName : rawGenres) {
                movie.getGenres().add(new Genre(0, genreName));
            }
        }

        List<Document> rawStars = doc.getList("stars", Document.class);
        if (rawStars != null && !rawStars.isEmpty()) {
            List<String> starIds = new ArrayList<>();
            for (Document starDoc : rawStars) {
                starIds.add(starDoc.getString("id"));
            }

            Map<String, Integer> starMovieCounts = new HashMap<>();
            for (Document starDoc : starCollection.find(Filters.in("_id", starIds))) {
                List<?> movieList = starDoc.get("movies", List.class);
                int count = (movieList != null) ? movieList.size() : 0;
                starMovieCounts.put(starDoc.getString("_id"), count);
            }

            for (Document starDoc : rawStars) {
                String starId = starDoc.getString("id");
                Star star = new Star();
                star.setId(starDoc.getString("id"));
                star.setName(starDoc.getString("name"));

                int totalCareerMovies = starMovieCounts.getOrDefault(starId, 0);
                star.setMovieCount(totalCareerMovies);

                movie.getStars().add(star);
            }
        }

        return movie;
    }
}
