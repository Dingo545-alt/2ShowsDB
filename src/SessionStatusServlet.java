import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;

@WebServlet(name = "SessionStatusServlet", urlPatterns = "/api/session-status")
public class SessionStatusServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        boolean loggedIn = session != null && session.getAttribute("user") != null;

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("loggedIn", loggedIn);

        PrintWriter out = response.getWriter();
        out.write(responseJson.toString());
        out.close();
    }
}