import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieListDao;
import Model.MovieListParams;
import Model.MovieListResult;
import Model.MovieListResult.MovieSummary;
import Model.MovieListResult.StarSummary;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieListServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;
    @Mock private ServletContext servletContext;
    @Mock private MovieListDao movieListDao;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getServletContext()).thenReturn(servletContext);
        lenient().when(request.getSession()).thenReturn(session);

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getMovieListDao).thenReturn(movieListDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    private MovieSummary sampleSummary() {
        MovieSummary summary = new MovieSummary();
        summary.setMovieId("tt1");
        summary.setMovieTitle("Sample Movie");
        summary.setMovieYear("2020");
        summary.setMovieDirector("Some Director");
        summary.setMovieDirectorId("nm1234");
        summary.setMovieRating("8.5");
        summary.setPosterThumbnailUrl("https://img/w342/x.jpg");
        summary.setGenres(List.of("Drama", "Comedy"));
        summary.setStars(List.of(new StarSummary("nm1", "A Star")));
        return summary;
    }

    @Test
    void returnsMoviesAndTotalCountOnSuccess() throws Exception {
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(sampleSummary()), 1));

        new MovieListServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals(1, json.get("totalCount").getAsInt());
        JsonObject movieObj = json.getAsJsonArray("movies").get(0).getAsJsonObject();
        assertEquals("tt1", movieObj.get("movie_id").getAsString());
        assertEquals("Sample Movie", movieObj.get("movie_title").getAsString());
        assertEquals("nm1234", movieObj.get("movie_director_id").getAsString());
        assertEquals(2, movieObj.getAsJsonArray("genres").size());
        assertEquals("nm1", movieObj.getAsJsonArray("stars").get(0).getAsJsonObject().get("star_id").getAsString());
    }

    @Test
    void defaultsPageSizeAndNumberWhenParamsMissing() throws Exception {
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(), 0));

        new MovieListServlet().doGet(request, response);

        ArgumentCaptor<MovieListParams> captor = ArgumentCaptor.forClass(MovieListParams.class);
        verify(movieListDao).getMovies(captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals(1, captor.getValue().getPageNumber());
    }

    @Test
    void invalidPageSizeFallsBackToDefault() throws Exception {
        lenient().when(request.getParameter("pageSize")).thenReturn("999");
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(), 0));

        new MovieListServlet().doGet(request, response);

        ArgumentCaptor<MovieListParams> captor = ArgumentCaptor.forClass(MovieListParams.class);
        verify(movieListDao).getMovies(captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    void nonNumericPageNumberFallsBackToOne() throws Exception {
        lenient().when(request.getParameter("pageNumber")).thenReturn("not-a-number");
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(), 0));

        new MovieListServlet().doGet(request, response);

        ArgumentCaptor<MovieListParams> captor = ArgumentCaptor.forClass(MovieListParams.class);
        verify(movieListDao).getMovies(captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
    }

    @Test
    void negativePageNumberIsClampedToOne() throws Exception {
        lenient().when(request.getParameter("pageNumber")).thenReturn("-5");
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(), 0));

        new MovieListServlet().doGet(request, response);

        ArgumentCaptor<MovieListParams> captor = ArgumentCaptor.forClass(MovieListParams.class);
        verify(movieListDao).getMovies(captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
    }

    @Test
    void savesQueryStringInSessionWhenPresent() throws Exception {
        when(request.getQueryString()).thenReturn("title=matrix&pageSize=25");
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(), 0));

        new MovieListServlet().doGet(request, response);

        verify(session).setAttribute("lastMovieListQuery", "title=matrix&pageSize=25");
    }

    @Test
    void doesNotTouchSessionWhenQueryStringIsNull() throws Exception {
        when(request.getQueryString()).thenReturn(null);
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenReturn(new MovieListResult(List.of(), 0));

        new MovieListServlet().doGet(request, response);

        verify(request, never()).getSession();
    }

    @Test
    void daoExceptionYields500WithErrorMessage() throws Exception {
        when(movieListDao.getMovies(any(MovieListParams.class)))
                .thenThrow(new RuntimeException("query failed"));

        new MovieListServlet().doGet(request, response);

        verify(response).setStatus(500);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("query failed", json.get("errorMessage").getAsString());
    }
}