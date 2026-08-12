import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.User;

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
    private static final long serialVersionUID = 5L;

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

        String username = request.getParameter("username");
        String plainPassword = request.getParameter("password");
        StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
        UserDao userDao = DaoFactory.getUserDao();

        try {
            String passwordHash = userDao.getPasswordForUsername(username);
            if (passwordHash != null && passwordEncryptor.checkPassword(plainPassword, passwordHash)) {
                User user = userDao.getUserByUsername(username);
                request.getSession().setAttribute("user", user);

                responseJson.addProperty("status", "success");
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                responseJson.addProperty("status", "error");
                responseJson.addProperty("message", "Invalid username or password");
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