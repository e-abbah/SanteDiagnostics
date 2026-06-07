package santediagnosticsltd.controllers;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import santediagnosticsltd.AuditLogger;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.Session;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;

public class CustomerDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Pane contentPane;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + Session.getUserName());
        showHome();
        
        // Execute security interceptor once layout initialization completes
        Platform.runLater(this::checkPasswordResetRequired);
    }

    // ─── HOME ────────────────────────────────────────────────
    @FXML
    private void showHome() {
        contentPane.getChildren().clear();

        VBox home = new VBox(20);
        home.setPadding(new Insets(30));
        home.prefWidthProperty().bind(contentPane.widthProperty());
        home.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("Patient Dashboard");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Welcome back, " + Session.getUserName() + 
                                   ". Manage your tests and results here.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        HBox cards = new HBox(15);
        VBox ordersCard = buildSummaryCard("📋 My Orders", getOrderCount() + " orders", "#3b5bdb");
        VBox resultsCard = buildSummaryCard("📊 My Results", getResultCount() + " results", "#10b981");
        VBox pendingCard = buildSummaryCard("⏳ Pending", getPendingCount() + " pending", "#f59e0b");

        cards.getChildren().addAll(ordersCard, resultsCard, pendingCard);
        home.getChildren().addAll(title, subtitle, cards);
        contentPane.getChildren().add(home);
    }

    private VBox buildSummaryCard(String title, String value, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setPrefWidth(180);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    // ─── BROWSE TESTS ─────────────────────────────────────────
    @FXML
    private void showBrowseTests() {
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("Available Tests");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        VBox testList = new VBox(10);

        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM test_types ORDER BY category");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String category = rs.getString("category");
                double price = rs.getDouble("price");
                int tat = rs.getInt("turnaround_hours");

                HBox card = buildTestCard(id, name, category, price, tat);
                testList.getChildren().add(card);
            }

            if (testList.getChildren().isEmpty()) {
                Label empty = new Label("No tests available yet. Check back later.");
                empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
                testList.getChildren().add(empty);
            }

        } catch (Exception e) {
            Label error = new Label("Error loading tests: " + e.getMessage());
            error.setStyle("-fx-text-fill: red;");
            testList.getChildren().add(error);
        }

        ScrollPane scroll = new ScrollPane(testList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        container.getChildren().addAll(title, scroll);
        contentPane.getChildren().add(container);
    }

    private HBox buildTestCard(int id, String name, String category, double price, int tat) {
        HBox card = new HBox(15);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 5, 0, 0, 1);");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label categoryLabel = new Label("Category: " + category + "  •  TAT: " + tat + " hours");
        categoryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        info.getChildren().addAll(nameLabel, categoryLabel);

        Label priceLabel = new Label("支" + String.format("%,.2f", price));
        priceLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #3b5bdb;");

        Button orderBtn = new Button("Request Test");
        orderBtn.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white;" +
                          "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 16 8 16;");
        orderBtn.setOnAction(e -> handleOrderTest(id, name, price, tat));

        card.getChildren().addAll(info, priceLabel, orderBtn);
        return card;
    }

    private void handleOrderTest(int testTypeId, String testName, double price, int tat) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Request " + testName + " for 支" + String.format("%,.2f", price) + "?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Test Request");
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    Connection conn = DBConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO test_requests (customer_id, test_type_id, status, payment_status, expected_at) " +
                        "VALUES (?, ?, 'pending', 'unpaid', NOW() + INTERVAL '" + tat + " hours')"
                    );
                    stmt.setInt(1, Session.getUserId());
                    stmt.setInt(2, testTypeId);
                    stmt.executeUpdate();

                    AuditLogger.log(Session.getUserId(), "TEST_REQUESTED: " + testName, "test_requests", testTypeId);

                    Alert paymentInfo = new Alert(Alert.AlertType.INFORMATION);
                    paymentInfo.setTitle("Payment Instructions");
                    paymentInfo.setHeaderText("Test Requested Successfully!");
                    paymentInfo.setContentText(
                        "Please make payment via bank transfer:\n\n" +
                        "Bank: First Bank Nigeria\n" +
                        "Account Name: Sante Diagnostics Ltd\n" +
                        "Account Number: 1234567890\n" +
                        "Amount: 支" + String.format("%,.2f", price) + "\n\n" +
                        "Your test will be processed after payment confirmation."
                    );
                    paymentInfo.showAndWait();
                    showMyOrders();

                } catch (Exception e) {
                    showError("Failed to place order: " + e.getMessage());
                }
            }
        });
    }

    // ─── MY ORDERS ────────────────────────────────────────────
    @FXML
    private void showMyOrders() {
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("My Orders");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ObservableList<String>, String> idCol = new TableColumn<>("Order #");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(0)));

        TableColumn<ObservableList<String>, String> testCol = new TableColumn<>("Test");
        testCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(1)));

        TableColumn<ObservableList<String>, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(2)));

        TableColumn<ObservableList<String>, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(3)));

        TableColumn<ObservableList<String>, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(4)));

        table.getColumns().addAll(idCol, testCol, statusCol, paymentCol, dateCol);
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT tr.id, tt.name, tr.status, tr.payment_status, tr.created_at " +
                "FROM test_requests tr " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "WHERE tr.customer_id = ? ORDER BY tr.created_at DESC"
            );
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("status").toUpperCase());
                row.add(rs.getString("payment_status").toUpperCase());
                row.add(rs.getTimestamp("created_at").toString().substring(0, 16));
                data.add(row);
            }
        } catch (Exception e) {
            showError("Error loading orders: " + e.getMessage());
        }

        table.setItems(data);
        VBox.setVgrow(table, Priority.ALWAYS);
        container.getChildren().addAll(title, table);
        contentPane.getChildren().add(container);
    }

    // ─── MY RESULTS ───────────────────────────────────────────
    @FXML
    private void showMyResults() {
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("My Results");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        VBox resultsList = new VBox(10);

        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT r.id, tt.name, r.result_value, r.file_path, r.uploaded_at, tr.expected_at " +
                "FROM results r " +
                "JOIN test_requests tr ON r.test_request_id = tr.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "WHERE tr.customer_id = ? AND r.is_validated = TRUE " +
                "ORDER BY r.uploaded_at DESC"
            );
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();

            boolean hasResults = false;
            while (rs.next()) {
                hasResults = true;
                HBox resultCard = buildResultCard(
                    rs.getString("name"),
                    rs.getString("result_value"),
                    rs.getString("file_path"),
                    rs.getTimestamp("uploaded_at").toString().substring(0, 16)
                );
                resultsList.getChildren().add(resultCard);
            }

            if (!hasResults) {
                Label empty = new Label("No validated results yet.");
                empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
                resultsList.getChildren().add(empty);
            }
        } catch (Exception e) {
            Label error = new Label("Error loading results: " + e.getMessage());
            error.setStyle("-fx-text-fill: red;");
            resultsList.getChildren().add(error);
        }

        ScrollPane scroll = new ScrollPane(resultsList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        container.getChildren().addAll(title, scroll);
        contentPane.getChildren().add(container);
    }

    private HBox buildResultCard(String testName, String resultValue, String filePath, String date) {
        HBox card = new HBox(15);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 5, 0, 0, 1);");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(testName);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label dateLabel = new Label("Result date: " + date);
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label valueLabel = new Label(resultValue != null ? "Result value: " + resultValue : "Report attached below");
        valueLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

        info.getChildren().addAll(nameLabel, dateLabel, valueLabel);

        VBox actionArea = new VBox(6);
        actionArea.setAlignment(Pos.CENTER_RIGHT);

        Label statusLabel = new Label("✅ Validated");
        statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 12px;");
        actionArea.getChildren().add(statusLabel);

        if (filePath != null && !filePath.trim().isEmpty()) {
            Button downloadBtn = new Button("📥 Download PDF");
            downloadBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 11px;" +
                                 "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
            downloadBtn.setOnAction(e -> handleDownloadPDF(filePath, testName));
            actionArea.getChildren().add(downloadBtn);
        }

        card.getChildren().addAll(info, actionArea);
        return card;
    }

    private void handleDownloadPDF(String sourcePath, String testName) {
        try {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                showError("The original file could not be found on the server directory storage.");
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Lab Report");
            String safeName = testName.replaceAll("[^a-zA-Z0-9]", "_");
            fileChooser.setInitialFileName(safeName + "_Report.pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            File destFile = fileChooser.showSaveDialog(stage);

            if (destFile != null) {
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Report file downloaded successfully!", ButtonType.OK);
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.showAndWait();

                AuditLogger.log(Session.getUserId(), "PDF_DOWNLOAD: " + testName, "results", Session.getUserId());
            }
        } catch (Exception e) {
            showError("Failed to copy file to directory: " + e.getMessage());
        }
    }

    // ─── LOGOUT ───────────────────────────────────────────────
    @FXML
    private void handleLogout() {
        try {
            AuditLogger.log(Session.getUserId(), "USER_LOGOUT", "users", Session.getUserId());
            Session.logout();
            Parent root = FXMLLoader.load(getClass().getResource("/santediagnosticsltd/views/login.fxml"));
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/santediagnosticsltd/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("Logout failed: " + e.getMessage());
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────
    private int getOrderCount() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM test_requests WHERE customer_id = ?");
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { return 0; }
        return 0;
    }

    private int getResultCount() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM results r JOIN test_requests tr ON r.test_request_id = tr.id " +
                "WHERE tr.customer_id = ? AND r.is_validated = TRUE"
            );
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { return 0; }
        return 0;
    }

    private int getPendingCount() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM test_requests WHERE customer_id = ? AND status = 'pending'");
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { return 0; }
        return 0;
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    // ─── FIRST LOGIN SECURITY CHECKS ──────────────────────────
    private void checkPasswordResetRequired() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT must_change_password FROM users WHERE id = ?"
            );
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && rs.getBoolean("must_change_password")) {
                showForcePasswordChangeModal();
            }
        } catch (Exception e) {
            showError("Security check failed: " + e.getMessage());
        }
    }

    private void showForcePasswordChangeModal() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Security Update Required");
        dialog.setHeaderText("First-Time Login Secure Setup\nPlease choose a new password for your Sante Diagnostics account.");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(welcomeLabel.getScene().getWindow());

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Enter new password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");

        VBox formLayout = new VBox(10, 
            new Label("New Password:"), newPasswordField, 
            new Label("Confirm Password:"), confirmPasswordField
        );
        formLayout.setPadding(new Insets(20));
        formLayout.setPrefWidth(320);
        dialog.getDialogPane().setContent(formLayout);

        ButtonType submitBtnType = new ButtonType("Update Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitBtnType, ButtonType.CANCEL);

        final Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitBtnType);
        submitBtn.addEventFilter(ActionEvent.ACTION, event -> {
            String pass = newPasswordField.getText().trim();
            String confirm = confirmPasswordField.getText().trim();

            if (pass.isEmpty() || !pass.equals(confirm)) {
                showError("Passwords do not match or fields are empty!");
                event.consume(); 
            } else if (pass.length() < 6) {
                showError("Password must be at least 6 characters long!");
                event.consume();
            } else {
                try {
                    Connection conn = DBConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE users SET password = ?, must_change_password = FALSE WHERE id = ?"
                    );
                    String hashedPass = org.mindrot.jbcrypt.BCrypt.hashpw(
                        pass,
                        org.mindrot.jbcrypt.BCrypt.gensalt()
                    );

                    stmt.setString(1, hashedPass); 
                    stmt.setInt(2, Session.getUserId());
                    stmt.executeUpdate();

                    AuditLogger.log(Session.getUserId(), "PASSWORD_FIRST_RESET_SUCCESS", "users", Session.getUserId());
                    
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION, "Password updated successfully!", ButtonType.OK);
                    successAlert.setHeaderText(null);
                    successAlert.showAndWait();

                } catch (Exception e) {
                    showError("Database failed to update password: " + e.getMessage());
                    event.consume();
                }
            }
        });

        dialog.showAndWait().ifPresent(response -> {
            if (response != submitBtnType) {
                handleLogout();
            }
        });
    }
}