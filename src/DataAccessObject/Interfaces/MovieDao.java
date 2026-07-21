package DataAccessObject.Interfaces;

import Model.Movie;

public interface MovieDao {
    Movie getMovieById(String id);
}
