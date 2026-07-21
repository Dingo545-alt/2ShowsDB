import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieDao;
import Model.Genre;
import Model.Movie;
import Model.Star;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;

@WebServlet(name = "SingleMovieServlet", urlPatterns = "/api/single-movie")
public class SingleMovieServlet extends HttpServlet{
    private static final long serialVersionUID = 3L;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException{

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String id = request.getParameter("id");
        JsonObject responseJson = new JsonObject();

        MovieDao movieDao = DaoFactory.getMovieDao();

        try {
            Movie movie = movieDao.getMovieById(id);

            if (movie == null) {
                responseJson.addProperty("status", "error");
                responseJson.addProperty("message", "Movie not found with ID: " + id);
                return;
            }

            responseJson.addProperty("id", movie.getId());
            responseJson.addProperty("title", movie.getTitle());
            responseJson.addProperty("year", movie.getYear());
            responseJson.addProperty("director", movie.getDirector());

            if (movie.getRating() == null) {
                responseJson.add("rating", JsonNull.INSTANCE);
            } else {
                responseJson.addProperty("rating", movie.getRating());
            }

            JsonArray genresArray = new JsonArray();
            for (Genre genre : movie.getGenres()) {
                JsonObject genreJson = new JsonObject();
                genreJson.addProperty("name", genre.getName());
                genresArray.add(genreJson);
            }
            responseJson.add("genres", genresArray);

            JsonArray starsArray = new JsonArray();
            for (Star star : movie.getStars()) {
                JsonObject starJson = new JsonObject();
                starJson.addProperty("id", star.getId());
                starJson.addProperty("name", star.getName());
                starJson.addProperty("movie_count", star.getMovieCount());
                starsArray.add(starJson);
            }
            responseJson.add("stars", starsArray);

            responseJson.addProperty("status", "success");
            response.setStatus(HttpServletResponse.SC_OK);

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