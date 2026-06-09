package santediagnosticsltd;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    private static String url;
    private static String user;
    private static String password;
    private static boolean configured = false;

    private static void loadConfig() {
        if (configured) return;
        try {
            Properties props = new Properties();
            InputStream input = DBConnection.class
                .getClassLoader()
                .getResourceAsStream("config.properties");
            props.load(input);
            url      = props.getProperty("db.url");
            user     = props.getProperty("db.user");
            password = props.getProperty("db.password");
            configured = true;
        } catch (Exception e) {
            System.err.println("DB config load failed: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        loadConfig();
        System.out.println("[DB DEBUG] Connecting to: " + url + " | User: " + user);
        return DriverManager.getConnection(url, user, password);
    }
}