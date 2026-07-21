import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.CartDao;
import Model.CartItemDetails;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet(name = "ConfirmationServlet", urlPatterns = "/api/confirmation")
public class ConfirmationServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 8L;

    private static final String EMPTY_RESPONSE = "{\"items\":[],\"total\":0,\"saleIds\":[]}";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out     = response.getWriter();
        HttpSession session = request.getSession(false);

        // No session or no completed order yet - return an empty snapshot
        if (session == null || session.getAttribute("lastConfirmation") == null) {
            out.write(EMPTY_RESPONSE);
            out.close();
            return;
        }

        JsonObject snapshot = JsonParser.parseString(
                session.getAttribute("lastConfirmation").toString()
        ).getAsJsonObject();

        JsonArray items = snapshot.getAsJsonArray("items");

        // fill each item with its movie title using CartDao (reuses the
        // existing id to {title, price} lookup - no new DAO needed)
        if (items != null && items.size() > 0) {
            try {
                List<String> movieIds = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    movieIds.add(items.get(i).getAsJsonObject().get("id").getAsString());
                }

                CartDao                      cartDao = DaoFactory.getCartDao();
                Map<String, CartItemDetails> details = cartDao.getCartItemDetails(movieIds);

                for (int i = 0; i < items.size(); i++) {
                    JsonObject item    = items.get(i).getAsJsonObject();
                    String     movieId = item.get("id").getAsString();
                    String     title   = details.getOrDefault(
                            movieId, new CartItemDetails("(unknown)", 0.0)).getTitle();
                    item.addProperty("title", title);
                }
            } catch (Exception e) {

            }
        }

        out.write(snapshot.toString());
        response.setStatus(HttpServletResponse.SC_OK);
        out.close();
    }
}
