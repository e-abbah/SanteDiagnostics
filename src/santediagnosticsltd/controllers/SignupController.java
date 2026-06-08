package santediagnosticsltd.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.EmailService;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.Random;
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
        String lastName  = lastNameField.getText().trim();
        String email     = emailField.getText().trim();
        String password  = passwordField.getText();

        // ── Validation ────────────────────────────────────────────────────────
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
            if (checkStmt.executeQuery().next()) {
                errorLabel.setText("An account with this email already exists.");
                return;
            }

            // ── Generate 6-digit verification code ────────────────────────────
            // WHY 6 DIGITS?
            // A clickable link requires a web server — this is a desktop app.
            // A 6-digit code entered into a dialog is the practical equivalent.
            // Random gives 100,000 to 999,999 — always 6 digits.
            String verificationCode = String.valueOf(100000 + new Random().nextInt(900000));

            // Hash password before storing — never store plain text
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // ── Insert user with is_email_verified = FALSE ────────────────────
            // The account exists but is locked until the code is verified.
            // email_verification_token stores the code for comparison later.
            PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO users (first_name, last_name, email, password, role, " +
                "is_first_login, is_email_verified, email_verification_token) " +
                "VALUES (?, ?, ?, ?, 'customer', FALSE, FALSE, ?)"
            );
            insertStmt.setString(1, firstName);
            insertStmt.setString(2, lastName);
            insertStmt.setString(3, email);
            insertStmt.setString(4, hashedPassword);
            insertStmt.setString(5, verificationCode);
            insertStmt.executeUpdate();

            // ── Send verification email on background thread ──────────────────
            // We run this on a new thread so the UI doesn't freeze while
            // waiting for the SMTP server to respond.
            new Thread(() ->
                EmailService.sendVerificationEmail(email, firstName, verificationCode)
            ).start();

            // ── Show verification code dialog ─────────────────────────────────
            // The customer enters the code they received in their email.
            // If correct, we mark is_email_verified = TRUE and redirect to login.
            showVerificationDialog(email);

        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Shows a dialog asking the customer to enter their 6-digit verification code.
     *
     * FLOW:
     * 1. Customer enters code → we check it against email_verification_token in DB
     * 2. Match → set is_email_verified = TRUE, clear token, go to login
     * 3. No match → show error, let them try again
     * 4. Cancel → delete the unverified account (clean up)
     *
     * WHY DELETE ON CANCEL?
     * If we keep unverified accounts forever, someone could block an email
     * address by registering with it and never verifying. Deleting on cancel
     * keeps the DB clean and lets the real owner register later.
     */
    private void showVerificationDialog(String email) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Email Verification");
        dialog.setHeaderText("A 6-digit verification code has been sent to:\n" + email +
                             "\n\nPlease enter the code below to verify your account.");
        dialog.initModality(Modality.APPLICATION_MODAL);

        TextField codeField = new TextField();
        codeField.setPromptText("Enter 6-digit code");
        codeField.setStyle("-fx-font-size: 18px; -fx-alignment: center; " +
                           "-fx-padding: 10; -fx-pref-width: 200;");

        // Restrict input to digits only, max 6 characters
        codeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*") || newVal.length() > 6) {
                codeField.setText(oldVal);
            }
        });

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");

        VBox content = new VBox(12, codeField, statusLabel);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        ButtonType verifyBtnType = new ButtonType("Verify", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(verifyBtnType, ButtonType.CANCEL);

        // Override the verify button to validate before closing
        final Button verifyBtn = (Button) dialog.getDialogPane().lookupButton(verifyBtnType);
        verifyBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String enteredCode = codeField.getText().trim();

            if (enteredCode.length() != 6) {
                statusLabel.setText("Please enter the full 6-digit code.");
                event.consume(); // Prevent dialog from closing
                return;
            }

            try {
                Connection conn = DBConnection.getConnection();

                // Fetch the stored token for this email
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT email_verification_token FROM users WHERE email = ?"
                );
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String storedToken = rs.getString("email_verification_token");

                    if (enteredCode.equals(storedToken)) {
                        // ✅ Code matches — verify the account
                        PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE users SET is_email_verified = TRUE, " +
                            "email_verification_token = NULL WHERE email = ?"
                        );
                        updateStmt.setString(1, email);
                        updateStmt.executeUpdate();

                        // Show success and go to login
                        Alert success = new Alert(Alert.AlertType.INFORMATION);
                        success.setTitle("Verified!");
                        success.setHeaderText(null);
                        success.setContentText("Your email has been verified successfully! " +
                                               "You can now log in.");
                        success.showAndWait();

                        try { navigateTo("/santediagnosticsltd/views/login.fxml"); }
                        catch (Exception ex) { errorLabel.setText("Navigation error: " + ex.getMessage()); }

                    } else {
                        // ❌ Wrong code — show error, keep dialog open
                        statusLabel.setText("❌ Incorrect code. Please check your email and try again.");
                        event.consume();
                    }
                }

            } catch (Exception ex) {
                statusLabel.setText("❌ Error verifying code: " + ex.getMessage());
                event.consume();
            }
        });

        // If customer cancels — delete the unverified account
       Optional<ButtonType> result = dialog.showAndWait();

// If customer closed the dialog without verifying — delete the unverified account
if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
    try {
        Connection conn = DBConnection.getConnection();
        PreparedStatement deleteStmt = conn.prepareStatement(
            "DELETE FROM users WHERE email = ? AND is_email_verified = FALSE"
        );
        deleteStmt.setString(1, email);
        deleteStmt.executeUpdate();
        errorLabel.setText("Registration cancelled. Your account has been removed.");
    } catch (Exception ex) {
        System.err.println("[SIGNUP] Cleanup failed: " + ex.getMessage());
    }
}

        if (result.isEmpty() || result.get().equals("cancelled")) {
            try {
                Connection conn = DBConnection.getConnection();
                PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM users WHERE email = ? AND is_email_verified = FALSE"
                );
                deleteStmt.setString(1, email);
                deleteStmt.executeUpdate();
                errorLabel.setText("Registration cancelled. Your account has been removed.");
            } catch (Exception ex) {
                System.err.println("[SIGNUP] Cleanup failed: " + ex.getMessage());
            }
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
