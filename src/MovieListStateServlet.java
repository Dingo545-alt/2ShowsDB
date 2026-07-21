import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;

/**
 * Used to preserve last search state in movie list so you can go back without losing their place
 */
@WebServlet(name = "MovieListStateServlet", urlPatterns = "/api/movie-list-state")
public class MovieListStateServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 3L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session  = request.getSession(false);
        String      savedQuery = null;

        if (session != null) {
            Object attr = session.getAttribute("lastMovieListQuery");
            if (attr instanceof String) {
                savedQuery = (String) attr;
            }
        }

        // Always return a string (empty if nothing saved) so the client
        // can build a URL unconditionally.
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("query", savedQuery == null ? "" : savedQuery);

        try (PrintWriter out = response.getWriter()) {
            out.write(responseJson.toString());
        }
        response.setStatus(200);
    }
}
