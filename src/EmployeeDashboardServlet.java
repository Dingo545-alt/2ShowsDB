import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;

import java.io.Serial;

@WebServlet(name="EmployeeDashboardServlet", urlPatterns="/api/employee-dashboard")
public class EmployeeDashboardServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 6L;
}