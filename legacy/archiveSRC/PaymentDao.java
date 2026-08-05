package DataAccessObject.Interfaces;

import Model.SaleRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PaymentDao {


    Map<String, Double> getPricesForCart(Collection<String> movieIds);

    int validateCardAndGetCustomerId(String email,
                                     String creditCardNumber,
                                     String firstName,
                                     String lastName,
                                     String expiration);

    List<SaleRecord> insertSales(int customerId,
                                 Map<String, Integer> cart,
                                 Map<String, Double> prices);
}
