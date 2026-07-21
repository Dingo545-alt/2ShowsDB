package Model;

import java.util.List;

// used for pagination
public class MovieListResult {
    private final List<MovieSummary> movies;
    private final int totalCount;

    public MovieListResult(List<MovieSummary> movies, int totalCount) {
        this.movies     = movies;
        this.totalCount = totalCount;
    }

    public List<MovieSummary> getMovies()  { return movies; }
    public int getTotalCount()             { return totalCount; }

    public static class MovieSummary {
        private String       movieId;
        private String       movieTitle;
        private String       movieYear;
        private String       movieDirector;
        private String       movieRating;
        private List<String> genres;
        private List<StarSummary> stars;

        public MovieSummary() {}

        public String getMovieId()        { return movieId; }
        public void   setMovieId(String v){ this.movieId = v; }

        public String getMovieTitle()        { return movieTitle; }
        public void   setMovieTitle(String v){ this.movieTitle = v; }

        public String getMovieYear()        { return movieYear; }
        public void   setMovieYear(String v){ this.movieYear = v; }

        public String getMovieDirector()        { return movieDirector; }
        public void   setMovieDirector(String v){ this.movieDirector = v; }

        public String getMovieRating()        { return movieRating; }
        public void   setMovieRating(String v){ this.movieRating = v; }

        public List<String> getGenres()              { return genres; }
        public void         setGenres(List<String> v){ this.genres = v; }

        public List<StarSummary> getStars()               { return stars; }
        public void              setStars(List<StarSummary> v){ this.stars = v; }
    }

    public static class StarSummary {
        private String starId;
        private String starName;

        public StarSummary(String starId, String starName) {
            this.starId   = starId;
            this.starName = starName;
        }

        public String getStarId()   { return starId; }
        public String getStarName() { return starName; }
    }


}
