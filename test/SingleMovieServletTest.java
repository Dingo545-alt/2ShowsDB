import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieDao;
import Model.Genre;
import Model.Movie;
import Model.Poster;
import Model.Star;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class SingleMovieServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private MovieDao movieDao;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getMovieDao).thenReturn(movieDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    @Test
    void returnsFullMovieJsonWhenFound() throws Exception {
        when(request.getParameter("id")).thenReturn("tt0111161");

        Movie movie = new Movie();
        movie.setId("tt0111161");
        movie.setTitle("The Shawshank Redemption");
        movie.setYear(1994);
        movie.setDirector("Frank Darabont");
        movie.setDirectorId("nm0000399");
        movie.setRating(9.3f);
        movie.setPoster(new Poster("/poster.jpg", "https://img/w342/poster.jpg", "https://img/original/poster.jpg"));
        movie.setGenres(List.of(new Genre(1, "Drama")));

        Star star = new Star("nm0000209", "Tim Robbins");
        star.setMovieCount(42);
        movie.setStars(List.of(star));

        when(movieDao.getMovieById("tt0111161")).thenReturn(movie);

        new SingleMovieServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("success", json.get("status").getAsString());
        assertEquals("tt0111161", json.get("id").getAsString());
        assertEquals("The Shawshank Redemption", json.get("title").getAsString());
        assertEquals(1994, json.get("year").getAsInt());
        assertEquals("Frank Darabont", json.get("director").getAsString());
        assertEquals("nm0000399", json.get("directorId").getAsString());
        assertEquals(9.3f, json.get("rating").getAsFloat());
        assertEquals("/poster.jpg", json.getAsJsonObject("poster").get("path").getAsString());
        assertEquals(1, json.getAsJsonArray("genres").size());
        assertEquals("Drama", json.getAsJsonArray("genres").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1, json.getAsJsonArray("stars").size());
        JsonObject starJson = json.getAsJsonArray("stars").get(0).getAsJsonObject();
        assertEquals("nm0000209", starJson.get("id").getAsString());
        assertEquals("Tim Robbins", starJson.get("name").getAsString());
        assertEquals(42, starJson.get("movie_count").getAsInt());
    }

    @Test
    void nullRatingSerializesAsJsonNull() throws Exception {
        when(request.getParameter("id")).thenReturn("tt1");

        Movie movie = new Movie();
        movie.setId("tt1");
        movie.setTitle("Untitled");
        movie.setRating(null);
        when(movieDao.getMovieById("tt1")).thenReturn(movie);

        new SingleMovieServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertTrue(json.get("rating").isJsonNull());
        assertTrue(json.get("poster").isJsonNull());
    }

    @Test
    void returnsErrorWhenMovieNotFound() throws Exception {
        when(request.getParameter("id")).thenReturn("nonexistent");
        when(movieDao.getMovieById("nonexistent")).thenReturn(null);

        new SingleMovieServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("nonexistent"));
        // Note: current implementation returns from the try block on the not-found path
        // without explicitly setting an HTTP status, so setStatus(200) is never called.
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void daoExceptionYieldsInternalServerError() throws Exception {
        when(request.getParameter("id")).thenReturn("tt1");
        when(movieDao.getMovieById("tt1")).thenThrow(new RuntimeException("boom"));

        new SingleMovieServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
    }
}