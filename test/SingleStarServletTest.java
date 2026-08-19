import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.StarDao;
import Model.Movie;
import Model.Photo;
import Model.Poster;
import Model.Star;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletContext;
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
class SingleStarServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private StarDao starDao;
    @Mock private ServletContext servletContext;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getServletContext()).thenReturn(servletContext);

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getStarDao).thenReturn(starDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    @Test
    void returnsFullStarJsonWhenFound() throws Exception {
        when(request.getParameter("id")).thenReturn("nm0000209");

        Star star = new Star("nm0000209", "Tim Robbins", "1958-10-16");
        star.setPhoto(new Photo("/photo.jpg", "https://img/w185/photo.jpg", "https://img/original/photo.jpg"));
        star.setBiography("Timothy Francis Robbins is an American actor.");
        Movie movie = new Movie("tt0111161", "The Shawshank Redemption");
        movie.setYear(1994);
        movie.setPoster(new Poster("/poster.jpg", "https://img/w342/poster.jpg", "https://img/original/poster.jpg"));
        star.setMovies(List.of(movie));
        when(starDao.getStarById("nm0000209")).thenReturn(star);

        new SingleStarServlet().doGet(request, response);

        verify(response).setStatus(200);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("nm0000209", json.get("id").getAsString());
        assertEquals("Tim Robbins", json.get("name").getAsString());
        assertEquals("1958-10-16", json.get("dob").getAsString());
        assertEquals("/photo.jpg", json.getAsJsonObject("photo").get("path").getAsString());
        assertEquals("https://img/w185/photo.jpg",
                json.getAsJsonObject("photo").getAsJsonObject("sizes").get("w185").getAsString());
        assertEquals("Timothy Francis Robbins is an American actor.", json.get("biography").getAsString());
        assertEquals(1, json.getAsJsonArray("movies").size());
        JsonObject movieJson = json.getAsJsonArray("movies").get(0).getAsJsonObject();
        assertEquals("tt0111161", movieJson.get("id").getAsString());
        assertEquals(1994, movieJson.get("year").getAsInt());
        assertEquals("/poster.jpg", movieJson.getAsJsonObject("poster").get("path").getAsString());
    }

    @Test
    void missingDobSerializesAsNA() throws Exception {
        when(request.getParameter("id")).thenReturn("nm1");
        Star star = new Star("nm1", "No Birthday");
        when(starDao.getStarById("nm1")).thenReturn(star);

        new SingleStarServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("N/A", json.get("dob").getAsString());
        assertTrue(json.get("photo").isJsonNull());
        assertTrue(json.get("biography").isJsonNull());
    }

    @Test
    void returns404WhenStarNotFound() throws Exception {
        when(request.getParameter("id")).thenReturn("missing");
        when(starDao.getStarById("missing")).thenReturn(null);

        new SingleStarServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertTrue(json.get("errorMessage").getAsString().contains("missing"));
    }

    @Test
    void daoExceptionYields500WithErrorMessage() throws Exception {
        when(request.getParameter("id")).thenReturn("nm1");
        when(starDao.getStarById("nm1")).thenThrow(new RuntimeException("db down"));

        new SingleStarServlet().doGet(request, response);

        verify(response).setStatus(500);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("db down", json.get("errorMessage").getAsString());
    }
}