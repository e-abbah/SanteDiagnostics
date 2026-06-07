package santediagnosticsltd.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class CustomerDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Pane contentPane;

    // Holds all active countdown timers so we can stop them when switching screens
    // WHY? If we don't stop timers, they keep running in the background and
    // try to update labels that no longer exist on screen — causing crashes.
    private final List<Timeline> activeTimers = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + Session.getUserName());
        showHome();
        Platform.runLater(this::checkPasswordResetRequired);
    }

    // ── Helper: stop all running timers ──────────────────────────────────────
    private void stopAllTimers() {
        activeTimers.forEach(Timeline::stop);
        activeTimers.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 1 — HOME
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showHome() {
        stopAllTimers();
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
        VBox ordersCard  = buildSummaryCard("📋 My Orders",  getOrderCount()  + " orders",  "#3b5bdb");
        VBox resultsCard = buildSummaryCard("📊 My Results", getResultCount() + " results", "#10b981");
        VBox pendingCard = buildSummaryCard("⏳ Pending",    getPendingCount()+ " pending", "#f59e0b");
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

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 2 — BROWSE TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showBrowseTests() {
        stopAllTimers();
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
                int id       = rs.getInt("id");
                String name  = rs.getString("name");
                String cat   = rs.getString("category");
                double price = rs.getDouble("price");
                int tat      = rs.getInt("turnaround_hours");
                testList.getChildren().add(buildTestCard(id, name, cat, price, tat));
            }

            if (testList.getChildren().isEmpty()) {
                Label empty = new Label("No tests available yet. Check back later.");
                empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
                testList.getChildren().add(empty);
            }
        } catch (Exception e) {
            testList.getChildren().add(new Label("Error loading tests: " + e.getMessage()));
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

        Label priceLabel = new Label("₦" + String.format("%,.2f", price));
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
            "Request " + testName + " for ₦" + String.format("%,.2f", price) + "?",
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
                        "Amount: ₦" + String.format("%,.2f", price) + "\n\n" +
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

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 3 — MY ORDERS
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showMyOrders() {
        stopAllTimers();
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("My Orders");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"Order #", "Test", "Status", "Payment", "Date"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(idx)));
            table.getColumns().add(col);
        }

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

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 4 — MY RESULTS
    // Includes: validated results, countdown timer for pending, PDF download
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showMyResults() {
        stopAllTimers();
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("My Results");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        VBox resultsList = new VBox(12);

        try {
            Connection conn = DBConnection.getConnection();

            // ── SECTION A: Validated results (ready to view/download) ─────────
            PreparedStatement validatedStmt = conn.prepareStatement(
                "SELECT r.id, tt.name, r.result_value, r.file_path, r.uploaded_at, tr.expected_at " +
                "FROM results r " +
                "JOIN test_requests tr ON r.test_request_id = tr.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "WHERE tr.customer_id = ? AND r.is_validated = TRUE " +
                "ORDER BY r.uploaded_at DESC"
            );
            validatedStmt.setInt(1, Session.getUserId());
            ResultSet validatedRs = validatedStmt.executeQuery();

            boolean hasValidated = false;
            while (validatedRs.next()) {
                hasValidated = true;
                HBox card = buildValidatedResultCard(
                    validatedRs.getString("name"),
                    validatedRs.getString("result_value"),
                    validatedRs.getString("file_path"),
                    validatedRs.getTimestamp("uploaded_at").toString().substring(0, 16)
                );
                resultsList.getChildren().add(card);
            }

            // ── SECTION B: Pending results with countdown timer ───────────────
            // Shows active orders that are not yet validated, with a live
            // countdown showing how much time remains until the result is due.
            PreparedStatement pendingStmt = conn.prepareStatement(
                "SELECT tr.id, tt.name, tr.status, tr.expected_at " +
                "FROM test_requests tr " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "WHERE tr.customer_id = ? AND tr.status != 'validated' " +
                "ORDER BY tr.created_at DESC"
            );
            pendingStmt.setInt(1, Session.getUserId());
            ResultSet pendingRs = pendingStmt.executeQuery();

            boolean hasPending = false;
            while (pendingRs.next()) {
                hasPending = true;
                Timestamp expectedAt = pendingRs.getTimestamp("expected_at");
                HBox card = buildPendingResultCard(
                    pendingRs.getInt("id"),
                    pendingRs.getString("name"),
                    pendingRs.getString("status"),
                    expectedAt
                );
                resultsList.getChildren().add(card);
            }

            if (!hasValidated && !hasPending) {
                Label empty = new Label("No test results yet. Place an order to get started.");
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

    /**
     * Builds a card for a VALIDATED result — shows result value, date,
     * and a Download PDF button if a file is attached.
     */
    private HBox buildValidatedResultCard(String testName, String resultValue,
                                           String filePath, String date) {
        HBox card = new HBox(15);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 5, 0, 0, 1);" +
                      "-fx-border-color: #10b981; -fx-border-width: 0 0 0 4; -fx-border-radius: 0 8 8 0;");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(testName);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        Label dateLabel = new Label("Result date: " + date);
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
        Label valueLabel = new Label(resultValue != null ? "Result: " + resultValue : "Report file attached");
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

    /**
     * Builds a card for a PENDING result — shows current status and a
     * LIVE COUNTDOWN TIMER showing time remaining until the result is due.
     *
     * HOW THE TIMER WORKS:
     * - JavaFX Timeline fires every second
     * - Each tick recalculates hours/minutes/seconds remaining
     * - The label updates in real time on the UI thread
     * - When time runs out, it shows "Result Due Now"
     * - Timer is stored in activeTimers so it can be stopped when leaving the screen
     */
    private HBox buildPendingResultCard(int requestId, String testName,
                                         String status, Timestamp expectedAt) {
        HBox card = new HBox(15);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 5, 0, 0, 1);" +
                      "-fx-border-color: #f59e0b; -fx-border-width: 0 0 0 4; -fx-border-radius: 0 8 8 0;");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(testName);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label statusLabel = new Label("Status: " + status.toUpperCase());
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        // This label will be updated every second by the Timeline
        Label countdownLabel = new Label("Calculating...");
        countdownLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        info.getChildren().addAll(nameLabel, statusLabel, countdownLabel);

        // ── Build the countdown timer ─────────────────────────────────────────
        if (expectedAt != null) {
            LocalDateTime expectedTime = expectedAt.toLocalDateTime();

            // Timeline = a repeating animation/task in JavaFX
            // KeyFrame = what to do on each tick (every 1 second here)
            Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                LocalDateTime now = LocalDateTime.now();
                long secondsLeft = ChronoUnit.SECONDS.between(now, expectedTime);

                if (secondsLeft <= 0) {
                    countdownLabel.setText("⏰ Result Due Now");
                    countdownLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
                } else {
                    long hours   = secondsLeft / 3600;
                    long minutes = (secondsLeft % 3600) / 60;
                    long seconds = secondsLeft % 60;
                    countdownLabel.setText(String.format("⏱ Ready in: %02dh %02dm %02ds",
                        hours, minutes, seconds));
                }
            }));

            // INDEFINITE = keep running until we stop it manually
            countdown.setCycleCount(Timeline.INDEFINITE);
            countdown.play();

            // Register it so stopAllTimers() can clean it up
            activeTimers.add(countdown);
        } else {
            countdownLabel.setText("⏱ Turnaround time not set");
        }

        Label pendingBadge = new Label("⏳ Pending");
        pendingBadge.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold; -fx-font-size: 12px;");

        card.getChildren().addAll(info, pendingBadge);
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
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf")
            );

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            File destFile = fileChooser.showSaveDialog(stage);

            if (destFile != null) {
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Report downloaded successfully!", ButtonType.OK);
                alert.setHeaderText(null);
                alert.showAndWait();
                AuditLogger.log(Session.getUserId(), "PDF_DOWNLOAD: " + testName, "results", Session.getUserId());
            }
        } catch (Exception e) {
            showError("Failed to download file: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleLogout() {
        try {
            stopAllTimers();
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

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS — DB counts + force password change
    // ══════════════════════════════════════════════════════════════════════════

    private int getOrderCount() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM test_requests WHERE customer_id = ?");
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
                "WHERE tr.customer_id = ? AND r.is_validated = TRUE");
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { return 0; }
        return 0;
    }

    private int getPendingCount() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM test_requests WHERE customer_id = ? AND status = 'pending'");
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { return 0; }
        return 0;
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    private void checkPasswordResetRequired() {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT is_first_login FROM users WHERE id = ?"
            );
            stmt.setInt(1, Session.getUserId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getBoolean("is_first_login")) {
                showForcePasswordChangeModal();
            }
        } catch (Exception e) {
            showError("Security check failed: " + e.getMessage());
        }
    }

    private void showForcePasswordChangeModal() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Security Update Required");
        dialog.setHeaderText("First-Time Login — Please set a new password.");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(welcomeLabel.getScene().getWindow());

        PasswordField newPasswordField     = new PasswordField();
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
            String pass    = newPasswordField.getText().trim();
            String confirm = confirmPasswordField.getText().trim();

            if (pass.isEmpty() || !pass.equals(confirm)) {
                showError("Passwords do not match or are empty!");
                event.consume();
            } else if (pass.length() < 6) {
                showError("Password must be at least 6 characters!");
                event.consume();
            } else {
                try {
                    Connection conn = DBConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE users SET password = ?, is_first_login = FALSE WHERE id = ?"
                    );
                    stmt.setString(1, org.mindrot.jbcrypt.BCrypt.hashpw(pass, org.mindrot.jbcrypt.BCrypt.gensalt()));
                    stmt.setInt(2, Session.getUserId());
                    stmt.executeUpdate();
                    AuditLogger.log(Session.getUserId(), "PASSWORD_FIRST_RESET_SUCCESS", "users", Session.getUserId());
                    Alert ok = new Alert(Alert.AlertType.INFORMATION, "Password updated successfully!", ButtonType.OK);
                    ok.setHeaderText(null);
                    ok.showAndWait();
                } catch (Exception e) {
                    showError("Failed to update password: " + e.getMessage());
                    event.consume();
                }
            }
        });

        dialog.showAndWait().ifPresent(response -> {
            if (response != submitBtnType) handleLogout();
        });
    }
}
