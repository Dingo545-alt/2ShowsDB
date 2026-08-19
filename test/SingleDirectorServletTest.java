import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.DirectorDao;
import Model.Director;
import Model.Movie;
import Model.Photo;
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
class SingleDirectorServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private DirectorDao directorDao;
    @Mock private ServletContext servletContext;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getServletContext()).thenReturn(servletContext);

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getDirectorDao).thenReturn(directorDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    @Test
    void returnsFullDirectorJsonWhenFound() throws Exception {
        when(request.getParameter("id")).thenReturn("nm0000399");

        Director director = new Director("nm0000399", "Frank Darabont");
        director.setDob("1959-01-28");
        director.setPhoto(new Photo("/photo.jpg", "https://img/w185/photo.jpg", "https://img/original/photo.jpg"));
        director.setBiography("Frank Darabont is a French-American film director.");
        Movie movie = new Movie("tt0111161", "The Shawshank Redemption");
        movie.setYear(1994);
        director.setMovies(List.of(movie));
        when(directorDao.getDirectorById("nm0000399")).thenReturn(director);

        new SingleDirectorServlet().doGet(request, response);

        verify(response).setStatus(200);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("nm0000399", json.get("id").getAsString());
        assertEquals("Frank Darabont", json.get("name").getAsString());
        assertEquals("1959-01-28", json.get("dob").getAsString());
        assertEquals("/photo.jpg", json.getAsJsonObject("photo").get("path").getAsString());
        assertEquals("https://img/w185/photo.jpg",
                json.getAsJsonObject("photo").getAsJsonObject("sizes").get("w185").getAsString());
        assertEquals("Frank Darabont is a French-American film director.", json.get("biography").getAsString());
        assertEquals(1, json.getAsJsonArray("movies").size());
        JsonObject movieJson = json.getAsJsonArray("movies").get(0).getAsJsonObject();
        assertEquals("tt0111161", movieJson.get("id").getAsString());
        assertEquals(1994, movieJson.get("year").getAsInt());
    }

    @Test
    void missingDobSerializesAsNA() throws Exception {
        when(request.getParameter("id")).thenReturn("nm1");
        Director director = new Director("nm1", "No Birthday");
        when(directorDao.getDirectorById("nm1")).thenReturn(director);

        new SingleDirectorServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("N/A", json.get("dob").getAsString());
        assertTrue(json.get("photo").isJsonNull());
        assertTrue(json.get("biography").isJsonNull());
    }

    @Test
    void returns404WhenDirectorNotFound() throws Exception {
        when(request.getParameter("id")).thenReturn("missing");
        when(directorDao.getDirectorById("missing")).thenReturn(null);

        new SingleDirectorServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertTrue(json.get("errorMessage").getAsString().contains("missing"));
    }

    @Test
    void daoExceptionYields500WithErrorMessage() throws Exception {
        when(request.getParameter("id")).thenReturn("nm1");
        when(directorDao.getDirectorById("nm1")).thenThrow(new RuntimeException("db down"));

        new SingleDirectorServlet().doGet(request, response);

        verify(response).setStatus(500);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("db down", json.get("errorMessage").getAsString());
    }
}