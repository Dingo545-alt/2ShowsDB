import Config.AppConfig;
import org.jasypt.util.password.PasswordEncryptor;
import org.jasypt.util.password.StrongPasswordEncryptor;
import java.sql.*;

public class EncryptPasswords {
    public static void main (String[] args) {
        try {
            updateCustomerPassword();
            updateEmployeePassword();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    private static void updateCustomerPassword() throws ClassNotFoundException {
        String loginUser = AppConfig.require("MYSQL_USER");
        String loginPasswd = AppConfig.require("MYSQL_PASS");
        String loginUrl = AppConfig.get("MYSQL_URL", "jdbc:mysql://localhost:3306/moviedb");

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

            try (Statement statement = connection.createStatement()) {
                String alterQuery = "ALTER TABLE customers MODIFY COLUMN password VARCHAR(128)";
                statement.executeUpdate(alterQuery);
                System.out.println("Altering customers table schema completed.");
            }

            String updateSql = "UPDATE customers SET password = ? WHERE id = ?";

            PasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();

            System.out.println("Encrypting passwords and preparing updates...");

            try (Statement selectStmt = connection.createStatement();
                 ResultSet resultSet = selectStmt.executeQuery("SELECT id, password FROM customers");
                 PreparedStatement updatePstmt = connection.prepareStatement(updateSql)) {

                int count = 0;
                while (resultSet.next()) {
                    String id = resultSet.getString("id");
                    String plainPassword = resultSet.getString("password");

                    String encryptedPassword = passwordEncryptor.encryptPassword(plainPassword);

                    updatePstmt.setString(1, encryptedPassword);
                    updatePstmt.setString(2, id);

                    updatePstmt.addBatch();
                    count++;

                    if (count % 1000 == 0) {
                        updatePstmt.executeBatch();
                    }
                }
                int[] results = updatePstmt.executeBatch();

                System.out.println("Updating passwords completed.");
                System.out.println("Complete.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void updateEmployeePassword() throws ClassNotFoundException {
        String loginUser = Config.require("MYSQL_USER");
        String loginPasswd = Config.require("MYSQL_PASS");
        String loginUrl = Config.get("MYSQL_URL", "jdbc:mysql://localhost:3306/moviedb");

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

            try (Statement statement = connection.createStatement()) {
                String alterQuery = "ALTER TABLE employees MODIFY COLUMN password VARCHAR(128)";
                statement.executeUpdate(alterQuery);
                System.out.println("Altering customers table schema completed.");
            }

            String updateSql = "UPDATE employees SET password = ? WHERE email = ?";

            PasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();

            System.out.println("Encrypting passwords and preparing updates...");

            try (Statement selectStmt = connection.createStatement();
                 ResultSet resultSet = selectStmt.executeQuery("SELECT email, password FROM employees");
                 PreparedStatement updatePstmt = connection.prepareStatement(updateSql)) {

                int count = 0;
                while (resultSet.next()) {
                    String email = resultSet.getString("email");
                    String plainPassword = resultSet.getString("password");

                    String encryptedPassword = passwordEncryptor.encryptPassword(plainPassword);

                    updatePstmt.setString(1, encryptedPassword);
                    updatePstmt.setString(2, email);

                    updatePstmt.addBatch();
                    count++;

                    if (count % 1000 == 0) {
                        updatePstmt.executeBatch();
                    }
                }
                int[] results = updatePstmt.executeBatch();

                System.out.println("Updating passwords completed.");
                System.out.println("Complete.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}