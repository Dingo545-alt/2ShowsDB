import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.User;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jasypt.util.password.StrongPasswordEncryptor;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;
    @Mock private UserDao userDao;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;
    private MockedStatic<RecaptchaVerify> recaptchaVerify;

    private static final String PLAIN_PASSWORD = "correct-horse-battery-staple";
    private static String PASSWORD_HASH;

    @BeforeEach
    void setUp() throws Exception {
        // Real jasypt hash so checkPassword() runs unmocked, matching production behavior.
        PASSWORD_HASH = new StrongPasswordEncryptor().encryptPassword(PLAIN_PASSWORD);

        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        lenient().when(request.getSession()).thenReturn(session);
        lenient().when(request.getParameter("password")).thenReturn(PLAIN_PASSWORD);
        lenient().when(request.getParameter("g-recaptcha-response")).thenReturn("captcha-token");

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getUserDao).thenReturn(userDao);

        recaptchaVerify = Mockito.mockStatic(RecaptchaVerify.class);
        recaptchaVerify.when(() -> RecaptchaVerify.verify(anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
        recaptchaVerify.close();
    }

    @Test
    void rejectsFailedRecaptcha() throws Exception {
        recaptchaVerify.when(() -> RecaptchaVerify.verify(anyString())).thenReturn(false);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
        assertEquals("Invalid Recaptcha Response", json.get("message").getAsString());
        verifyNoInteractions(userDao);
    }

    @Test
    void logsInUserWithCorrectPassword() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(userDao.getPasswordForUsername("jdoe")).thenReturn(PASSWORD_HASH);
        User user = new User("jdoe", PASSWORD_HASH);
        when(userDao.getUserByUsername("jdoe")).thenReturn(user);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(session).setAttribute("user", user);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("success", json.get("status").getAsString());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(request.getParameter("password")).thenReturn("totally-wrong-password");
        when(userDao.getPasswordForUsername("jdoe")).thenReturn(PASSWORD_HASH);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
        assertEquals("Invalid username or password", json.get("message").getAsString());
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    void rejectsUnknownUsername() throws Exception {
        when(request.getParameter("username")).thenReturn("nobody");
        when(userDao.getPasswordForUsername("nobody")).thenReturn(null);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
        assertEquals("Invalid username or password", json.get("message").getAsString());
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    void daoExceptionYields500() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(userDao.getPasswordForUsername("jdoe")).thenThrow(new RuntimeException("db down"));

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
    }
}