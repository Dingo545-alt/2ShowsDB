import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.GenreDao;
import com.google.gson.JsonArray;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.util.List;

@WebServlet(name = "GenresServlet", urlPatterns = "/api/genres")
public class GenresServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 5L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JsonArray genreArray = new JsonArray();

        GenreDao genreDao = DaoFactory.getGenreDao();

        try {
            List<String> genres = genreDao.getAllGenreNames();
            for (String genreName : genres) {
                genreArray.add(genreName);
            }

            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            PrintWriter out = response.getWriter();
            out.write(genreArray.toString());
            out.close(); 
        }
    }
}