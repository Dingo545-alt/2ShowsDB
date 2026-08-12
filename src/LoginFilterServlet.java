import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter(filterName = "LoginFilterServlet", urlPatterns = "/*")
public class LoginFilterServlet implements Filter {

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
//        HttpServletRequest httpRequest = (HttpServletRequest) request;
//        HttpServletResponse httpResponse = (HttpServletResponse) response;

        chain.doFilter(request, response);

//        String requestURI = httpRequest.getRequestURI().toLowerCase();
//        String contextPath = httpRequest.getContextPath().toLowerCase();
//
//        String pathInApp = requestURI.substring(contextPath.length());
//
//        boolean isLoginOrPublic = pathInApp.endsWith("/login.html") ||
//                pathInApp.endsWith("/signup.html") ||
//                pathInApp.endsWith("login.js") ||
//                pathInApp.endsWith("signup.js") ||
//                pathInApp.endsWith("/api/login") ||
//                pathInApp.endsWith("/api/signup") ||
//                pathInApp.endsWith("/api/logout") ||
//                pathInApp.equals("/main.html") ||
//                pathInApp.equals("/");
//
//        boolean isStaticResource = pathInApp.endsWith(".css") ||
//                pathInApp.endsWith(".png") ||
//                pathInApp.endsWith(".jpg") ||
//                pathInApp.endsWith(".js");
//
//        HttpSession session = httpRequest.getSession(false);
//        boolean isLoggedIn = (session != null && session.getAttribute("user") != null);
//
//        if (isLoginOrPublic || isStaticResource || isLoggedIn) {
//            chain.doFilter(request, response);
//        } else {
//            httpResponse.sendRedirect(httpRequest.getContextPath() + "/main.html");
//        }

    }
}