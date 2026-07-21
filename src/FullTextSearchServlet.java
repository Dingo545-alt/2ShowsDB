import DataAccessObject.DaoFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@WebServlet(name = "FullTextSearchServlet", urlPatterns = "/api/full-text-search")
public class FullTextSearchServlet extends HttpServlet {

    private MongoCollection<Document> movieCollection;

    @Override
    public void init() {
        MongoDatabase db  = DaoFactory.getMongoClient().getDatabase("moviedb");
        this.movieCollection = db.getCollection("movies");
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out     = response.getWriter();
        JsonArray   results = new JsonArray();

        String query = request.getParameter("q");
        if (query == null || query.trim().isEmpty()) {
            out.write(results.toString());
            out.close();
            return;
        }

        Bson filter = buildPrefixFilter(query.trim());

        try {
            movieCollection
                    .find(filter)
                    .projection(Projections.include("_id", "title"))
                    .limit(10)
                    .into(new ArrayList<>())
                    .forEach(doc -> {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("id",    doc.getString("_id"));
                        obj.addProperty("title", doc.getString("title"));
                        results.add(obj);
                    });

            response.setStatus(200);
        } catch (Exception e) {
            request.getServletContext().log("FullTextSearch error: " + e.getMessage());
        }

        out.write(results.toString());
        out.close();
    }

    /**
     * Ex: "good u" matches "Good Uncle" or "Ultimate Good", but not "Feel Good".
     */
    private Bson buildPrefixFilter(String rawQuery) {
        String[] tokens = rawQuery.split("\\s+");
        List<Bson> conditions = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                conditions.add(Filters.regex("title",
                        "(?i)\\b" + Pattern.quote(token)));
            }
        }
        if (conditions.isEmpty()) return new Document();
        return conditions.size() == 1 ? conditions.get(0) : Filters.and(conditions);
    }
}