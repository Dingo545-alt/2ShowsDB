import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.CartDao;
import Model.CartItemDetails;

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
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(name = "ShoppingCartServlet", urlPatterns = "/api/shopping-cart")
public class ShoppingCartServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Returns the map from the session, creating an empty one if needed.
     * LinkedHashMap used to display the order the user added them in.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Integer> getOrCreateCart(HttpSession session) {
        Map<String, Integer> cart = (Map<String, Integer>) session.getAttribute("cart");
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute("cart", cart);
        }
        return cart;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession();
        Map<String, Integer> cart = getOrCreateCart(session);

        JsonArray cartArray = new JsonArray();

        if (cart.isEmpty()) {
            out.write(cartArray.toString());
            out.close();
            return;
        }

        try {
            CartDao                      cartDao = DaoFactory.getCartDao();
            Map<String, CartItemDetails> details = cartDao.getCartItemDetails(cart.keySet());

            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                String          movieId = entry.getKey();
                int             quantity = entry.getValue();
                CartItemDetails item    = details.getOrDefault(
                        movieId, new CartItemDetails("(unknown)", 0.0));

                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("id",       movieId);
                itemJson.addProperty("title",    item.getTitle());
                itemJson.addProperty("quantity", quantity);
                itemJson.addProperty("price",    item.getPrice());
                cartArray.add(itemJson);
            }

            out.write(cartArray.toString());
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("errorMessage", e.getMessage());
            out.write(err.toString());
            response.setStatus(500);
        } finally {
            out.close();
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String movieId = request.getParameter("id");
        String action = request.getParameter("action");

        JsonObject responseJson = new JsonObject();

        if (movieId == null || movieId.isEmpty() || action == null || action.isEmpty()) {
            responseJson.addProperty("status", "error");
            responseJson.addProperty("message", "Missing id or action");
            response.setStatus(400);
            out.write(responseJson.toString());
            out.close();
            return;
        }

        HttpSession session = request.getSession();
        Map<String, Integer> cart = getOrCreateCart(session);

        switch (action) {
            case "add":
            case "increase":
                cart.merge(movieId, 1, Integer::sum);
                break;
            case "decrease":
                Integer current = cart.get(movieId);
                if (current != null) {
                    if (current <= 1) {
                        cart.remove(movieId);
                    } else {
                        cart.put(movieId, current - 1);
                    }
                }
                break;
            case "delete":
                cart.remove(movieId);
                break;
            default:
                responseJson.addProperty("status", "error");
                responseJson.addProperty("message", "Unknown action: " + action);
                response.setStatus(400);
                out.write(responseJson.toString());
                out.close();
                return;
        }

        int totalItems = 0;
        for (int q : cart.values()) totalItems += q;

        responseJson.addProperty("status", "success");
        responseJson.addProperty("cartCount", totalItems);
        response.setStatus(200);
        out.write(responseJson.toString());
        out.close();
    }
}
