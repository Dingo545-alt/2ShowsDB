package DataAccessObject.Interfaces;

import Model.MovieListParams;
import Model.MovieListResult;

public interface MovieListDao {
    MovieListResult getMovies(MovieListParams params);
}
