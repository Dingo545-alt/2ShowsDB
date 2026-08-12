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

@WebServlet(name = "SignupServlet", urlPatterns = "/api/signup")
public class SignupServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject responseJson = new JsonObject();

        String username = request.getParameter("username");
        String plainPassword = request.getParameter("password");

        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) {
            responseJson.addProperty("status", "error");
            responseJson.addProperty("message", "Username and password are required");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter out = response.getWriter();
            out.write(responseJson.toString());
            out.close();
            return;
        }

        UserDao userDao = DaoFactory.getUserDao();
        StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();

        try {
            String passwordHash = passwordEncryptor.encryptPassword(plainPassword);
            User user = new User(username, passwordHash);
            boolean created = userDao.createUser(user);

            if (created) {
                request.getSession().setAttribute("user", user);
                responseJson.addProperty("status", "success");
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                responseJson.addProperty("status", "error");
                responseJson.addProperty("message", "Username is already taken");
                response.setStatus(HttpServletResponse.SC_CONFLICT);
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