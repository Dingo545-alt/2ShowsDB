import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * LoginFilterServlet's access-control logic is currently commented out,
 * so it unconditionally passes every request through. This test locks in
 * that pass-through behavior so a future re-enable of the auth logic shows up as an
 * intentional test change rather than a silent regression.
 */
@ExtendWith(MockitoExtension.class)
class LoginFilterServletTest {

    @Mock private ServletRequest request;
    @Mock private ServletResponse response;
    @Mock private FilterChain chain;

    @Test
    void alwaysPassesRequestThroughToChain() throws Exception {
        new LoginFilterServlet().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoMoreInteractions(request, response, chain);
    }
}