package DataAccessObject.Interfaces;

import Model.CartItemDetails;

import java.util.Collection;
import java.util.Map;

public interface CartDao {
    Map<String, CartItemDetails> getCartItemDetails(Collection<String> movieIds);
}
