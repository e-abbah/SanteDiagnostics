/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package santediagnosticsltd.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.stage.Stage;
import santediagnosticsltd.AuditLogger;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.EmailService;
import santediagnosticsltd.Session;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import java.util.UUID;

public class LabAttendantDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Pane  contentPane;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + Session.getUserName());
        showHome();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 1 — HOME
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showHome() {
        contentPane.getChildren().clear();

        VBox home = new VBox(20);
        home.setPadding(new Insets(30));
        home.prefWidthProperty().bind(contentPane.widthProperty());
        home.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("Lab Attendant Overview");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Daily operations summary for " + Session.getUserName() + ".");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        HBox cards = new HBox(15);
        cards.getChildren().addAll(
            buildSummaryCard("📋 Total Requests",
                countFrom("SELECT COUNT(*) FROM test_requests"), "#3b5bdb"),
            buildSummaryCard("💰 Unpaid",
                countFrom("SELECT COUNT(*) FROM test_requests WHERE payment_status = 'unpaid'"), "#ef4444"),
            buildSummaryCard("🧫 In Processing",
                countFrom("SELECT COUNT(*) FROM test_requests WHERE status = 'processing'"), "#f59e0b"),
            buildSummaryCard("✅ Validated",
                countFrom("SELECT COUNT(*) FROM results WHERE is_validated = TRUE"), "#10b981")
        );

        home.getChildren().addAll(title, subtitle, cards);
        contentPane.getChildren().add(home);
    }

    private VBox buildSummaryCard(String title, String value, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setPrefWidth(190);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        card.getChildren().addAll(t, v);
        return card;
    }

    private String countFrom(String sql) {
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(sql).executeQuery();
            if (rs.next()) return String.valueOf(rs.getInt(1));
        } catch (Exception e) {
            System.err.println("[LAB] Count query failed: " + e.getMessage());
        }
        return "0";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 2 — TEST REQUEST QUEUE
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showTestRequests() {
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("📋 Test Request Queue");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("All patient test requests. Mark as Paid after confirming payment.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "Patient", "Test", "Status", "Payment", "Date"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(idx)));
            table.getColumns().add(col);
        }

        TableColumn<ObservableList<String>, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Mark as Paid");
            {
                btn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;" +
                             "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
                btn.setOnAction(e -> {
                    ObservableList<String> row = getTableView().getItems().get(getIndex());
                    handleMarkAsPaid(Integer.parseInt(row.get(0)), row.get(4));
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        table.getColumns().add(actionCol);

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(
                "SELECT tr.id, u.first_name || ' ' || u.last_name AS patient, " +
                "tt.name, tr.status, tr.payment_status, tr.created_at " +
                "FROM test_requests tr " +
                "JOIN users u  ON tr.customer_id  = u.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "ORDER BY tr.created_at DESC"
            ).executeQuery();

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("patient"));
                row.add(rs.getString("name"));
                row.add(rs.getString("status").toUpperCase());
                row.add(rs.getString("payment_status").toUpperCase());
                row.add(rs.getTimestamp("created_at").toString().substring(0, 16));
                rows.add(row);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load requests: " + e.getMessage());
        }

        table.setItems(rows);
        VBox.setVgrow(table, Priority.ALWAYS);
        container.getChildren().addAll(title, subtitle, table);
        contentPane.getChildren().add(container);
    }

    private void handleMarkAsPaid(int requestId, String currentStatus) {
        if ("PAID".equals(currentStatus)) {
            showAlert(Alert.AlertType.INFORMATION, "Already Paid", "This request is already marked as paid.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Mark request #" + requestId + " as PAID?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                try {
                    Connection conn = DBConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE test_requests SET payment_status = 'paid' WHERE id = ?"
                    );
                    stmt.setInt(1, requestId);
                    stmt.executeUpdate();
                    AuditLogger.log(Session.getUserId(),
                        "PAYMENT_MARKED_PAID: Request #" + requestId, "test_requests", requestId);
                    showAlert(Alert.AlertType.INFORMATION, "Updated", "Request #" + requestId + " marked as paid.");
                    showTestRequests();
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Update failed: " + e.getMessage());
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 3 — SAMPLE LIFECYCLE TRACKING
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showSampleTracking() {
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("🧫 Sample Lifecycle Tracking");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Update the processing stage for each patient sample.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        HBox legend = new HBox(20);
        legend.setPadding(new Insets(10, 0, 10, 0));
        String[][] stages = {
            {"⏳ Pending",    "#9ca3af"},
            {"🧪 Collected",  "#3b5bdb"},
            {"⚙️ Processing", "#f59e0b"},
            {"✅ Validated",  "#10b981"}
        };
        for (String[] stage : stages) {
            Label badge = new Label(stage[0]);
            badge.setStyle("-fx-text-fill: " + stage[1] + "; -fx-font-size: 12px; -fx-font-weight: bold;" +
                           "-fx-background-color: white; -fx-background-radius: 4;" +
                           "-fx-padding: 4 10 4 10;" +
                           "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 4, 0, 0, 1);");
            legend.getChildren().add(badge);
        }

        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"Request ID", "Patient", "Test", "Current Status", "Payment", "Requested On"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(idx)));
            table.getColumns().add(col);
        }

        TableColumn<ObservableList<String>, Void> updateCol = new TableColumn<>("Update Status");
        updateCol.setMinWidth(160);
        updateCol.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();
            private final Button updateBtn = new Button("Update");
            {
                combo.getItems().addAll("pending", "collected", "processing", "validated");
                combo.setStyle("-fx-font-size: 12px;");
                updateBtn.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white;" +
                                   "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
                updateBtn.setOnAction(e -> {
                    ObservableList<String> row = getTableView().getItems().get(getIndex());
                    handleUpdateSampleStatus(Integer.parseInt(row.get(0)), combo.getValue());
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ObservableList<String> row = getTableView().getItems().get(getIndex());
                    combo.setValue(row.get(3).toLowerCase());
                    HBox cell = new HBox(6, combo, updateBtn);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(cell);
                }
            }
        });
        table.getColumns().add(updateCol);

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(
                "SELECT tr.id, u.first_name || ' ' || u.last_name AS patient, " +
                "tt.name, tr.status, tr.payment_status, tr.created_at " +
                "FROM test_requests tr " +
                "JOIN users u  ON tr.customer_id  = u.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "ORDER BY tr.created_at DESC"
            ).executeQuery();

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("patient"));
                row.add(rs.getString("name"));
                row.add(rs.getString("status").toUpperCase());
                row.add(rs.getString("payment_status").toUpperCase());
                row.add(rs.getTimestamp("created_at").toString().substring(0, 16));
                rows.add(row);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load samples: " + e.getMessage());
        }

        table.setItems(rows);
        VBox.setVgrow(table, Priority.ALWAYS);
        container.getChildren().addAll(title, subtitle, legend, table);
        contentPane.getChildren().add(container);
    }

    private void handleUpdateSampleStatus(int requestId, String newStatus) {
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE test_requests SET status = ? WHERE id = ?"
            );
            stmt.setString(1, newStatus);
            stmt.setInt(2, requestId);
            stmt.executeUpdate();
            AuditLogger.log(Session.getUserId(),
                "SAMPLE_STATUS_UPDATED: Request #" + requestId + " -> " + newStatus,
                "test_requests", requestId);
            showAlert(Alert.AlertType.INFORMATION, "Updated",
                "Request #" + requestId + " status updated to: " + newStatus.toUpperCase());
            showSampleTracking();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to update status: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 4 — RESULT UPLOAD & VALIDATION
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showResultUpload() {
        contentPane.getChildren().clear();

        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("📤 Upload & Validate Results");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        VBox uploadForm = new VBox(12);
        uploadForm.setPadding(new Insets(20));
        uploadForm.setMaxWidth(520);
        uploadForm.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        Label formTitle = new Label("Upload Result File");
        formTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label requestLabel = createLabel("Select Test Request (Paid only)");
        ComboBox<String> requestCombo = new ComboBox<>();
        requestCombo.setMaxWidth(Double.MAX_VALUE);
        requestCombo.setPromptText("Select a request...");
        requestCombo.setStyle("-fx-font-size: 13px;");

        java.util.Map<String, Integer> requestMap = new java.util.LinkedHashMap<>();

        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(
                "SELECT tr.id, u.first_name || ' ' || u.last_name AS patient, tt.name " +
                "FROM test_requests tr " +
                "JOIN users u  ON tr.customer_id  = u.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "WHERE tr.payment_status = 'paid' " +
                "AND tr.id NOT IN (SELECT test_request_id FROM results) " +
                "ORDER BY tr.created_at DESC"
            ).executeQuery();

            while (rs.next()) {
                String display = "#" + rs.getInt("id") + " — " +
                                 rs.getString("patient") + " (" + rs.getString("name") + ")";
                requestMap.put(display, rs.getInt("id"));
                requestCombo.getItems().add(display);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load requests: " + e.getMessage());
        }

        Label valueLabel = createLabel("Result Value (optional — for numeric/text results)");
        TextField resultValueField = createField("e.g. Haemoglobin: 13.5 g/dL");

        Label fileLabel = createLabel("Attach File (PDF or Image)");
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Label fileNameLabel = new Label("No file selected");
        fileNameLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 12px;");

        File[] fileHolder = new File[1];

        Button chooseFileBtn = new Button("📁 Choose File");
        chooseFileBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #374151;" +
                               "-fx-border-color: #d1d5db; -fx-border-radius: 6;" +
                               "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 16 8 16;");
        chooseFileBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Result File");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF & Images", "*.pdf", "*.png", "*.jpg", "*.jpeg")
            );
            File chosen = fc.showOpenDialog(welcomeLabel.getScene().getWindow());
            if (chosen != null) {
                fileHolder[0] = chosen;
                fileNameLabel.setText("📄 " + chosen.getName());
                fileNameLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 12px;");
            }
        });
        fileRow.getChildren().addAll(chooseFileBtn, fileNameLabel);

        Label uploadStatus = new Label("");

        Button uploadBtn = new Button("Upload Result");
        uploadBtn.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white; -fx-font-weight: bold;" +
                           "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20 10 20;");
        uploadBtn.setOnAction(e ->
            handleUploadResult(requestCombo, requestMap, resultValueField, fileHolder, uploadStatus)
        );

        uploadForm.getChildren().addAll(
            formTitle, requestLabel, requestCombo,
            valueLabel, resultValueField,
            fileLabel, fileRow,
            uploadStatus, uploadBtn
        );

        Label pendingTitle = new Label("Pending Validation");
        pendingTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e; -fx-padding: 10 0 0 0;");

        Label pendingSubtitle = new Label("Results below are uploaded but not yet visible to patients. Click Validate to release.");
        pendingSubtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        TableView<ObservableList<String>> pendingTable = buildPendingValidationTable();
        pendingTable.setMaxHeight(250);

        container.getChildren().addAll(title, uploadForm, pendingTitle, pendingSubtitle, pendingTable);
        contentPane.getChildren().add(container);
    }

    private void handleUploadResult(ComboBox<String> requestCombo,
                                     java.util.Map<String, Integer> requestMap,
                                     TextField resultValueField,
                                     File[] fileHolder,
                                     Label uploadStatus) {
        String selectedRequest = requestCombo.getValue();
        if (selectedRequest == null) {
            uploadStatus.setText("❌ Please select a test request.");
            uploadStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        int requestId = requestMap.get(selectedRequest);
        String resultValue = resultValueField.getText().trim();
        File file = fileHolder[0];

        if (resultValue.isEmpty() && file == null) {
            uploadStatus.setText("❌ Please enter a result value or attach a file.");
            uploadStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        String filePath = null;
        if (file != null) {
            try {
                File storageDir = new File("results_storage");
                if (!storageDir.exists()) storageDir.mkdirs();
                String uniqueName = UUID.randomUUID() + "_" + file.getName();
                File destination = new File(storageDir, uniqueName);
                Files.copy(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                filePath = destination.getAbsolutePath();
            } catch (Exception e) {
                uploadStatus.setText("❌ File copy failed: " + e.getMessage());
                uploadStatus.setStyle("-fx-text-fill: #ef4444;");
                return;
            }
        }

        try {
            Connection conn = DBConnection.getConnection();
            // NOTE: No uploaded_by column — removed to match actual DB schema
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO results (test_request_id, result_value, file_path, is_validated) " +
                "VALUES (?, ?, ?, FALSE)",
                PreparedStatement.RETURN_GENERATED_KEYS
            );
            stmt.setInt(1, requestId);
            stmt.setString(2, resultValue.isEmpty() ? null : resultValue);
            stmt.setString(3, filePath);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            int newResultId = keys.next() ? keys.getInt(1) : 0;

            AuditLogger.log(Session.getUserId(),
                "RESULT_UPLOADED: Request #" + requestId, "results", newResultId);

            uploadStatus.setText("✅ Result uploaded. It is now pending validation.");
            uploadStatus.setStyle("-fx-text-fill: #10b981;");
            showResultUpload();

        } catch (Exception e) {
            uploadStatus.setText("❌ DB error: " + e.getMessage());
            uploadStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    private TableView<ObservableList<String>> buildPendingValidationTable() {
        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"Result ID", "Request ID", "Patient", "Test", "Uploaded At", "Has File"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(idx)));
            table.getColumns().add(col);
        }

        TableColumn<ObservableList<String>, Void> validateCol = new TableColumn<>("Action");
        validateCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✅ Validate");
            {
                btn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;" +
                             "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
                btn.setOnAction(e -> {
                    ObservableList<String> row = getTableView().getItems().get(getIndex());
                    handleValidateResult(Integer.parseInt(row.get(0)), Integer.parseInt(row.get(1)));
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        table.getColumns().add(validateCol);

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(
                "SELECT r.id, r.test_request_id, " +
                "u.first_name || ' ' || u.last_name AS patient, " +
                "tt.name, r.uploaded_at, " +
                "CASE WHEN r.file_path IS NOT NULL THEN 'Yes' ELSE 'No' END AS has_file " +
                "FROM results r " +
                "JOIN test_requests tr ON r.test_request_id = tr.id " +
                "JOIN users u  ON tr.customer_id  = u.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "WHERE r.is_validated = FALSE " +
                "ORDER BY r.uploaded_at DESC"
            ).executeQuery();

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(String.valueOf(rs.getInt("test_request_id")));
                row.add(rs.getString("patient"));
                row.add(rs.getString("name"));
                row.add(rs.getTimestamp("uploaded_at").toString().substring(0, 16));
                row.add(rs.getString("has_file"));
                rows.add(row);
            }
        } catch (Exception e) {
            System.err.println("[LAB] Failed to load pending results: " + e.getMessage());
        }

        table.setItems(rows);
        return table;
    }

    private void handleValidateResult(int resultId, int requestId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Validate result #" + resultId + "? This will make it visible to the patient " +
            "and send them an email notification.", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Validation");
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    Connection conn = DBConnection.getConnection();

                    // Mark result as validated + set validated_at timestamp
                    PreparedStatement validateStmt = conn.prepareStatement(
                        "UPDATE results SET is_validated = TRUE, validated_at = NOW() WHERE id = ?"
                    );
                    validateStmt.setInt(1, resultId);
                    validateStmt.executeUpdate();

                    // Update test request status to validated
                    PreparedStatement statusStmt = conn.prepareStatement(
                        "UPDATE test_requests SET status = 'validated' WHERE id = ?"
                    );
                    statusStmt.setInt(1, requestId);
                    statusStmt.executeUpdate();

                    AuditLogger.log(Session.getUserId(),
                        "RESULT_VALIDATED: Result #" + resultId, "results", resultId);

                    // Fetch patient details for email
                    PreparedStatement patientStmt = conn.prepareStatement(
                        "SELECT u.email, u.first_name, tt.name AS test_name " +
                        "FROM test_requests tr " +
                        "JOIN users u  ON tr.customer_id  = u.id " +
                        "JOIN test_types tt ON tr.test_type_id = tt.id " +
                        "WHERE tr.id = ?"
                    );
                    patientStmt.setInt(1, requestId);
                    ResultSet rs = patientStmt.executeQuery();

                    if (rs.next()) {
                        String email     = rs.getString("email");
                        String firstName = rs.getString("first_name");
                        String testName  = rs.getString("test_name");
                        new Thread(() ->
                            EmailService.sendResultReadyEmail(email, firstName, testName)
                        ).start();
                    }

                    showAlert(Alert.AlertType.INFORMATION, "Validated",
                        "Result #" + resultId + " validated. Patient has been notified.");
                    showResultUpload();

                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Validation failed: " + e.getMessage());
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 5 — CREATE CUSTOMER ACCOUNT
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showCreateCustomer() {
        contentPane.getChildren().clear();

        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("👤 Create Customer Account");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        VBox form = new VBox(12);
        form.setPadding(new Insets(20));
        form.setMaxWidth(480);
        form.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        TextField firstNameField = createField("First Name");
        TextField lastNameField  = createField("Last Name");
        TextField emailField     = createField("Email Address");
        Label statusLabel        = new Label("");

        Button createBtn = new Button("Create Customer Account");
        createBtn.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white; -fx-font-weight: bold;" +
                           "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20 10 20;");
        createBtn.setOnAction(e ->
            handleCreateCustomer(firstNameField, lastNameField, emailField, statusLabel)
        );

        form.getChildren().addAll(
            new Label("New Customer Details") {{
                setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
            }},
            createLabel("First Name"), firstNameField,
            createLabel("Last Name"),  lastNameField,
            createLabel("Email"),      emailField,
            statusLabel, createBtn
        );

        container.getChildren().addAll(title, form);
        contentPane.getChildren().add(container);
    }

    private void handleCreateCustomer(TextField firstNameField, TextField lastNameField,
                                       TextField emailField, Label statusLabel) {
        String firstName = firstNameField.getText().trim();
        String lastName  = lastNameField.getText().trim();
        String email     = emailField.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            statusLabel.setText("❌ All fields are required.");
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            statusLabel.setText("❌ Please enter a valid email address.");
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        String tempPassword   = UUID.randomUUID().toString().substring(0, 8);
        String hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(
            tempPassword, org.mindrot.jbcrypt.BCrypt.gensalt()
        );

        try {
            Connection conn = DBConnection.getConnection();

            PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE email = ?");
            checkStmt.setString(1, email);
            if (checkStmt.executeQuery().next()) {
                statusLabel.setText("❌ An account with this email already exists.");
                statusLabel.setStyle("-fx-text-fill: #ef4444;");
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (first_name, last_name, email, password, role, is_first_login, is_email_verified) " +
                "VALUES (?, ?, ?, ?, 'customer', TRUE, TRUE)",
                PreparedStatement.RETURN_GENERATED_KEYS
            );
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setString(3, email);
            stmt.setString(4, hashedPassword);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            int newUserId = keys.next() ? keys.getInt(1) : 0;

            AuditLogger.log(Session.getUserId(),
                "CUSTOMER_ACCOUNT_CREATED: " + email, "users", newUserId);

            String finalEmail     = email;
            String finalFirstName = firstName;
            new Thread(() ->
                EmailService.sendWelcomeEmail(finalEmail, finalFirstName, tempPassword)
            ).start();

            statusLabel.setText("✅ Customer account created! Credentials sent to " + email);
            statusLabel.setStyle("-fx-text-fill: #10b981;");
            firstNameField.clear(); lastNameField.clear(); emailField.clear();

        } catch (Exception ex) {
            statusLabel.setText("❌ Error: " + ex.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleLogout() {
        try {
            AuditLogger.log(Session.getUserId(), "USER_LOGOUT", "users", Session.getUserId());
            Session.logout();
            Parent root = FXMLLoader.load(
                getClass().getResource("/santediagnosticsltd/views/login.fxml")
            );
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/santediagnosticsltd/css/styles.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Logout Error", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private TextField createField(String placeholder) {
        TextField tf = new TextField();
        tf.setPromptText(placeholder);
        tf.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db;" +
                    "-fx-border-radius: 6; -fx-background-radius: 6;" +
                    "-fx-padding: 8 12 8 12; -fx-font-size: 13px;");
        return tf;
    }

    private Label createLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #374151;");
        return lbl;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
