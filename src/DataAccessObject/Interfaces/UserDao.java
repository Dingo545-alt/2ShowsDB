package DataAccessObject.Interfaces;

import Model.Customer;
import Model.Employee;

public interface UserDao {
    String getPasswordForCustomer(String email);
    String getPasswordForEmployee(String email);

    Customer getCustomerByEmail(String email);
    Employee getEmployeeByEmail(String email);
}
