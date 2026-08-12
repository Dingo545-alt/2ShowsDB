import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionStatusServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;

    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void reportsLoggedOutWhenNoSession() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        new SessionStatusServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertFalse(json.get("loggedIn").getAsBoolean());
    }

    @Test
    void reportsLoggedOutWhenSessionHasNoUser() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(null);

        new SessionStatusServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertFalse(json.get("loggedIn").getAsBoolean());
    }

    @Test
    void reportsLoggedInForActiveUserSession() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(new Object());

        new SessionStatusServlet().doGet(request, response);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertTrue(json.get("loggedIn").getAsBoolean());
    }
}