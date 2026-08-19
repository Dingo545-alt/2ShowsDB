package Model;

import java.util.ArrayList;
import java.util.List;

public class Director {
    private String id;
    private String name;
    private String dob; // ISO 8601 date string ("YYYY-MM-DD"), null when unknown
    private Photo photo;
    private String biography; // TMDB person bio, null when TMDB has none
    private List<Movie> movies = new ArrayList<>();

    public Director() {}

    public Director(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public Photo getPhoto() { return photo; }
    public void setPhoto(Photo photo) { this.photo = photo; }

    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }

    public List<Movie> getMovies() { return movies; }
    public void setMovies(List<Movie> movies) { this.movies = movies; }
}
