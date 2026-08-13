package DataAccessObject.Interfaces;

import Model.Movie;
import Model.User;

import java.util.List;

public interface UserDao {
    /** Returns false without creating anything if the username is already taken. */
    boolean createUser(User user);

    String getPasswordForUsername(String username);

    User getUserByUsername(String username);

    /** Returns false without adding anything if the user already has 3 favorites. Idempotent if already favorited. */
    boolean addFavorite(String username, Movie movie);

    void removeFavorite(String username, String movieId);

    List<Movie> getFavorites(String username);

    /** Moves the movie to the given status, replacing any status it already had in the watchlist. */
    void setWatchStatus(String username, Movie movie, String status);

    void removeFromWatchlist(String username, String movieId);

    /** Each returned Movie has watchStatus set to "watched" | "watching" | "plan_to_watch". */
    List<Movie> getWatchlist(String username);
}