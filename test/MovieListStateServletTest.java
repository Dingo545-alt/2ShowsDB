import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieListStateServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;

    private StringWriter responseBody;

    private void stubWriter() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void returnsSavedQueryWhenPresentInSession() throws Exception {
        stubWriter();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("lastMovieListQuery")).thenReturn("title=matrix");

        new MovieListStateServlet().doGet(request, response);

        verify(response).setStatus(200);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("title=matrix", json.get("query").getAsString());
    }

    @Test
    void returnsEmptyStringWhenNoSession() throws Exception {
        stubWriter();
        when(request.getSession(false)).thenReturn(null);

        new MovieListStateServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("", json.get("query").getAsString());
    }

    @Test
    void returnsEmptyStringWhenAttributeMissing() throws Exception {
        stubWriter();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("lastMovieListQuery")).thenReturn(null);

        new MovieListStateServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("", json.get("query").getAsString());
    }

    @Test
    void returnsEmptyStringWhenAttributeIsNotAString() throws Exception {
        stubWriter();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("lastMovieListQuery")).thenReturn(42);

        new MovieListStateServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("", json.get("query").getAsString());
    }
}