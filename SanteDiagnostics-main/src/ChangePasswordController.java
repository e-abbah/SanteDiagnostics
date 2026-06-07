package santediagnosticsltd.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import santediagnosticsltd.AuditLogger;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.Session;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ResourceBundle;

public class ChangePasswordController implements Initializable {

    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        errorLabel.setText("");
    }

    @FXML
    private void handleChangePassword() {
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Please fill in all fields.");
            return;
        }

        if (newPassword.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match.");
            return;
        }

        try {
            Connection conn = DBConnection.getConnection();

            // Hash the new password securely
            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());

            // FIXED SQL: Changed 'is_first_login' to 'must_change_password' to match database and dashboard flags
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET password = ?, must_change_password = FALSE WHERE id = ?"
            );
            stmt.setString(1, hashedPassword);
            stmt.setInt(2, Session.getUserId());
            stmt.executeUpdate();

            // Log the action
            AuditLogger.log(
                Session.getUserId(),
                "PASSWORD_CHANGED",
                "users",
                Session.getUserId()
            );

            // Redirect based on role
            String role = Session.getUserRole();
            switch (role) {
                case "super_admin" ->
                    navigateTo("/santediagnosticsltd/views/super-admin-dashboard.fxml");
                case "lab_attendant" ->
                    navigateTo("/santediagnosticsltd/views/lab-attendant-dashboard.fxml");
                case "customer" ->
                    navigateTo("/santediagnosticsltd/views/customer-dashboard.fxml");
            }

        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    private void navigateTo(String fxmlPath) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) newPasswordField.getScene().getWindow();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(
            getClass().getResource("/santediagnosticsltd/css/styles.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.centerOnScreen();
    }
}