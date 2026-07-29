package Model;

import java.util.ArrayList;
import java.util.List;

public class Star {
    private String id;
    private String name;
    private String dob; // ISO 8601 date string ("YYYY-MM-DD"), null when unknown
    private int movieCount;
    private List<Movie> movies = new ArrayList<>();

    public Star() {}

    public Star(String id, String name) {
        this.id = id;
        this.name = name;
    }
    public Star(String id, String name, String dob) {
        this.id = id;
        this.name = name;
        this.dob = dob;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public int getMovieCount() { return movieCount; }
    public void setMovieCount(int movieCount) { this.movieCount = movieCount; }

    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }
}
