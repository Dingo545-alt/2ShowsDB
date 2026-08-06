import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.DirectorDao;
import Model.Director;
import Model.Movie;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;


@WebServlet(name = "SingleDirectorServlet", urlPatterns = "/api/single-director")
public class SingleDirectorServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;


    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String id = request.getParameter("id");
        request.getServletContext().log("getting id: " + id);

        PrintWriter out = response.getWriter();

        try{
            DirectorDao directorDao = DaoFactory.getDirectorDao();
            Director director = directorDao.getDirectorById(id);

            if (director == null) {
                JsonObject errorJson = new JsonObject();
                errorJson.addProperty("errorMessage", "Director not found with id: " + id);
                out.write(errorJson.toString());
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            JsonObject directorJson = new JsonObject();
            directorJson.addProperty("id",   director.getId());
            directorJson.addProperty("name", director.getName());

            if (director.getDob() == null) {
                directorJson.addProperty("dob", "N/A");
            } else {
                directorJson.addProperty("dob", director.getDob());
            }

            if (director.getPhoto() != null) {
                JsonObject photoJson = new JsonObject();
                photoJson.addProperty("path", director.getPhoto().getPath());
                JsonObject sizesJson = new JsonObject();
                sizesJson.addProperty("w185", director.getPhoto().getW185());
                sizesJson.addProperty("original", director.getPhoto().getOriginal());
                photoJson.add("sizes", sizesJson);
                directorJson.add("photo", photoJson);
            } else {
                directorJson.add("photo", null);
            }

            JsonArray moviesArray = new JsonArray();
            for (Movie movie : director.getMovies()) {
                JsonObject movieJson = new JsonObject();
                movieJson.addProperty("id",    movie.getId());
                movieJson.addProperty("title", movie.getTitle());
                movieJson.addProperty("year",  movie.getYear());
                moviesArray.add(movieJson);
            }
            directorJson.add("movies", moviesArray);

            out.write(directorJson.toString());
            response.setStatus(200);

        } catch (Exception e) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("errorMessage", e.getMessage());
            out.write(jsonObject.toString());
            request.getServletContext().log("Error:", e);
            response.setStatus(500);
        } finally {
            out.close();
        }
    }
}