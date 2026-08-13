import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieDao;
import DataAccessObject.Interfaces.UserDao;
import Model.Movie;
import Model.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
class WatchlistServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;
    @Mock private UserDao userDao;
    @Mock private MovieDao movieDao;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getUserDao).thenReturn(userDao);
        daoFactory.when(DaoFactory::getMovieDao).thenReturn(movieDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    private void loginAs(String username) {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(new User(username, "hash"));
    }

    @Test
    void getRejectsWhenNotLoggedIn() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        new WatchlistServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(userDao);
    }

    @Test
    void getReturnsWatchlistWithStatusPerMovie() throws Exception {
        loginAs("jdoe");
        Movie movie = new Movie("tt1", "The Movie");
        movie.setYear(2001);
        movie.setWatchStatus("watching");
        when(userDao.getWatchlist("jdoe")).thenReturn(List.of(movie));

        new WatchlistServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(1, json.size());
        JsonObject first = json.get(0).getAsJsonObject();
        assertEquals("tt1", first.get("id").getAsString());
        assertEquals("watching", first.get("status").getAsString());
    }

    @Test
    void postRejectsWhenNotLoggedIn() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        new WatchlistServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(userDao, movieDao);
    }

    @Test
    void postSetsWatchStatus() throws Exception {
        loginAs("jdoe");
        when(request.getParameter("movieId")).thenReturn("tt1");
        when(request.getParameter("status")).thenReturn("watched");
        Movie movie = new Movie("tt1", "The Movie");
        when(movieDao.getMovieById("tt1")).thenReturn(movie);

        new WatchlistServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        ArgumentCaptor<Movie> movieCaptor = ArgumentCaptor.forClass(Movie.class);
        verify(userDao).setWatchStatus(eq("jdoe"), movieCaptor.capture(), eq("watched"));
        assertEquals("tt1", movieCaptor.getValue().getId());

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("success", json.get("status").getAsString());
    }

    @Test
    void postRejectsInvalidStatus() throws Exception {
        loginAs("jdoe");
        when(request.getParameter("movieId")).thenReturn("tt1");
        when(request.getParameter("status")).thenReturn("bogus");
        Movie movie = new Movie("tt1", "The Movie");
        when(movieDao.getMovieById("tt1")).thenReturn(movie);

        new WatchlistServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(userDao);
    }

    @Test
    void postRejectsUnknownMovieId() throws Exception {
        loginAs("jdoe");
        when(request.getParameter("movieId")).thenReturn("does-not-exist");
        when(request.getParameter("status")).thenReturn("watched");
        when(movieDao.getMovieById("does-not-exist")).thenReturn(null);

        new WatchlistServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(userDao);
    }

    @Test
    void deleteRejectsWhenNotLoggedIn() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        new WatchlistServlet().doDelete(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(userDao);
    }

    @Test
    void deleteRemovesMovieFromWatchlist() throws Exception {
        loginAs("jdoe");
        when(request.getParameter("movieId")).thenReturn("tt1");

        new WatchlistServlet().doDelete(request, response);

        verify(userDao).removeFromWatchlist("jdoe", "tt1");
        verify(response).setStatus(HttpServletResponse.SC_OK);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("success", json.get("status").getAsString());
    }

    @Test
    void deleteRejectsMissingMovieId() throws Exception {
        loginAs("jdoe");
        when(request.getParameter("movieId")).thenReturn(null);

        new WatchlistServlet().doDelete(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(userDao);
    }
}
