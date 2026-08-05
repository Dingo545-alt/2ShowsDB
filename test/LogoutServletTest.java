import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;

    @Test
    void invalidatesExistingSessionAndRedirects() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/2shows");

        new LogoutServlet().doGet(request, response);

        verify(session).invalidate();
        verify(response).sendRedirect("/2shows/index.html");
    }

    @Test
    void redirectsWithoutErrorWhenNoActiveSession() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/2shows");

        new LogoutServlet().doGet(request, response);

        verify(response).sendRedirect("/2shows/index.html");
    }
}