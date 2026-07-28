package Model;

public class Poster {
    private String path;
    private String w342;
    private String original;

    public Poster() {}

    public Poster(String path, String w342, String original) {
        this.path = path;
        this.w342 = w342;
        this.original = original;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getW342() { return w342; }
    public void setW342(String w342) { this.w342 = w342; }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }
}
