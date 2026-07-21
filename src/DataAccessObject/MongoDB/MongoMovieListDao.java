package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieListDao;
import Model.MovieListParams;
import Model.MovieListResult;
import Model.MovieListResult.MovieSummary;
import Model.MovieListResult.StarSummary;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Projections;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MongoMovieListDao implements MovieListDao {

    private final MongoCollection<Document> movieCollection;

    public MongoMovieListDao() {
        MongoClient mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database  = mongoClient.getDatabase("moviedb");
        this.movieCollection    = database.getCollection("movies");
    }

    @Override
    public MovieListResult getMovies(MovieListParams params) {
        Bson filter   = buildFilter(params);
        Bson sortSpec = buildSort(params);
        int  pageSize = params.getPageSize();
        int  offset   = (params.getPageNumber() - 1) * pageSize;

        int totalCount = (int) movieCollection.countDocuments(filter);

        Bson projection = Projections.fields(
                Projections.include("_id", "title", "year", "director", "rating", "genres", "stars")
        );

        List<Document> docs = movieCollection
                .find(filter)
                .projection(projection)
                .sort(sortSpec)
                .skip(offset)
                .limit(pageSize)
                .into(new ArrayList<>());

        List<MovieSummary> summaries = new ArrayList<>();
        for (Document doc : docs) {
            summaries.add(toSummary(doc));
        }

        return new MovieListResult(summaries, totalCount);
    }

    // ------------------- filter builder ---------------------------------------------------------
    private Bson buildFilter(MovieListParams p) {

        // 1. Full-text prefix search (new search box)
        String fullText = p.getFullTextQuery();
        if (fullText != null && !fullText.isEmpty()) {
            return buildFullTextFilter(fullText);
        }

        // 2. Browse by genre
        String browseGenre = p.getBrowseGenre();
        if (browseGenre != null && !browseGenre.isEmpty()) {
            return Filters.eq("genres", browseGenre);
        }

        // 3. Browse by starting character
        String browseStartChar = p.getBrowseStartChar();
        if (browseStartChar != null && !browseStartChar.isEmpty()) {
            String prefix = Pattern.quote(browseStartChar.toUpperCase());
            return Filters.regex("title", "^" + prefix, "i");
        }

        // 4. Keyword search (original search box — preserved untouched)
        List<Bson> conditions = new ArrayList<>();

        if (p.getSearchTitle() != null && !p.getSearchTitle().isEmpty()) {
            conditions.add(Filters.regex("title",
                    Pattern.quote(p.getSearchTitle()), "i"));
        }
        if (p.getSearchYear() != null && !p.getSearchYear().isEmpty()) {
            try {
                conditions.add(Filters.eq("year", Integer.parseInt(p.getSearchYear())));
            } catch (NumberFormatException ignored) {}
        }
        if (p.getSearchDirector() != null && !p.getSearchDirector().isEmpty()) {
            conditions.add(Filters.regex("director",
                    Pattern.quote(p.getSearchDirector()), "i"));
        }
        if (p.getSearchStar() != null && !p.getSearchStar().isEmpty()) {
            conditions.add(Filters.regex("stars.name",
                    Pattern.quote(p.getSearchStar()), "i"));
        }

        return conditions.isEmpty() ? new Document() : Filters.and(conditions);
    }

    /**
     * Creates a case-insensitive MongoDB filter requiring every word in the search query
     * to match the start of a word in the document's title.
     * Example: "good u" matches "Good Uncle" or "Ultimate Good", but not "Feel Good".
     */
    private Bson buildFullTextFilter(String rawQuery) {
        String[] tokens = rawQuery.trim().split("\\s+");
        List<Bson> conditions = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                conditions.add(Filters.regex("title",
                        "(?i)\\b" + Pattern.quote(token)));
            }
        }
        if (conditions.isEmpty()) return new Document();
        return conditions.size() == 1 ? conditions.get(0) : Filters.and(conditions);
    }

    // ------------------- sort builder ---------------------------------------------------------
    private Bson buildSort(MovieListParams p) {
        String primaryField  = resolveSortField(p.getPrimarySortField());
        boolean primaryAsc   = isAscending(p.getPrimarySortDirection());

        String secondaryField = resolveSortField(p.getSecondarySortField());
        if (secondaryField.equals(primaryField)) {
            secondaryField = primaryField.equals("title") ? "rating" : "title";
        }
        boolean secondaryAsc = isAscending(p.getSecondarySortDirection());

        Bson primary   = primaryAsc   ? Sorts.ascending(primaryField)   : Sorts.descending(primaryField);
        Bson secondary = secondaryAsc ? Sorts.ascending(secondaryField) : Sorts.descending(secondaryField);

        return Sorts.orderBy(primary, secondary);
    }

    private String resolveSortField(String requested) {
        if (requested == null) return "rating";
        switch (requested.toLowerCase()) {
            case "title":  return "title";
            case "rating": return "rating";
            default:       return "rating";
        }
    }

    private boolean isAscending(String direction) {
        return direction != null && direction.equalsIgnoreCase("asc");
    }

    // ------------------- Document to MovieSummary --------------------------------------

    /**
     *  Maps a MongoDB document to a {@link MovieSummary}
     *
     *  genres - already stored alphabetically in the doc, take first 3
     *  stars - already stored by movie-count DESC, name ASC, take first 3
     */
    private MovieSummary toSummary(Document doc) {
        MovieSummary s = new MovieSummary();

        s.setMovieId(doc.getString("_id"));
        s.setMovieTitle(doc.getString("title"));
        s.setMovieYear(String.valueOf(doc.getInteger("year", 0)));
        s.setMovieDirector(doc.getString("director"));

        Double rating = doc.getDouble("rating");
        s.setMovieRating(rating != null ? String.valueOf(rating) : "N/A");

        List<String> allGenres  = doc.getList("genres", String.class);
        List<String> top3Genres = new ArrayList<>();
        if (allGenres != null) {
            for (int i = 0; i < Math.min(3, allGenres.size()); i++) {
                top3Genres.add(allGenres.get(i));
            }
        }
        s.setGenres(top3Genres);

        List<Document>   allStars  = doc.getList("stars", Document.class);
        List<StarSummary> top3Stars = new ArrayList<>();
        if (allStars != null) {
            for (int i = 0; i < Math.min(3, allStars.size()); i++) {
                Document starDoc = allStars.get(i);
                top3Stars.add(new StarSummary(
                        starDoc.getString("id"),
                        starDoc.getString("name")
                ));
            }
        }
        s.setStars(top3Stars);

        return s;
    }
}