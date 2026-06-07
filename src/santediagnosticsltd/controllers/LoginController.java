package santediagnosticsltd.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.Session;
import santediagnosticsltd.AuditLogger;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordTextField;
    @FXML private Button togglePasswordBtn;
    @FXML private Label errorLabel;

    private boolean isPasswordVisible = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        errorLabel.setText("");

        // Real-time synchronization binding between plain text and masked field layers
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isPasswordVisible) {
                passwordTextField.setText(newVal);
            }
        });

        passwordTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isPasswordVisible) {
                passwordField.setText(newVal);
            }
        });
    }

    @FXML
    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            // Revert back to safe hidden dot mask view
            passwordField.setVisible(true);
            passwordTextField.setVisible(false);
            togglePasswordBtn.setText("👁");
            isPasswordVisible = false;
        } else {
            // Expose values via readable plain text layout field
            passwordField.setVisible(false);
            passwordTextField.setVisible(true);
            togglePasswordBtn.setText("🙈");
            isPasswordVisible = true;
        }
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        
        // Grabs directly from the active visible component to prevent JavaFX thread sync lag
        String password = isPasswordVisible ? passwordTextField.getText() : passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please fill in all fields.");
            return;
        }

        // Live Diagnostic Console Hook
        System.out.println("[DIAGNOSTIC] Form Submission Email -> '" + email + "'");
        System.out.println("[DIAGNOSTIC] Form Submission Password Text -> '" + password + "'");

        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("password").trim();
                boolean isFirstLogin = rs.getBoolean("is_first_login");
                String role = rs.getString("role");

                System.out.println("[DIAGNOSTIC] User match found in DB!");
                System.out.println("[DIAGNOSTIC] Hashed password saved inside your Table -> " + hashedPassword);

                if (BCrypt.checkpw(password, hashedPassword)) {
                    System.out.println("[DIAGNOSTIC] BCrypt Match Check Result: SUCCESS");
                    
                    int id = rs.getInt("id");
                    String name = rs.getString("first_name") + " " + rs.getString("last_name");

                    // Store in session
                    Session.login(id, name, role, email);

                    // Log the action
                    AuditLogger.log(id, "USER_LOGIN", "users", id);

                    // Check if first login — force password change
                    if (isFirstLogin) {
                        navigateTo("/santediagnosticsltd/views/change-password.fxml");
                        return;
                    }

                    // Redirect based on role
                    switch (role) {
                        case "super_admin" ->
                            navigateTo("/santediagnosticsltd/views/super-admin-dashboard.fxml");
                        case "lab_attendant" ->
                            navigateTo("/santediagnosticsltd/views/lab-attendant-dashboard.fxml");
                        case "customer" ->
                            navigateTo("/santediagnosticsltd/views/customer-dashboard.fxml");
                        default ->
                            errorLabel.setText("Unknown role. Contact admin.");
                    }

                } else {
                    System.out.println("[DIAGNOSTIC] BCrypt Match Check Result: FAILED (Wrong Password Match)");
                    errorLabel.setText("Invalid email or password.");
                }

            } else {
                System.out.println("[DIAGNOSTIC] Database Lookup Result: FAILED (0 Rows matched user string criteria)");
                errorLabel.setText("Invalid email or password.");
            }

        } catch (Exception e) {
            System.out.println("[DIAGNOSTIC] Driver Critical Crash -> " + e.getMessage());
            e.printStackTrace();
            errorLabel.setText("Connection error: " + e.getMessage());
        }
    }

    @FXML
    private void goToSignup() {
        try {
            navigateTo("/santediagnosticsltd/views/signup.fxml");
        } catch (Exception e) {
            errorLabel.setText("Could not load signup page.");
        }
    }

    /**
     * Hardened view-switching router with multi-layered resource path resolution.
     */
    private void navigateTo(String fxmlPath) throws Exception {
        URL url = getClass().getResource(fxmlPath);
        
        // Fallback 1: Try resolving via Root ClassLoader context (Common for Maven layouts)
        if (url == null) {
            String cleanPath = fxmlPath.startsWith("/") ? fxmlPath.substring(1) : fxmlPath;
            url = getClass().getClassLoader().getResource(cleanPath);
        }
        
        // Fallback 2: Try relative directory stepping from controller package layout
        if (url == null) {
            String filename = fxmlPath.substring(fxmlPath.lastIndexOf("/") + 1);
            url = getClass().getResource("../views/" + filename);
        }

        // Safeguard crash handler to report missing build assets cleanly
        if (url == null) {
            System.out.println("[CRITICAL] Classpath resolution failed completely for file: " + fxmlPath);
            errorLabel.setText("View File Asset missing from build paths!");
            return;
        }

        System.out.println("[DIAGNOSTIC] Target FXML URL resolved successfully: " + url.toExternalForm());
        
        Parent root = FXMLLoader.load(url);
        Stage stage = (Stage) emailField.getScene().getWindow();
        Scene scene = new Scene(root);
        
        // Safe contextual CSS attachment
        URL cssUrl = getClass().getResource("/santediagnosticsltd/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            // Classloader alternative pathing for styles sheet lookups
            URL altCss = getClass().getClassLoader().getResource("santediagnosticsltd/css/styles.css");
            if (altCss != null) scene.getStylesheets().add(altCss.toExternalForm());
        }
        
        stage.setScene(scene);
        stage.centerOnScreen();
    }
}