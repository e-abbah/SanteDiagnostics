package santediagnosticsltd.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import santediagnosticsltd.DBConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;

public class SignupController implements Initializable {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        errorLabel.setText("");
    }

    @FXML
    private void handleSignup() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        // Validation
        if (firstName.isEmpty() || lastName.isEmpty() || 
            email.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please fill in all fields.");
            return;
        }

        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            errorLabel.setText("Please enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters.");
            return;
        }

        try {
            Connection conn = DBConnection.getConnection();

            // Check if email already exists
            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM users WHERE email = ?"
            );
            checkStmt.setString(1, email);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                errorLabel.setText("An account with this email already exists.");
                return;
            }

            // Hash password
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // Explicitly map all parameters to prevent driver execution glitches
            PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO users (first_name, last_name, email, password, role, is_first_login) " +
                "VALUES (?, ?, ?, ?, ?, FALSE)"
            );
            insertStmt.setString(1, firstName);
            insertStmt.setString(2, lastName);
            insertStmt.setString(3, email);
            insertStmt.setString(4, hashedPassword);
            insertStmt.setString(5, "customer"); // Explicitly setting the role here
            insertStmt.executeUpdate();

            // Native Information Alert Pop-up
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Registration Successful");
            alert.setHeaderText(null); 
            alert.setContentText("User account created successfully!");
            
            // Blocks thread execution until user clicks "OK"
            alert.showAndWait();

            // Navigate back to the sign-in view
            navigateTo("/santediagnosticsltd/views/login.fxml");

        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void goToLogin() {
        try {
            navigateTo("/santediagnosticsltd/views/login.fxml");
        } catch (Exception e) {
            errorLabel.setText("Could not load login page.");
        }
    }

    private void navigateTo(String fxmlPath) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) emailField.getScene().getWindow();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(
            getClass().getResource("/santediagnosticsltd/css/styles.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.centerOnScreen();
    }
}