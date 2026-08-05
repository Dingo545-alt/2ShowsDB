import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.PaymentDao;
import Model.Customer;
import Model.SaleRecord;

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
import java.util.List;
import java.util.Map;

@WebServlet(name = "PaymentServlet", urlPatterns = "/api/payment")
public class PaymentServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 3L;

    // -------- GET: return price preview for the current cart ------------------------

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out     = response.getWriter();
        HttpSession session = request.getSession();
        Map<String, Integer> cart = ShoppingCartServlet.getOrCreateCart(session);

        JsonObject responseJson = new JsonObject();
        JsonArray  itemsArray   = new JsonArray();
        double total      = 0.0;
        int    totalItems = 0;

        try {
            PaymentDao          paymentDao = DaoFactory.getPaymentDao();
            Map<String, Double> prices     = paymentDao.getPricesForCart(cart.keySet());

            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                String movieId = entry.getKey();
                int    quantity = entry.getValue();
                double price   = prices.getOrDefault(movieId, 0.0);

                JsonObject item = new JsonObject();
                item.addProperty("id",       movieId);
                item.addProperty("quantity", quantity);
                item.addProperty("price",    price);
                itemsArray.add(item);

                total      += price * quantity;
                totalItems += quantity;
            }

            responseJson.add("items",     itemsArray);
            responseJson.addProperty("total",     total);
            responseJson.addProperty("itemCount", totalItems);
            response.setStatus(HttpServletResponse.SC_OK);
            out.write(responseJson.toString());

        } catch (Exception e) {
            responseJson.addProperty("status",  "error");
            responseJson.addProperty("message", "Database error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write(responseJson.toString());
        } finally {
            out.close();
        }
    }

    // -------- POST: validate card, insert sales, clear cart --------------------------------

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out          = response.getWriter();
        JsonObject  responseJson = new JsonObject();

        // -------- Validate required fields --------------------------------
        String firstName        = request.getParameter("firstName");
        String lastName         = request.getParameter("lastName");
        String creditCardNumber = request.getParameter("creditCardNumber");
        String expiration       = request.getParameter("expiration"); // expects YYYY-MM-DD

        if (firstName == null || firstName.isEmpty()
                || lastName == null || lastName.isEmpty()
                || creditCardNumber == null || creditCardNumber.isEmpty()
                || expiration == null || expiration.isEmpty()) {
            responseJson.addProperty("status",  "error");
            responseJson.addProperty("message", "All payment fields are required.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(responseJson.toString());
            out.close();
            return;
        }

        // -------- Validate expiration format before hitting the DB --------------------------------
        if (!expiration.matches("\\d{4}-\\d{2}-\\d{2}")) {
            responseJson.addProperty("status",  "error");
            responseJson.addProperty("message", "Expiration must be in YYYY-MM-DD format.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(responseJson.toString());
            out.close();
            return;
        }

        HttpSession          session = request.getSession();
        Map<String, Integer> cart    = ShoppingCartServlet.getOrCreateCart(session);

        if (cart.isEmpty()) {
            responseJson.addProperty("status",  "error");
            responseJson.addProperty("message", "Your cart is empty.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(responseJson.toString());
            out.close();
            return;
        }

        Customer customer = (Customer) session.getAttribute("customer");
        if (customer == null) {
            responseJson.addProperty("status",  "error");
            responseJson.addProperty("message", "You must be logged in to place an order.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.write(responseJson.toString());
            out.close();
            return;
        }
        String customerEmail = customer.getEmail();

        try {
            PaymentDao paymentDao = DaoFactory.getPaymentDao();

            // -------- Validate card + get customer id (single DB query in Mongo) --------
            int customerId = paymentDao.validateCardAndGetCustomerId(
                    customerEmail, creditCardNumber, firstName, lastName, expiration);

            if (customerId == -1) {
                responseJson.addProperty("status",  "error");
                responseJson.addProperty("message",
                        "Payment information does not match any credit card on file.");
                response.setStatus(402);
                out.write(responseJson.toString());
                out.close();
                return;
            }

            // --------Snapshot prices before inserting so the receipt is accurate --------
            Map<String, Double> prices = paymentDao.getPricesForCart(cart.keySet());

            // -------- Insert sales (atomic) --------------------------------
            List<SaleRecord> sales = paymentDao.insertSales(customerId, cart, prices);

            // -------- Build confirmation snapshot for the confirmation page --------
            JsonArray confirmationItems = new JsonArray();
            JsonArray saleIdArr         = new JsonArray();
            double    grandTotal        = 0.0;

            for (SaleRecord sale : sales) {
                JsonObject item = new JsonObject();
                item.addProperty("id",       sale.getMovieId());
                item.addProperty("quantity", sale.getQuantity());
                item.addProperty("price",    sale.getPrice());
                confirmationItems.add(item);
                grandTotal += sale.getPrice() * sale.getQuantity();
                saleIdArr.add(sale.getSaleId());
            }

            JsonObject confirmation = new JsonObject();
            confirmation.add("items",  confirmationItems);
            confirmation.addProperty("total", grandTotal);
            confirmation.add("saleIds", saleIdArr);
            session.setAttribute("lastConfirmation", confirmation.toString());

            // Cart is now an order — clear it
            cart.clear();

            responseJson.addProperty("status", "success");
            responseJson.add("saleIds", saleIdArr);
            response.setStatus(HttpServletResponse.SC_OK);
            out.write(responseJson.toString());

        } catch (Exception e) {
            responseJson.addProperty("status",  "error");
            responseJson.addProperty("message", "Database error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write(responseJson.toString());
        } finally {
            out.close();
        }
    }
}