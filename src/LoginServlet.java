import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.Customer;
import Model.Employee;

import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jasypt.util.password.StrongPasswordEncryptor;

import java.io.IOException;
import java.io.PrintWriter;

import java.io.Serial;

@WebServlet(name = "LoginServlet", urlPatterns = "/api/login")
public class LoginServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 4L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject responseJson = new JsonObject();

        String gRecaptchaResponse = request.getParameter("g-recaptcha-response");
        if (!RecaptchaVerify.verify(gRecaptchaResponse)) {
            responseJson.addProperty("status", "error");
            responseJson.addProperty("message", "Invalid Recaptcha Response");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            PrintWriter out = response.getWriter();
            out.write(responseJson.toString());
            out.close();
            return;
        }

        String email = request.getParameter("email");
        String plainPassword = request.getParameter("password");
        StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
        UserDao userDao = DaoFactory.getUserDao();

        try {
            String employeePasswordHash = userDao.getPasswordForEmployee(email);
            if (employeePasswordHash != null) {
                if (passwordEncryptor.checkPassword(plainPassword, employeePasswordHash)) {
                    Employee employee = userDao.getEmployeeByEmail(email);
                    request.getSession().setAttribute("employee", employee);

                    responseJson.addProperty("status", "success");
                    responseJson.addProperty("type", "employee");
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
            }

            String customerPasswordHash = userDao.getPasswordForCustomer(email);
            if (customerPasswordHash != null) {
                if (passwordEncryptor.checkPassword(plainPassword, customerPasswordHash)) {
                    Customer customer = userDao.getCustomerByEmail(email);
                    request.getSession().setAttribute("customer", customer);

                    responseJson.addProperty("status", "success");
                    responseJson.addProperty("type", "customer");
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }

            responseJson.addProperty("status", "error");
            responseJson.addProperty("message", "Invalid email or password");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        } catch (Exception e) {
            responseJson.addProperty("status", "error");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            PrintWriter out = response.getWriter();
            out.write(responseJson.toString());
            out.close();
        }
    }
}