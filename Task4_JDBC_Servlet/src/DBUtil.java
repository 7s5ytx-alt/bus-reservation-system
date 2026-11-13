import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Task 4: DBUtil
 * Database connection utility for MySQL JDBC connection.
 */
public class DBUtil {

    // Configure these outside source control: BUS_DB_URL, BUS_DB_USER, BUS_DB_PASSWORD.

    static {
        try {
            // Load MySQL 8+ Driver class
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Fallback for older MySQL connector versions
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException ex) {
                System.err.println("MySQL JDBC Driver not found! Ensure mysql-connector-j.jar is in WEB-INF/lib");
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            requireEnvironment("BUS_DB_URL"),
            requireEnvironment("BUS_DB_USER"),
            requireEnvironment("BUS_DB_PASSWORD")
        );
    }

    private static String requireEnvironment(String name) throws SQLException {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new SQLException("Required environment variable is not configured: " + name);
        }
        return value;
    }
}
