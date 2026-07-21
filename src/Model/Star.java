package Model;

import java.util.ArrayList;
import java.util.List;

public class Star {
    private String id;
    private String name;
    private Integer birthYear;
    private int movieCount;
    private List<Movie> movies = new ArrayList<>();

    public Star() {}

    public Star(String id, String name) {
        this.id = id;
        this.name = name;
    }
    public Star(String id, String name, Integer birthYear) {
        this.id = id;
        this.name = name;
        this.birthYear = birthYear;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getBirthYear() { return birthYear; }
    public void setBirthYear(Integer birthYear) { this.birthYear = birthYear; }

    public int getMovieCount() { return movieCount; }
    public void setMovieCount(int movieCount) { this.movieCount = movieCount; }

    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }
}
