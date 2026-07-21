package DataAccessObject.MongoDB;

import DataAccessObject.DaoFactory;
import DataAccessObject.Interfaces.UserDao;
import Model.CreditCard;
import Model.Customer;
import Model.Employee;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;

import java.util.Date;
import java.text.SimpleDateFormat;

public class MongoUserDao implements UserDao {
    private final MongoCollection<Document> customerCollection;
    private final MongoCollection<Document> employeeCollection;

    public MongoUserDao() {
        MongoClient mongoClient = DaoFactory.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("moviedb");
        this.customerCollection = database.getCollection("customers");
        this.employeeCollection = database.getCollection("employees");
    }

    @Override
    public String getPasswordForCustomer(String email) {
        Document doc = customerCollection.find(Filters.eq("email", email))
                .projection(Projections.include("password"))
                .first();
        return doc != null ? doc.getString("password") : null;
    }

    @Override
    public String getPasswordForEmployee(String email) {
        Document doc = employeeCollection.find(Filters.eq("_id", email))
                .projection(Projections.include("password"))
                .first();
        return doc != null ? doc.getString("password") : null;
    }

    @Override
    public Customer getCustomerByEmail(String email) {
        Document doc = customerCollection.find(Filters.eq("email", email)).first();
        if  (doc == null) return null;

        Customer customer = new Customer();
        customer.setId(doc.getInteger("_id"));
        customer.setFirstName(doc.getString("first_name"));
        customer.setLastName(doc.getString("last_name"));
        customer.setEmail(doc.getString("email"));
        customer.setPassword(doc.getString("password"));

        Document ccDoc = (Document) doc.get("credit_card");
        if (ccDoc != null) {
            CreditCard cc = new CreditCard();
            cc.setId(ccDoc.getString("id"));
            cc.setFirstName(ccDoc.getString("first_name"));
            cc.setLastName(ccDoc.getString("last_name"));
            try {
                String expStr = doc.getString("expiration");
                if  (expStr != null) {
                    Date date = new SimpleDateFormat("yyyy-MM-dd").parse(expStr);
                    cc.setExpiration(date);
                }
            } catch(Exception e) {
                cc.setExpiration(null);
            }
            customer.setCreditCard(cc);
        }
        return customer;
    }

    @Override
    public Employee getEmployeeByEmail(String email) {
        Document doc = employeeCollection.find(Filters.eq("email", email)).first();
        if  (doc == null) return null;
        return new Employee(doc.getString("_id"), doc.getString("password"),
                doc.getString("fullname"));
    }
}
