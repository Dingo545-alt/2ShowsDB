import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieListDao;
import Model.MovieListParams;
import Model.MovieListResult;
import Model.MovieListResult.MovieSummary;
import Model.MovieListResult.StarSummary;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.Set;

@WebServlet(name = "MovieListServlet", urlPatterns = "/api/movie-list")
public class MovieListServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 3L;

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final int DEFAULT_PAGE_SIZE           = 10;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        long servletExecutionStartTime = System.nanoTime();
        long queryExecutionElapsedTime = 0;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String primarySortField       = request.getParameter("primarySortField");
        String primarySortDirection   = request.getParameter("primarySortDirection");
        String secondarySortField     = request.getParameter("secondarySortField");
        String secondarySortDirection = request.getParameter("secondarySortDirection");

        int pageSize   = resolveRequestedPageSize(request.getParameter("pageSize"));
        int pageNumber = resolveRequestedPageNumber(request.getParameter("pageNumber"));

        String searchTitle     = request.getParameter("title");
        String searchYear      = request.getParameter("year");
        String searchDirector  = request.getParameter("director");
        String searchStar      = request.getParameter("star");
        String browseGenre     = request.getParameter("genre");
        String browseStartChar = request.getParameter("start-char");
        String fullTextQuery   = request.getParameter("fulltext");

        request.getServletContext().log(
                "List request: primary=" + primarySortField + " " + primarySortDirection
                        + ", secondary=" + secondarySortField + " " + secondarySortDirection
                        + ", page=" + pageNumber + ", size=" + pageSize
                        + ", title=" + searchTitle + ", year=" + searchYear
                        + ", director=" + searchDirector + ", star=" + searchStar
                        + ", genre=" + browseGenre + ", start-char=" + browseStartChar
                        + ", fulltext=" + fullTextQuery);

        // session saving
        String currentQueryString = request.getQueryString();
        if (currentQueryString != null) {
            request.getSession().setAttribute("lastMovieListQuery", currentQueryString);
        }

        PrintWriter out = response.getWriter();

        try {
            MovieListParams params = MovieListParams.builder()
                    .pageSize(pageSize)
                    .pageNumber(pageNumber)
                    .primarySortField(primarySortField)
                    .primarySortDirection(primarySortDirection)
                    .secondarySortField(secondarySortField)
                    .secondarySortDirection(secondarySortDirection)
                    .searchTitle(searchTitle)
                    .searchYear(searchYear)
                    .searchDirector(searchDirector)
                    .searchStar(searchStar)
                    .browseGenre(browseGenre)
                    .browseStartChar(browseStartChar)
                    .fullTextQuery(fullTextQuery)
                    .build();

            MovieListDao movieListDao = DaoFactory.getMovieListDao();

            long queryExecutionStartTime = System.nanoTime();
            MovieListResult result    = movieListDao.getMovies(params);
            long queryExecutionEndTime = System.nanoTime();
            queryExecutionElapsedTime = queryExecutionEndTime - queryExecutionStartTime;

            // -- Build JSON response --
            JsonArray moviesArray = new JsonArray();
            for (MovieSummary movie : result.getMovies()) {
                JsonObject movieObj = new JsonObject();
                movieObj.addProperty("movie_id",       movie.getMovieId());
                movieObj.addProperty("movie_title",    movie.getMovieTitle());
                movieObj.addProperty("movie_year",     movie.getMovieYear());
                movieObj.addProperty("movie_director", movie.getMovieDirector());
                movieObj.addProperty("movie_rating",   movie.getMovieRating());

                JsonArray genresArray = new JsonArray();
                for (String genre : movie.getGenres()) {
                    genresArray.add(genre);
                }
                movieObj.add("genres", genresArray);

                JsonArray starsArray = new JsonArray();
                for (StarSummary star : movie.getStars()) {
                    JsonObject starObj = new JsonObject();
                    starObj.addProperty("star_id",   star.getStarId());
                    starObj.addProperty("star_name", star.getStarName());
                    starsArray.add(starObj);
                }
                movieObj.add("stars", starsArray);

                moviesArray.add(movieObj);
            }

            request.getServletContext().log("Getting " + moviesArray.size() + " results");

            JsonObject responseJson = new JsonObject();
            responseJson.add("movies", moviesArray);
            responseJson.addProperty("totalCount", result.getTotalCount());

            out.write(responseJson.toString());
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("errorMessage", e.getMessage());
            out.write(jsonObject.toString());
            response.setStatus(500);
        } finally {
            out.close();
        }
        long servletExecutionEndTime = System.nanoTime();
        long servletExecutionElapsedTime = servletExecutionEndTime - servletExecutionStartTime;

        String logDirPath = request.getServletContext().getRealPath("/WEB-INF/LogFiles");

        File logDir = new File(logDirPath);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        File logFile = new File(logDir, "mongo_log.txt");

        // writes log in target build structure
        synchronized (MovieListServlet.class) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
                String line = servletExecutionElapsedTime + "," + queryExecutionElapsedTime;
                bw.write(line);
                bw.newLine();
            } catch (IOException e) {
                request.getServletContext().log("Error writing Log file: " + e.getMessage());
            }
        }
    }

    private int resolveRequestedPageSize(String requestedPageSize) {
        if (requestedPageSize == null) return DEFAULT_PAGE_SIZE;
        try {
            int parsed = Integer.parseInt(requestedPageSize);
            if (ALLOWED_PAGE_SIZES.contains(parsed)) return parsed;
        } catch (NumberFormatException ignored) {}
        return DEFAULT_PAGE_SIZE;
    }

    private int resolveRequestedPageNumber(String requestedPageNumber) {
        if (requestedPageNumber == null) return 1;
        try {
            int parsed = Integer.parseInt(requestedPageNumber);
            return Math.max(parsed, 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}