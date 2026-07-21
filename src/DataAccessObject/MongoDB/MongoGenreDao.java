package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.GenreDao;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MongoGenreDao implements GenreDao {
    private final MongoCollection<Document> movieCollection;

    public MongoGenreDao() {
        MongoClient mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("moviedb");
        this.movieCollection = database.getCollection("movies");
    }

    @Override
    public List<String> getAllGenreNames() {
        List<String> uniqueGenres = new ArrayList<>();

        Bson filter = Filters.and(
                Filters.exists("genres"),
                Filters.ne("genres", null),
                Filters.not(Filters.size("genres", 0))
        );

        movieCollection.distinct("genres", filter, String.class)
                .into(uniqueGenres);

        Collections.sort(uniqueGenres);

        return uniqueGenres;
    }

}
