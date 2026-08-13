package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.Movie;
import Model.Poster;
import Model.User;
import com.mongodb.DuplicateKeyException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoUserDao implements UserDao {
    private static final int MAX_FAVORITES = 3;

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

    @Override
    public boolean addFavorite(String username, Movie movie) {
        Document userDoc = userCollection.find(Filters.eq("_id", username))
                .projection(Projections.include("favorites"))
                .first();
        if (userDoc == null) return false;

        List<Document> favorites = userDoc.getList("favorites", Document.class);
        if (favorites == null) favorites = new ArrayList<>();

        boolean alreadyFavorited = favorites.stream()
                .anyMatch(fav -> movie.getId().equals(fav.getString("id")));
        if (alreadyFavorited) return true;

        if (favorites.size() >= MAX_FAVORITES) return false;

        Document favoriteDoc = movieToDocument(movie);
        userCollection.updateOne(Filters.eq("_id", username), Updates.push("favorites", favoriteDoc));
        return true;
    }

    @Override
    public void removeFavorite(String username, String movieId) {
        userCollection.updateOne(Filters.eq("_id", username),
                Updates.pull("favorites", Filters.eq("id", movieId)));
    }

    @Override
    public List<Movie> getFavorites(String username) {
        Document userDoc = userCollection.find(Filters.eq("_id", username))
                .projection(Projections.include("favorites"))
                .first();

        List<Movie> favorites = new ArrayList<>();
        if (userDoc == null) return favorites;

        List<Document> rawFavorites = userDoc.getList("favorites", Document.class);
        if (rawFavorites == null) return favorites;

        for (Document favoriteDoc : rawFavorites) {
            favorites.add(documentToMovie(favoriteDoc));
        }
        return favorites;
    }

    @Override
    public void setWatchStatus(String username, Movie movie, String status) {
        Document userDoc = userCollection.find(Filters.eq("_id", username))
                .projection(Projections.include("watchlist"))
                .first();
        if (userDoc == null) return;

        List<Document> watchlist = userDoc.getList("watchlist", Document.class);
        List<Document> updated = new ArrayList<>();
        if (watchlist != null) {
            for (Document entry : watchlist) {
                if (!movie.getId().equals(entry.getString("id"))) {
                    updated.add(entry);
                }
            }
        }

        Document entryDoc = movieToDocument(movie).append("status", status);
        updated.add(entryDoc);

        userCollection.updateOne(Filters.eq("_id", username), Updates.set("watchlist", updated));
    }

    @Override
    public void removeFromWatchlist(String username, String movieId) {
        userCollection.updateOne(Filters.eq("_id", username),
                Updates.pull("watchlist", Filters.eq("id", movieId)));
    }

    @Override
    public List<Movie> getWatchlist(String username) {
        Document userDoc = userCollection.find(Filters.eq("_id", username))
                .projection(Projections.include("watchlist"))
                .first();

        List<Movie> watchlist = new ArrayList<>();
        if (userDoc == null) return watchlist;

        List<Document> rawWatchlist = userDoc.getList("watchlist", Document.class);
        if (rawWatchlist == null) return watchlist;

        for (Document entryDoc : rawWatchlist) {
            Movie movie = documentToMovie(entryDoc);
            movie.setWatchStatus(entryDoc.getString("status"));
            watchlist.add(movie);
        }
        return watchlist;
    }

    private Document movieToDocument(Movie movie) {
        Document doc = new Document("id", movie.getId())
                .append("title", movie.getTitle())
                .append("year", movie.getYear());

        Poster poster = movie.getPoster();
        if (poster != null) {
            doc.append("poster", new Document("path", poster.getPath())
                    .append("sizes", new Document("w342", poster.getW342())
                            .append("original", poster.getOriginal())));
        }

        return doc;
    }

    private Movie documentToMovie(Document doc) {
        Movie movie = new Movie();
        movie.setId(doc.getString("id"));
        movie.setTitle(doc.getString("title"));
        movie.setYear(doc.getInteger("year", 0));

        Document posterDoc = doc.get("poster", Document.class);
        if (posterDoc != null) {
            Poster poster = new Poster();
            poster.setPath(posterDoc.getString("path"));
            Document sizes = posterDoc.get("sizes", Document.class);
            if (sizes != null) {
                poster.setW342(sizes.getString("w342"));
                poster.setOriginal(sizes.getString("original"));
            }
            movie.setPoster(poster);
        }

        return movie;
    }
}
