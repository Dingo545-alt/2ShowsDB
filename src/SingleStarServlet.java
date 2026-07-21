import DataAccessObject.DaoFactory;
import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.StarDao;
import Model.Movie;
import Model.Star;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;


@WebServlet(name = "SingleStarServlet", urlPatterns = "/api/single-star")
public class SingleStarServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 3L;


    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String id = request.getParameter("id");
        request.getServletContext().log("getting id: " + id);

        PrintWriter out = response.getWriter();

        try{
            StarDao starDao = DaoFactory.getStarDao();
            Star star    = starDao.getStarById(id);

            if (star == null) {
                JsonObject errorJson = new JsonObject();
                errorJson.addProperty("errorMessage", "Star not found with id: " + id);
                out.write(errorJson.toString());
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            JsonObject starJson = new JsonObject();
            starJson.addProperty("id",   star.getId());
            starJson.addProperty("name", star.getName());

            if (star.getBirthYear() == null) {
                starJson.addProperty("birth_year", "N/A");
            } else {
                starJson.addProperty("birth_year", star.getBirthYear());
            }

            JsonArray moviesArray = new JsonArray();
            for (Movie movie : star.getMovies()) {
                JsonObject movieJson = new JsonObject();
                movieJson.addProperty("id",    movie.getId());
                movieJson.addProperty("title", movie.getTitle());
                movieJson.addProperty("year",  movie.getYear());
                moviesArray.add(movieJson);
            }
            starJson.add("movies", moviesArray);

            out.write(starJson.toString());
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
