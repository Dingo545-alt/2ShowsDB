package Model;

public class Photo {
    private String path;
    private String w185;
    private String original;

    public Photo() {}

    public Photo(String path, String w185, String original) {
        this.path = path;
        this.w185 = w185;
        this.original = original;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getW185() { return w185; }
    public void setW185(String w185) { this.w185 = w185; }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }
}
