package Model;

import java.util.ArrayList;
import java.util.List;

public class Movie {
    private String id;
    private String title;
    private int year;
    private String director;
    private String directorId;
    private double price;
    private Float rating;
    private int voteCount;
    private List<Genre> genres = new ArrayList<>();
    private List<Star> stars = new ArrayList<>();
    private Poster poster;
    private String overview; // TMDB plot synopsis, null when TMDB has none
    private String watchStatus; // "watched" | "watching" | "plan_to_watch"; only set on watchlist entries

    public Movie() {}

    public Movie(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public Movie(String id, String title, int year, String director, double price,
                 Float rating, int voteCount, List<Genre> genres,  List<Star> stars) {
        this.id = id;
        this.title = title;
        this.year = year;
        this.director = director;
        this.price = price;
        this.rating = rating;
        this.voteCount = voteCount;
        this.genres = genres;
        this.stars = stars;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getDirectorId() { return directorId; }
    public void setDirectorId(String directorId) { this.directorId = directorId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public Float getRating() { return rating; }
    public void setRating(Float rating) { this.rating = rating; }

    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }

    public List<Genre> getGenres() { return genres; }
    public void setGenres(List<Genre> genres) { this.genres = genres; }

    public List<Star> getStars() { return stars; }
    public void setStars(List<Star> stars) { this.stars = stars; }

    public Poster getPoster() { return poster; }
    public void setPoster(Poster poster) { this.poster = poster; }

    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }

    public String getWatchStatus() { return watchStatus; }
    public void setWatchStatus(String watchStatus) { this.watchStatus = watchStatus; }
}
