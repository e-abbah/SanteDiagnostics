package santediagnosticsltd.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.Session;
import santediagnosticsltd.AuditLogger;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
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

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isPasswordVisible) passwordTextField.setText(newVal);
        });

        passwordTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isPasswordVisible) passwordField.setText(newVal);
        });
    }

    @FXML
    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordField.setVisible(true);
            passwordTextField.setVisible(false);
            togglePasswordBtn.setText("👁");
            isPasswordVisible = false;
        } else {
            passwordField.setVisible(false);
            passwordTextField.setVisible(true);
            togglePasswordBtn.setText("🙈");
            isPasswordVisible = true;
        }
    }

    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim();
        String password = isPasswordVisible ? passwordTextField.getText() : passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please fill in all fields.");
            return;
        }

        System.out.println("[DIAGNOSTIC] Form Submission Email -> '" + email + "'");

        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("password").trim();
                boolean isFirstLogin  = rs.getBoolean("is_first_login");
                String role           = rs.getString("role");

                if (BCrypt.checkpw(password, hashedPassword)) {
                    System.out.println("[DIAGNOSTIC] BCrypt Match Check Result: SUCCESS");

                    // Check email verification for self-registered customers only
                    if ("customer".equals(role) && !rs.getBoolean("is_email_verified")) {
                        errorLabel.setText("Please verify your email before logging in.");
                        return;
                    }

                    int id      = rs.getInt("id");
                    String name = rs.getString("first_name") + " " + rs.getString("last_name");

                    Session.login(id, name, role, email);
                    AuditLogger.log(id, "USER_LOGIN", "users", id);

                    // Force password change for staff-created accounts
                    if (isFirstLogin) {
                        navigateTo("/santediagnosticsltd/views/change-password.fxml");
                        return;
                    }

                    switch (role) {
                        case "super_admin"   -> navigateTo("/santediagnosticsltd/views/super-admin-dashboard.fxml");
                        case "lab_attendant" -> navigateTo("/santediagnosticsltd/views/lab-attendant-dashboard.fxml");
                        case "customer"      -> navigateTo("/santediagnosticsltd/views/customer-dashboard.fxml");
                        default              -> errorLabel.setText("Unknown role. Contact admin.");
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

    // ══════════════════════════════════════════════════════════════════════════
    // FORGOT PASSWORD
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleForgotPassword() {

        // STEP 1: Ask for email
        TextInputDialog emailDialog = new TextInputDialog();
        emailDialog.setTitle("Forgot Password");
        emailDialog.setHeaderText("Enter your registered email address.");
        emailDialog.setContentText("Email:");

        Optional<String> emailResult = emailDialog.showAndWait();
        if (emailResult.isEmpty() || emailResult.get().trim().isEmpty()) return;

        String email = emailResult.get().trim();
        System.out.println("[FORGOT PWD DEBUG] Email entered: '" + email + "' | Length: " + email.length());

        try {
            Connection conn = DBConnection.getConnection();

            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id, first_name FROM users WHERE email = ?"
            );
            checkStmt.setString(1, email);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                errorLabel.setText("No account found with that email address.");
                return;
            }

            String firstName = rs.getString("first_name");
            System.out.println("[FORGOT PWD DEBUG] Found user: " + firstName);

            // STEP 2: Generate reset code and save to DB
            // Reusing email_verification_token — same purpose, one-time identity code
            String resetCode = String.valueOf(100000 + new java.util.Random().nextInt(900000));

            PreparedStatement updateStmt = conn.prepareStatement(
                "UPDATE users SET email_verification_token = ? WHERE email = ?"
            );
            updateStmt.setString(1, resetCode);
            updateStmt.setString(2, email);
            int rowsUpdated = updateStmt.executeUpdate();
            System.out.println("[FORGOT PWD DEBUG] Rows updated: " + rowsUpdated + " | Code: " + resetCode);

            // Send reset email on background thread
            new Thread(() ->
                santediagnosticsltd.EmailService.sendPasswordResetEmail(email, firstName, resetCode)
            ).start();

            // STEP 3: Show reset dialog
            showPasswordResetDialog(email);

        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showPasswordResetDialog(String email) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Enter the 6-digit code sent to:\n" + email);

        TextField codeField = new TextField();
        codeField.setPromptText("6-digit reset code");
        codeField.setStyle("-fx-font-size: 15px; -fx-padding: 8;");

        // Restrict to digits only, max 6 characters
        codeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*") || newVal.length() > 6) codeField.setText(oldVal);
        });

        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("New password (min 6 characters)");
        newPassField.setStyle("-fx-font-size: 13px; -fx-padding: 8;");

        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Confirm new password");
        confirmPassField.setStyle("-fx-font-size: 13px; -fx-padding: 8;");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10,
            new Label("Reset Code:"),       codeField,
            new Label("New Password:"),     newPassField,
            new Label("Confirm Password:"), confirmPassField,
            statusLabel
        );
        content.setPadding(new javafx.geometry.Insets(20));
        content.setPrefWidth(340);
        dialog.getDialogPane().setContent(content);

        ButtonType resetBtnType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetBtnType, ButtonType.CANCEL);

        final Button resetBtn = (Button) dialog.getDialogPane().lookupButton(resetBtnType);

        resetBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String enteredCode = codeField.getText().trim();
            String newPass     = newPassField.getText();
            String confirmPass = confirmPassField.getText();

            if (enteredCode.length() != 6) {
                statusLabel.setText("❌ Please enter the full 6-digit code.");
                event.consume(); return;
            }
            if (newPass.length() < 6) {
                statusLabel.setText("❌ Password must be at least 6 characters.");
                event.consume(); return;
            }
            if (!newPass.equals(confirmPass)) {
                statusLabel.setText("❌ Passwords do not match.");
                event.consume(); return;
            }

            try {
                Connection conn = DBConnection.getConnection();

                // STEP 4: Verify code against stored token
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT email_verification_token FROM users WHERE email = ?"
                );
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String storedToken = rs.getString("email_verification_token");

                    // Debug — helps confirm what's being compared
                    System.out.println("[RESET DEBUG] Stored token: '" + storedToken + "' | Entered: '" + enteredCode + "'");

                    if (enteredCode.equals(storedToken)) {
                        // Code matches — update password and clear token
                        String hashedPassword = BCrypt.hashpw(newPass, BCrypt.gensalt());

                        PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE users SET password = ?, email_verification_token = NULL WHERE email = ?"
                        );
                        updateStmt.setString(1, hashedPassword);
                        updateStmt.setString(2, email);
                        updateStmt.executeUpdate();

                        // Use 0 — AuditLogger handles NULL via NULLIF(?,0)
                        // since no user is logged in during password reset
                        AuditLogger.log(0, "PASSWORD_RESET: " + email, "users", 0);

                        Alert success = new Alert(
                            Alert.AlertType.INFORMATION,
                            "Password reset successfully! You can now log in.",
                            ButtonType.OK
                        );
                        success.setHeaderText(null);
                        success.showAndWait();

                    } else {
                        statusLabel.setText("❌ Incorrect code. Please check your email.");
                        event.consume();
                    }
                } else {
                    statusLabel.setText("❌ No reset request found for this email.");
                    event.consume();
                }

            } catch (Exception ex) {
                statusLabel.setText("❌ Error: " + ex.getMessage());
                ex.printStackTrace();
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void goToSignup() {
        try {
            navigateTo("/santediagnosticsltd/views/signup.fxml");
        } catch (Exception e) {
            errorLabel.setText("Could not load signup page.");
        }
    }

    private void navigateTo(String fxmlPath) throws Exception {
        URL url = getClass().getResource(fxmlPath);

        if (url == null) {
            String cleanPath = fxmlPath.startsWith("/") ? fxmlPath.substring(1) : fxmlPath;
            url = getClass().getClassLoader().getResource(cleanPath);
        }

        if (url == null) {
            String filename = fxmlPath.substring(fxmlPath.lastIndexOf("/") + 1);
            url = getClass().getResource("../views/" + filename);
        }

        if (url == null) {
            System.out.println("[CRITICAL] Classpath resolution failed for: " + fxmlPath);
            errorLabel.setText("View file missing from build paths!");
            return;
        }

        System.out.println("[DIAGNOSTIC] Target FXML URL resolved successfully: " + url.toExternalForm());

        Parent root = FXMLLoader.load(url);
        Stage stage = (Stage) emailField.getScene().getWindow();
        Scene scene = new Scene(root);

        URL cssUrl = getClass().getResource("/santediagnosticsltd/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            URL altCss = getClass().getClassLoader().getResource("santediagnosticsltd/css/styles.css");
            if (altCss != null) scene.getStylesheets().add(altCss.toExternalForm());
        }

        stage.setScene(scene);
        stage.centerOnScreen();
    }
}
