import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.Customer;
import Model.Employee;
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
    void logsInEmployeeWithCorrectPassword() throws Exception {
        when(request.getParameter("email")).thenReturn("boss@2shows.com");
        when(userDao.getPasswordForEmployee("boss@2shows.com")).thenReturn(PASSWORD_HASH);
        Employee employee = new Employee("boss@2shows.com", PASSWORD_HASH, "The Boss");
        when(userDao.getEmployeeByEmail("boss@2shows.com")).thenReturn(employee);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(session).setAttribute("employee", employee);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("success", json.get("status").getAsString());
        assertEquals("employee", json.get("type").getAsString());
    }

    @Test
    void fallsBackToCustomerWhenNotAnEmployee() throws Exception {
        when(request.getParameter("email")).thenReturn("customer@example.com");
        when(userDao.getPasswordForEmployee("customer@example.com")).thenReturn(null);
        when(userDao.getPasswordForCustomer("customer@example.com")).thenReturn(PASSWORD_HASH);
        Customer customer = new Customer(1, "Jane", "Doe", "123 Main St",
                "customer@example.com", PASSWORD_HASH, null);
        when(userDao.getCustomerByEmail("customer@example.com")).thenReturn(customer);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(session).setAttribute("customer", customer);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("customer", json.get("type").getAsString());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        when(request.getParameter("email")).thenReturn("customer@example.com");
        when(request.getParameter("password")).thenReturn("totally-wrong-password");
        when(userDao.getPasswordForEmployee("customer@example.com")).thenReturn(null);
        when(userDao.getPasswordForCustomer("customer@example.com")).thenReturn(PASSWORD_HASH);

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
        assertEquals("Invalid email or password", json.get("message").getAsString());
        verify(session, never()).setAttribute(anyString(), any());
    }

    /**
     * Documents an existing bug in LoginServlet: the "Invalid email or password" branch is
     * nested inside {@code if (customerPasswordHash != null)}, so when the email matches
     * neither an employee nor a customer, that branch is skipped entirely. No status is set
     * (the mock never records a setStatus call) and the response body is an empty JSON object,
     * rather than the 401 + error message a caller would reasonably expect.
     */
    @Test
    void unknownEmailFallsThroughWithoutSettingErrorResponse() throws Exception {
        when(request.getParameter("email")).thenReturn("nobody@example.com");
        when(userDao.getPasswordForEmployee("nobody@example.com")).thenReturn(null);
        when(userDao.getPasswordForCustomer("nobody@example.com")).thenReturn(null);

        new LoginServlet().doPost(request, response);

        verify(response, never()).setStatus(anyInt());
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertFalse(json.has("status"));
    }

    @Test
    void daoExceptionYields500() throws Exception {
        when(request.getParameter("email")).thenReturn("customer@example.com");
        when(userDao.getPasswordForEmployee("customer@example.com")).thenThrow(new RuntimeException("db down"));

        new LoginServlet().doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
        assertEquals("error", json.get("status").getAsString());
    }
}