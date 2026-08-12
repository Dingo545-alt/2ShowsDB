package DataAccessObject.Interfaces;

import Model.User;

public interface UserDao {
    /** Returns false without creating anything if the username is already taken. */
    boolean createUser(User user);

    String getPasswordForUsername(String username);

    User getUserByUsername(String username);
}