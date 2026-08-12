import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.MovieDao;
import DataAccessObject.Interfaces.UserDao;
import Model.Movie;
import Model.Poster;
import Model.User;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;

@WebServlet(name = "FavoritesServlet", urlPatterns = "/api/favorites")
public class FavoritesServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String LIMIT_MESSAGE =
            "You can only add 3 favorited movies. To remove one go to profile page.";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String username = requireUsername(request, response);
        if (username == null) return;

        JsonArray favoritesArray = new JsonArray();

        try {
            UserDao userDao = DaoFactory.getUserDao();
            for (Movie movie : userDao.getFavorites(username)) {
                favoritesArray.add(toJson(movie));
            }
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            PrintWriter out = response.getWriter();
            out.write(favoritesArray.toString());
            out.close();
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String username = requireUsername(request, response);
        if (username == null) return;

        JsonObject responseJson = new JsonObject();

        try {
            String movieId = request.getParameter("movieId");
            MovieDao movieDao = DaoFactory.getMovieDao();
            Movie movie = movieId != null ? movieDao.getMovieById(movieId) : null;

            if (movie == null) {
                responseJson.addProperty("status", "error");
                responseJson.addProperty("message", "Movie not found");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                UserDao userDao = DaoFactory.getUserDao();
                boolean added = userDao.addFavorite(username, movie);
                if (added) {
                    responseJson.addProperty("status", "success");
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    responseJson.addProperty("status", "error");
                    responseJson.addProperty("message", LIMIT_MESSAGE);
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                }
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

    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String username = requireUsername(request, response);
        if (username == null) return;

        JsonObject responseJson = new JsonObject();

        try {
            String movieId = request.getParameter("movieId");
            if (movieId == null) {
                responseJson.addProperty("status", "error");
                responseJson.addProperty("message", "movieId is required");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                UserDao userDao = DaoFactory.getUserDao();
                userDao.removeFavorite(username, movieId);
                responseJson.addProperty("status", "success");
                response.setStatus(HttpServletResponse.SC_OK);
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

    /** Writes a 401 JSON error and returns null when no user is logged in. */
    private String requireUsername(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;

        if (user == null) {
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("status", "error");
            responseJson.addProperty("message", "Not logged in");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            PrintWriter out = response.getWriter();
            out.write(responseJson.toString());
            out.close();
            return null;
        }

        return user.getUsername();
    }

    private JsonObject toJson(Movie movie) {
        JsonObject movieJson = new JsonObject();
        movieJson.addProperty("id", movie.getId());
        movieJson.addProperty("title", movie.getTitle());
        movieJson.addProperty("year", movie.getYear());

        Poster poster = movie.getPoster();
        if (poster != null) {
            JsonObject posterJson = new JsonObject();
            posterJson.addProperty("path", poster.getPath());
            posterJson.addProperty("w342", poster.getW342());
            posterJson.addProperty("original", poster.getOriginal());
            movieJson.add("poster", posterJson);
        }

        return movieJson;
    }
}
