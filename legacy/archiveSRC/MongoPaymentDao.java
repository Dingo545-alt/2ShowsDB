package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.PaymentDao;
import Model.SaleRecord;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MongoPaymentDao implements PaymentDao{

    private final MongoCollection<Document> movieCollection;
    private final MongoCollection<Document> customerCollection;
    private final MongoCollection<Document> salesCollection;
    private final MongoCollection<Document> countersCollection;

    public MongoPaymentDao() {
        MongoClient mongoClient     = DaoFactory.getMongoClient();
        MongoDatabase db            = mongoClient.getDatabase("moviedb");
        this.movieCollection    = db.getCollection("movies");
        this.customerCollection = db.getCollection("customers");
        this.salesCollection    = db.getCollection("sales");
        this.countersCollection = db.getCollection("counters");
        initSaleCounter();
    }

    private void initSaleCounter() {
        Document maxDoc = salesCollection
                .find()
                .sort(new Document("_id", -1))
                .limit(1)
                .first();
        int currentMax = maxDoc != null ? maxDoc.getInteger("_id", 0) : 0;

        // $max only updates if currentMax is greater than the existing seq.
        // upsert creates the document if it doesn't exist yet.
        countersCollection.updateOne(
                Filters.eq("_id", "sales"),
                new Document("$max", new Document("seq", currentMax)),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
        );
    }


    // ----------- Price Lookup --------------------------------------------
    @Override
    public Map<String, Double> getPricesForCart(Collection<String> movieIds) {
        Map<String, Double> prices = new HashMap<>();
        if (movieIds == null || movieIds.isEmpty()) return prices;

        movieCollection
                .find(Filters.in("_id", movieIds))
                .projection(Projections.include("_id", "price"))
                .forEach(doc -> {
                    Double price = doc.getDouble("price");
                    prices.put(doc.getString("_id"), price != null ? price : 0.0);
                });

        return prices;
    }


    // ----------- Card validation + customer lookup (single query) --------------------------------------------
    @Override
    public int validateCardAndGetCustomerId(String email,
                                            String creditCardNumber,
                                            String firstName,
                                            String lastName,
                                            String expiration) {
        Document doc = customerCollection.find(
                Filters.and(
                        Filters.eq("email",                email),
                        Filters.eq("credit_card.id",         creditCardNumber),
                        Filters.eq("credit_card.first_name", firstName),
                        Filters.eq("credit_card.last_name",  lastName),
                        Filters.eq("credit_card.expiration", expiration)
                )
        ).projection(Projections.include("_id")).first();

        if (doc == null) return -1;

        Integer id = doc.getInteger("_id");
        return id != null ? id : -1;
    }


    // ----------- Sales insertion (transactional) --------------------------------------------
    @Override
    public List<SaleRecord> insertSales(int customerId,
                                        Map<String, Integer> cart,
                                        Map<String, Double> prices) {
        List<SaleRecord> records = new ArrayList<>();
        String today = java.time.LocalDate.now().toString(); // "YYYY-MM-DD"

        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            String movieId  = entry.getKey();
            int    quantity = entry.getValue();
            double price    = prices.getOrDefault(movieId, 0.0);

            int saleId = getNextSaleId();

            Document saleDoc = new Document("_id",          saleId)
                    .append("customer_id",   customerId)
                    .append("movie_id",      movieId)
                    .append("sale_date",     today)
                    .append("quantity",      quantity)
                    .append("price_at_sale", price);

            salesCollection.insertOne(saleDoc);
            records.add(new SaleRecord(saleId, movieId, quantity, price));
        }

        return records;
    }

    // ----------- Counter helper --------------------------------------------
    private int getNextSaleId() {
        Document result = countersCollection.findOneAndUpdate(
                Filters.eq("_id", "sales"),
                new Document("$inc", new Document("seq", 1)),
                new com.mongodb.client.model.FindOneAndUpdateOptions()
                        .upsert(true)
                        .returnDocument(com.mongodb.client.model.ReturnDocument.AFTER)
        );
        return result != null ? result.getInteger("seq") : 1;
    }
}
