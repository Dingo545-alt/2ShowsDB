package Model;

public class MovieListParams {
    // ------- Pagination --------------------------------
    private final int pageSize;
    private final int pageNumber;

    // ------- Sort --------------------------------
    private final String primarySortField;
    private final String primarySortDirection;
    private final String secondarySortField;
    private final String secondarySortDirection;

    // ------- Search filters --------------------------------
    private final String searchTitle;
    private final String searchYear;
    private final String searchDirector;
    private final String searchStar;

    // ------- Browse filters (mutually exclusive with search) --------------------------------
    private final String browseGenre;
    private final String browseStartChar;

    // ------- Full-text search --------------------------------
    private final String fullTextQuery;

    private MovieListParams(Builder b) {
        this.pageSize               = b.pageSize;
        this.pageNumber             = b.pageNumber;
        this.primarySortField       = b.primarySortField;
        this.primarySortDirection   = b.primarySortDirection;
        this.secondarySortField     = b.secondarySortField;
        this.secondarySortDirection = b.secondarySortDirection;
        this.searchTitle            = b.searchTitle;
        this.searchYear             = b.searchYear;
        this.searchDirector         = b.searchDirector;
        this.searchStar             = b.searchStar;
        this.browseGenre            = b.browseGenre;
        this.browseStartChar        = b.browseStartChar;
        this.fullTextQuery          = b.fullTextQuery;
    }

    // ----------------------- getters --------------------------------

    public int getPageSize()                { return pageSize; }
    public int getPageNumber()              { return pageNumber; }

    public String getPrimarySortField()       { return primarySortField; }
    public String getPrimarySortDirection()   { return primarySortDirection; }
    public String getSecondarySortField()     { return secondarySortField; }
    public String getSecondarySortDirection() { return secondarySortDirection; }

    public String getSearchTitle()    { return searchTitle; }
    public String getSearchYear()     { return searchYear; }
    public String getSearchDirector() { return searchDirector; }
    public String getSearchStar()     { return searchStar; }

    public String getBrowseGenre()     { return browseGenre; }
    public String getBrowseStartChar() { return browseStartChar; }

    public String getFullTextQuery()   { return fullTextQuery; }

    // ----------------------- Builder --------------------------------
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int    pageSize   = 10;
        private int    pageNumber = 1;
        private String primarySortField;
        private String primarySortDirection;
        private String secondarySortField;
        private String secondarySortDirection;
        private String searchTitle;
        private String searchYear;
        private String searchDirector;
        private String searchStar;
        private String browseGenre;
        private String browseStartChar;
        private String fullTextQuery;

        public Builder pageSize(int v)                  { this.pageSize = v;                return this; }
        public Builder pageNumber(int v)                { this.pageNumber = v;              return this; }
        public Builder primarySortField(String v)       { this.primarySortField = v;        return this; }
        public Builder primarySortDirection(String v)   { this.primarySortDirection = v;    return this; }
        public Builder secondarySortField(String v)     { this.secondarySortField = v;      return this; }
        public Builder secondarySortDirection(String v) { this.secondarySortDirection = v;  return this; }
        public Builder searchTitle(String v)            { this.searchTitle = v;             return this; }
        public Builder searchYear(String v)             { this.searchYear = v;              return this; }
        public Builder searchDirector(String v)         { this.searchDirector = v;          return this; }
        public Builder searchStar(String v)             { this.searchStar = v;              return this; }
        public Builder browseGenre(String v)            { this.browseGenre = v;             return this; }
        public Builder browseStartChar(String v)        { this.browseStartChar = v;         return this; }
        public Builder fullTextQuery(String v)          { this.fullTextQuery = v;           return this; }

        public MovieListParams build() { return new MovieListParams(this); }
    }
}