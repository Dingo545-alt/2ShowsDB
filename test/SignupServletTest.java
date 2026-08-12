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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;
    @Mock private UserDao userDao;

    private StringWriter responseBody;
    private MockedStatic<DaoFactory> daoFactory;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        daoFactory = Mockito.mockStatic(DaoFactory.class);
        daoFactory.when(DaoFactory::getUserDao).thenReturn(userDao);
    }

    @AfterEach
    void tearDown() {
        daoFactory.close();
    }

    @Test
    void createsAccountWithEncryptedPasswordAndLogsIn() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(request.getParameter("password")).thenReturn("hunter2");
        when(userDao.createUser(any(User.class))).thenReturn(true);
        when(request.getSession()).thenReturn(session);

        new SignupServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDao).createUser(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertEquals("jdoe", createdUser.getUsername());
        assertNotEquals("hunter2", createdUser.getPassword());
        assertTrue(new StrongPasswordEncryptor().checkPassword("hunter2", createdUser.getPassword()));
        verify(session).setAttribute("user", createdUser);

        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("success", json.get("status").getAsString());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(request.getParameter("password")).thenReturn("hunter2");
        when(userDao.createUser(any(User.class))).thenReturn(false);

        new SignupServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_CONFLICT);
        verifyNoInteractions(session);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
        assertEquals("Username is already taken", json.get("message").getAsString());
    }

    @Test
    void rejectsBlankUsername() throws Exception {
        when(request.getParameter("username")).thenReturn(" ");
        when(request.getParameter("password")).thenReturn("hunter2");

        new SignupServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(userDao);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
    }

    @Test
    void rejectsMissingPassword() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(request.getParameter("password")).thenReturn(null);

        new SignupServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(userDao);
    }

    @Test
    void daoExceptionYields500() throws Exception {
        when(request.getParameter("username")).thenReturn("jdoe");
        when(request.getParameter("password")).thenReturn("hunter2");
        when(userDao.createUser(any(User.class))).thenThrow(new RuntimeException("db down"));

        new SignupServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
    }
}