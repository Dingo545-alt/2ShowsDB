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

//        boolean isDashboardPath = pathInApp.startsWith("/_dashboard") || pathInApp.startsWith("/api/employee-dashboard");
//        boolean isLoginOrPublic = pathInApp.endsWith("/login.html") ||
//                pathInApp.endsWith("login.js") ||
//                pathInApp.endsWith("/api/login") ||
//                pathInApp.endsWith("/api/logout") ||
//                pathInApp.equals("/index.html") ||
//                pathInApp.equals("/");
//
//        boolean isStaticResource = pathInApp.endsWith(".css") ||
//                pathInApp.endsWith(".png") ||
//                pathInApp.endsWith(".jpg") ||
//                pathInApp.endsWith(".js");
//
//        HttpSession session = httpRequest.getSession(false);
//        boolean isCustomerLoggedIn = (session != null && session.getAttribute("customer") != null);
//        boolean isEmployeeLoggedIn = (session != null && session.getAttribute("employee") != null);
//
//        if (isLoginOrPublic || isStaticResource) {
//            chain.doFilter(request, response);
//        } else if (isDashboardPath) {
//            if (isEmployeeLoggedIn) {
//                chain.doFilter(request, response);
//            } else {
//                httpResponse.sendRedirect(httpRequest.getContextPath() + "/index.html");
//            }
//        } else {
//            if (isCustomerLoggedIn || isEmployeeLoggedIn) {
//                chain.doFilter(request, response);
//            } else {
//                httpResponse.sendRedirect(httpRequest.getContextPath() + "/index.html");
//            }
//        }


    }
}
