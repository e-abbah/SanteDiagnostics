package santediagnosticsltd.controllers;

// ─── JavaFX UI Imports ───────────────────────────────────────────────────────
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
import javafx.stage.Stage;

// ─── App Imports ─────────────────────────────────────────────────────────────
import santediagnosticsltd.AuditLogger;
import santediagnosticsltd.DBConnection;
import santediagnosticsltd.EmailService;
import santediagnosticsltd.Session;

// ─── Java Standard Imports ───────────────────────────────────────────────────
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * SuperAdminDashboardController
 *
 * PATTERN USED: "Dynamic Content Pane"
 * Instead of creating a separate FXML file for each screen, we have ONE
 * contentPane (a blank Pane in the FXML). Each sidebar button calls a method
 * that CLEARS the pane and REBUILDS it with new content. This is the same
 * pattern Person 1 used in CustomerDashboardController.
 *
 * SCREENS IN THIS CONTROLLER:
 *   1. showHome()         → Overview with summary cards
 *   2. showTestBuilder()  → Create / view test types
 *   3. showTestRequests() → All patient test requests + mark as paid
 *   4. showCreateAccount()→ Create Lab Attendant or Customer accounts
 *   5. showAuditTrail()   → Read-only log of all system actions
 */
public class SuperAdminDashboardController implements Initializable {

    // ─── FXML-bound fields ────────────────────────────────────────────────────
    // @FXML means JavaFX will automatically connect this variable to the
    // element with the matching fx:id in the FXML file.
    @FXML private Label welcomeLabel;
    @FXML private Pane contentPane;

    // ─── INITIALIZE ───────────────────────────────────────────────────────────
    /**
     * initialize() is called automatically by JavaFX right after the FXML is
     * loaded. Think of it as the constructor for your controller.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Greet the logged-in admin using the Session (set during login)
        welcomeLabel.setText("Welcome, " + Session.getUserName());

        // Show the home/overview screen by default when the dashboard opens
        showHome();

        // Platform.runLater() defers code until after JavaFX finishes
        // rendering the layout — needed for any DB calls at startup
        Platform.runLater(this::checkAdminAccess);
    }

    /**
     * Security guard: if somehow a non-admin reaches this dashboard, log them out.
     * This is a defensive check — the login controller already handles routing,
     * but double-checking here is good practice.
     */
    private void checkAdminAccess() {
        if (!"super_admin".equals(Session.getUserRole())) {
            showAlert(Alert.AlertType.ERROR, "Access Denied", "You do not have admin privileges.");
            handleLogout();
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 1 — HOME / OVERVIEW
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showHome() {
        contentPane.getChildren().clear();

        // VBox = vertical layout container (items stacked top to bottom)
        VBox home = new VBox(20);
        home.setPadding(new Insets(30));
        // Bind width/height to the parent pane so it resizes with the window
        home.prefWidthProperty().bind(contentPane.widthProperty());
        home.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("Admin Overview");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("System-wide summary for Sante Diagnostics Ltd.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        // HBox = horizontal layout container (items side by side)
        HBox cards = new HBox(15);
        cards.getChildren().addAll(
            buildSummaryCard("👤 Total Users",    countFrom("SELECT COUNT(*) FROM users"),                    "#3b5bdb"),
            buildSummaryCard("🧪 Test Types",     countFrom("SELECT COUNT(*) FROM test_types"),               "#10b981"),
            buildSummaryCard("📋 Total Requests", countFrom("SELECT COUNT(*) FROM test_requests"),            "#f59e0b"),
            buildSummaryCard("💰 Unpaid",         countFrom("SELECT COUNT(*) FROM test_requests WHERE payment_status='unpaid'"), "#ef4444")
        );

        home.getChildren().addAll(title, subtitle, cards);
        contentPane.getChildren().add(home);
    }

    /**
     * Reusable helper that builds a stat card (white box with a title and value).
     * Parameters make it flexible — call it once per card with different data.
     */
    private VBox buildSummaryCard(String title, String value, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setPrefWidth(190);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    /**
     * Generic DB count helper.
     * Runs any "SELECT COUNT(*) FROM ..." query and returns the result as a String.
     * Returns "0" on error so the UI doesn't crash.
     */
    private String countFrom(String sql) {
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(sql).executeQuery();
            if (rs.next()) return String.valueOf(rs.getInt(1));
        } catch (Exception e) {
            System.err.println("[ADMIN] Count query failed: " + e.getMessage());
        }
        return "0";
    }


    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 2 — CUSTOM TEST BUILDER
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showTestBuilder() {
        contentPane.getChildren().clear();

        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("🧪 Custom Test Builder");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        // ── FORM SECTION ──────────────────────────────────────────────────────
        // We build the form in code (not FXML) because it's dynamically loaded
        // into the contentPane at runtime.

        VBox form = new VBox(12);
        form.setPadding(new Insets(20));
        form.setMaxWidth(500);
        form.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        Label formTitle = new Label("Add New Test Type");
        formTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        // Form fields
        TextField nameField     = createField("Test Name (e.g. Full Blood Count)");
        TextField categoryField = createField("Category (e.g. Blood, Imaging, Biopsy)");
        TextField priceField    = createField("Price (₦)");
        TextField tatField      = createField("Turnaround Time (hours)");

        // ComboBox = dropdown selector
        // Result format tells the system what kind of result to expect
        Label formatLabel = new Label("Result Format");
        formatLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #374151;");
        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("numeric", "text", "pdf", "image");
        formatBox.setValue("numeric");   // default selection
        formatBox.setMaxWidth(Double.MAX_VALUE);
        formatBox.setStyle("-fx-font-size: 13px;");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 12px;");

        Button saveBtn = new Button("Save Test Type");
        saveBtn.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white; -fx-font-weight: bold;" +
                         "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20 10 20;");

        // When the Save button is clicked, call handleSaveTestType()
        saveBtn.setOnAction(e -> handleSaveTestType(
            nameField, categoryField, priceField, tatField, formatBox, statusLabel
        ));

        form.getChildren().addAll(
            formTitle,
            createLabel("Test Name"), nameField,
            createLabel("Category"),  categoryField,
            createLabel("Price (₦)"), priceField,
            createLabel("TAT (hours)"), tatField,
            formatLabel, formatBox,
            statusLabel,
            saveBtn
        );

        // ── EXISTING TESTS TABLE ──────────────────────────────────────────────
        Label tableTitle = new Label("Existing Test Types");
        tableTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e; -fx-padding: 10 0 0 0;");

        TableView<ObservableList<String>> table = buildTestTypesTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        container.getChildren().addAll(title, form, tableTitle, table);
        contentPane.getChildren().add(container);
    }

    /**
     * Handles the "Save Test Type" button click.
     * Reads from the form fields, validates, then inserts into the DB.
     *
     * WHY PASS FIELDS AS PARAMETERS? Because this method is called from a
     * lambda (the button's onAction), and local variables inside lambdas
     * must be effectively final. Passing them as parameters is the clean fix.
     */
    private void handleSaveTestType(TextField nameField, TextField categoryField,
                                     TextField priceField, TextField tatField,
                                     ComboBox<String> formatBox, Label statusLabel) {
        // Read and trim whitespace from every field
        String name     = nameField.getText().trim();
        String category = categoryField.getText().trim();
        String priceStr = priceField.getText().trim();
        String tatStr   = tatField.getText().trim();
        String format   = formatBox.getValue();

        // ── Validation ────────────────────────────────────────────────────────
        if (name.isEmpty() || category.isEmpty() || priceStr.isEmpty() || tatStr.isEmpty()) {
            statusLabel.setText("❌ All fields are required.");
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
            return;
        }

        double price;
        int tat;
        try {
            // parseDouble / parseInt throw NumberFormatException if input is not a number
            price = Double.parseDouble(priceStr);
            tat   = Integer.parseInt(tatStr);
            if (price <= 0 || tat <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            statusLabel.setText("❌ Price and TAT must be positive numbers.");
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
            return;
        }

        // ── Database Insert ───────────────────────────────────────────────────
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO test_types (name, category, price, turnaround_hours, result_format) " +
                "VALUES (?, ?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS  // needed to get the new record's ID
            );
            stmt.setString(1, name);
            stmt.setString(2, category);
            stmt.setDouble(3, price);
            stmt.setInt(4, tat);
            stmt.setString(5, format);
            stmt.executeUpdate();

            // Get the ID of the newly inserted row for audit logging
            ResultSet keys = stmt.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : 0;

            // Write to audit trail — immutable record of who did what
            AuditLogger.log(Session.getUserId(), "TEST_TYPE_CREATED: " + name, "test_types", newId);

            // Show success and clear the form
            statusLabel.setText("✅ Test type '" + name + "' saved successfully!");
            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px;");
            nameField.clear(); categoryField.clear(); priceField.clear(); tatField.clear();
            formatBox.setValue("numeric");

            // Refresh the screen to show the new test in the table
            showTestBuilder();

        } catch (Exception ex) {
            statusLabel.setText("❌ Database error: " + ex.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
        }
    }

    /**
     * Builds and returns a TableView loaded with all existing test types from DB.
     * TableView = a spreadsheet-like table component in JavaFX.
     */
    private TableView<ObservableList<String>> buildTestTypesTable() {
        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setMaxHeight(250);

        // Each TableColumn needs a cellValueFactory — this tells the column
        // how to get its value from a row. Each row is an ObservableList<String>,
        // so .get(0) = first column, .get(1) = second column, etc.
        String[] headers = {"ID", "Name", "Category", "Price (₦)", "TAT (hrs)", "Format"};
        for (int i = 0; i < headers.length; i++) {
            final int index = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(index))
            );
            table.getColumns().add(col);
        }

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = conn.prepareStatement(
                "SELECT id, name, category, price, turnaround_hours, result_format FROM test_types ORDER BY category"
            ).executeQuery();

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("category"));
                row.add("₦" + String.format("%,.2f", rs.getDouble("price")));
                row.add(String.valueOf(rs.getInt("turnaround_hours")));
                row.add(rs.getString("result_format"));
                rows.add(row);
            }
        } catch (Exception e) {
            System.err.println("[ADMIN] Failed to load test types: " + e.getMessage());
        }

        table.setItems(rows);
        return table;
    }


    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 3 — TEST REQUEST QUEUE
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

        Label subtitle = new Label("View all patient test requests. Mark as Paid after confirming bank transfer.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        // ── Table setup ───────────────────────────────────────────────────────
        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "Patient", "Test", "Status", "Payment", "Date"};
        for (int i = 0; i < headers.length; i++) {
            final int index = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(index))
            );
            table.getColumns().add(col);
        }

        // ── "Mark as Paid" action column ─────────────────────────────────────
        // This column holds a Button, not just text, so it needs a custom cell.
        TableColumn<ObservableList<String>, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(col -> new TableCell<>() {
            // Each cell gets its own button instance
            private final Button btn = new Button("Mark as Paid");
            {
                btn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;" +
                             "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
                btn.setOnAction(e -> {
                    // getIndex() gives the row number; getTableView().getItems() gives all rows
                    ObservableList<String> row = getTableView().getItems().get(getIndex());
                    int requestId = Integer.parseInt(row.get(0));
                    String paymentStatus = row.get(4);
                    handleMarkAsPaid(requestId, paymentStatus);
                });
            }

            // updateItem() is called by JavaFX whenever the cell needs to render
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                // empty = true means this cell is in an empty row — hide the button
                setGraphic(empty ? null : btn);
            }
        });
        table.getColumns().add(actionCol);

        // ── Load data from DB ─────────────────────────────────────────────────
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DBConnection.getConnection();
            // JOIN combines data from 3 tables: test_requests + users + test_types
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT tr.id, u.first_name || ' ' || u.last_name AS patient, " +
                "tt.name AS test_name, tr.status, tr.payment_status, tr.created_at " +
                "FROM test_requests tr " +
                "JOIN users u ON tr.customer_id = u.id " +
                "JOIN test_types tt ON tr.test_type_id = tt.id " +
                "ORDER BY tr.created_at DESC"
            );
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("patient"));
                row.add(rs.getString("test_name"));
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

    /**
     * Called when admin clicks "Mark as Paid" on a request row.
     * Updates the payment_status column in the DB and logs the action.
     */
    private void handleMarkAsPaid(int requestId, String currentStatus) {
        if ("PAID".equals(currentStatus)) {
            showAlert(Alert.AlertType.INFORMATION, "Already Paid", "This request is already marked as paid.");
            return;
        }

        // Confirm before making changes
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Mark request #" + requestId + " as PAID?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    Connection conn = DBConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE test_requests SET payment_status = 'paid' WHERE id = ?"
                    );
                    stmt.setInt(1, requestId);
                    stmt.executeUpdate();

                    AuditLogger.log(Session.getUserId(), "PAYMENT_MARKED_PAID: Request #" + requestId, "test_requests", requestId);
                    showAlert(Alert.AlertType.INFORMATION, "Updated", "Request #" + requestId + " marked as paid.");
                    showTestRequests(); // Refresh the table
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Update failed: " + e.getMessage());
                }
            }
        });
    }


    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 4 — CREATE ACCOUNT (Lab Attendant or Customer)
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showCreateAccount() {
        contentPane.getChildren().clear();

        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("👤 Create Staff / Customer Account");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        // ── Form ──────────────────────────────────────────────────────────────
        VBox form = new VBox(12);
        form.setPadding(new Insets(20));
        form.setMaxWidth(480);
        form.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        TextField firstNameField = createField("First Name");
        TextField lastNameField  = createField("Last Name");
        TextField emailField     = createField("Email Address");

        // Role selector — Super Admin can create either role
        Label roleLabel = new Label("Role");
        roleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #374151;");
        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.getItems().addAll("Lab Attendant", "Customer");
        roleBox.setValue("Lab Attendant");
        roleBox.setMaxWidth(Double.MAX_VALUE);

        Label statusLabel = new Label("");

        Button createBtn = new Button("Create Account");
        createBtn.setStyle("-fx-background-color: #3b5bdb; -fx-text-fill: white; -fx-font-weight: bold;" +
                           "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20 10 20;");
        createBtn.setOnAction(e -> handleCreateAccount(firstNameField, lastNameField, emailField, roleBox, statusLabel));

        form.getChildren().addAll(
            new Label("New Account Details") {{
                setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
            }},
            createLabel("First Name"), firstNameField,
            createLabel("Last Name"),  lastNameField,
            createLabel("Email"),      emailField,
            roleLabel, roleBox,
            statusLabel,
            createBtn
        );

        container.getChildren().addAll(title, form);
        contentPane.getChildren().add(container);
    }

    /**
     * Creates a new staff/customer account.
     *
     * KEY CONCEPTS:
     * - UUID.randomUUID() generates a unique temporary password automatically
     * - BCrypt hashes the password before storing — never store plain text!
     * - must_change_password = TRUE forces the new user to reset on first login
     * - is_first_login = TRUE triggers the change-password screen in LoginController
     * - We email the temporary password so the new user can log in
     */
    private void handleCreateAccount(TextField firstNameField, TextField lastNameField,
                                      TextField emailField, ComboBox<String> roleBox,
                                      Label statusLabel) {
        String firstName = firstNameField.getText().trim();
        String lastName  = lastNameField.getText().trim();
        String email     = emailField.getText().trim();
        String selectedRole = roleBox.getValue();

        // Map display name to DB role value
        String dbRole = selectedRole.equals("Lab Attendant") ? "lab_attendant" : "customer";

        // ── Validation ────────────────────────────────────────────────────────
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

        // ── Generate temp password ────────────────────────────────────────────
        // UUID = Universally Unique Identifier — random 36-character string
        // We take the first 8 characters as the temporary password
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);
        String hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(
            tempPassword,
            org.mindrot.jbcrypt.BCrypt.gensalt()
        );

        // ── DB Insert ─────────────────────────────────────────────────────────
        try {
            Connection conn = DBConnection.getConnection();

            // Check if email already exists
            PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE email = ?");
            checkStmt.setString(1, email);
            if (checkStmt.executeQuery().next()) {
                statusLabel.setText("❌ An account with this email already exists.");
                statusLabel.setStyle("-fx-text-fill: #ef4444;");
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (first_name, last_name, email, password, role, is_first_login, is_email_verified) " +
                "VALUES (?, ?, ?, ?, ?, TRUE, TRUE)",
                PreparedStatement.RETURN_GENERATED_KEYS
            );
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setString(3, email);
            stmt.setString(4, hashedPassword);
            stmt.setString(5, dbRole);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            int newUserId = keys.next() ? keys.getInt(1) : 0;

            AuditLogger.log(Session.getUserId(),
                "ACCOUNT_CREATED: " + dbRole + " - " + email,
                "users", newUserId);

            // ── Send welcome email with temp password ─────────────────────────
            // Run email sending on a background thread so it doesn't freeze the UI
            String finalEmail = email;
            String finalFirstName = firstName;
            new Thread(() -> {
                EmailService.sendWelcomeEmail(finalEmail, finalFirstName, tempPassword);
            }).start();

            statusLabel.setText("✅ Account created! Temporary password sent to " + email);
            statusLabel.setStyle("-fx-text-fill: #10b981;");
            firstNameField.clear(); lastNameField.clear(); emailField.clear();

        } catch (Exception ex) {
            statusLabel.setText("❌ Error: " + ex.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // SCREEN 5 — AUDIT TRAIL
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void showAuditTrail() {
        contentPane.getChildren().clear();

        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.prefWidthProperty().bind(contentPane.widthProperty());
        container.prefHeightProperty().bind(contentPane.heightProperty());

        Label title = new Label("🔍 Audit Trail");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Immutable log of all system actions. Records cannot be edited or deleted.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        // ── Table ─────────────────────────────────────────────────────────────
        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // Make the table uneditable — read-only requirement from the spec
        table.setEditable(false);

        String[] headers = {"Log ID", "User", "Action", "Table Affected", "Record ID", "Timestamp"};
        for (int i = 0; i < headers.length; i++) {
            final int index = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().get(index))
            );
            table.getColumns().add(col);
        }

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try {
            Connection conn = DBConnection.getConnection();
            // LEFT JOIN — includes audit entries even if the user was deleted
            PreparedStatement stmt = conn.prepareStatement(
               // TO:
                    "SELECT al.id, COALESCE(u.first_name || ' ' || u.last_name, 'Deleted User') AS user_name, " +"al.action, al.affected_table, al.affected_record_id, al.timestamp " +
                    "FROM audit_log al " +
                    "LEFT JOIN users u ON al.user_id = u.id " +
                    "ORDER BY al.timestamp DESC"
            );
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("user_name"));
                row.add(rs.getString("action"));
                row.add(rs.getString("affected_table"));
                row.add(String.valueOf(rs.getInt("affected_record_id")));
                row.add(rs.getTimestamp("timestamp").toString().substring(0, 19));
                rows.add(row);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load audit trail: " + e.getMessage());
        }

        table.setItems(rows);
        VBox.setVgrow(table, Priority.ALWAYS);

        container.getChildren().addAll(title, subtitle, table);
        contentPane.getChildren().add(container);
    }


    // ══════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleLogout() {
        try {
            AuditLogger.log(Session.getUserId(), "USER_LOGOUT", "users", Session.getUserId());
            Session.logout();
            Parent root = FXMLLoader.load(getClass().getResource("/santediagnosticsltd/views/login.fxml"));
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
    // These small methods are reused across multiple screens above.
    // ══════════════════════════════════════════════════════════════════════════

    /** Creates a styled text input field */
    private TextField createField(String placeholder) {
        TextField tf = new TextField();
        tf.setPromptText(placeholder);
        tf.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db;" +
                    "-fx-border-radius: 6; -fx-background-radius: 6;" +
                    "-fx-padding: 8 12 8 12; -fx-font-size: 13px;");
        return tf;
    }

    /** Creates a styled form field label */
    private Label createLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #374151;");
        return lbl;
    }

    /** Shows a standard JavaFX alert dialog */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
