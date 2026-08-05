import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.GenreDao;
import com.google.gson.JsonArray;
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
class GenresServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private GenreDao genreDao;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getGenreDao).thenReturn(genreDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    @Test
    void returnsGenreNamesAsJsonArray() throws Exception {
        when(genreDao.getAllGenreNames()).thenReturn(List.of("Drama", "Comedy", "Action"));

        new GenresServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(3, json.size());
        assertEquals("Drama", json.get(0).getAsString());
    }

    @Test
    void returnsEmptyArrayWhenNoGenres() throws Exception {
        when(genreDao.getAllGenreNames()).thenReturn(List.of());

        new GenresServlet().doGet(request, response);

        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(0, json.size());
    }

    @Test
    void daoExceptionYields500AndEmptyArray() throws Exception {
        when(genreDao.getAllGenreNames()).thenThrow(new RuntimeException("boom"));

        new GenresServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonArray json = JsonParser.parseString(responseBody.toString()).getAsJsonArray();
        assertEquals(0, json.size());
    }
}