# 🏥 Sante Diagnostics LIMS
**Laboratory Information Management System**  
Built with JavaFX, PostgreSQL, and Jakarta Mail.

---

## 📋 Table of Contents
1. [Project Overview](#project-overview)
2. [Prerequisites](#prerequisites)
3. [Database Setup](#database-setup)
4. [Project Configuration](#project-configuration)
5. [Adding Required Libraries](#adding-required-libraries)
6. [Running the Project](#running-the-project)
7. [Default Login Credentials](#default-login-credentials)
8. [Email Setup (Optional)](#email-setup-optional)
9. [Project Structure](#project-structure)
10. [User Roles & Features](#user-roles--features)

---

## Project Overview
Sante Diagnostics LIMS digitalizes laboratory operations for Sante Diagnostics Ltd. It replaces a manual paper-based process with a full desktop application supporting three user roles: Super Admin, Lab Attendant, and Customer.

---

## Prerequisites
Make sure you have all of these installed before proceeding:

| Tool | Version | Download |
|---|---|---|
| Java JDK | 17 or higher | https://adoptium.net |
| JavaFX SDK | 17 or higher | https://gluonhq.com/products/javafx |
| NetBeans IDE | 17 or higher | https://netbeans.apache.org |
| PostgreSQL | 14 or higher | https://www.postgresql.org/download |
| pgAdmin 4 | Any | Included with PostgreSQL installer |

---

## Database Setup

### Step 1 — Create the Database
Open **pgAdmin**, connect to your local PostgreSQL server, then open the Query Tool and run:

```sql
CREATE DATABASE sante_diagnostics;
```

### Step 2 — Create the Tables
Connect to the `sante_diagnostics` database and run the following SQL:

```sql
-- Users table (all roles: super_admin, lab_attendant, customer)
CREATE TABLE users (
    id                SERIAL PRIMARY KEY,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255) UNIQUE NOT NULL,
    password          VARCHAR(255) NOT NULL,
    role              VARCHAR(50)  NOT NULL CHECK (role IN ('super_admin', 'lab_attendant', 'customer')),
    is_first_login    BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    created_by        INTEGER REFERENCES users(id),
    created_at        TIMESTAMP DEFAULT NOW()
);

-- Test types (defined by Super Admin)
CREATE TABLE test_types (
    id                SERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    category          VARCHAR(100) NOT NULL,
    price             NUMERIC(10, 2) NOT NULL,
    turnaround_hours  INTEGER NOT NULL,
    result_format     VARCHAR(50) CHECK (result_format IN ('numeric', 'text', 'pdf', 'image')),
    created_at        TIMESTAMP DEFAULT NOW()
);

-- Test requests (placed by customers)
CREATE TABLE test_requests (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES users(id),
    test_type_id    INTEGER NOT NULL REFERENCES test_types(id),
    status          VARCHAR(50) DEFAULT 'pending' CHECK (status IN ('pending', 'collected', 'processing', 'validated')),
    payment_status  VARCHAR(50) DEFAULT 'unpaid' CHECK (payment_status IN ('unpaid', 'paid')),
    created_at      TIMESTAMP DEFAULT NOW(),
    expected_at     TIMESTAMP
);

-- Results (uploaded and validated by Lab Attendants)
CREATE TABLE results (
    id               SERIAL PRIMARY KEY,
    test_request_id  INTEGER NOT NULL REFERENCES test_requests(id),
    result_value     TEXT,
    file_path        TEXT,
    is_validated     BOOLEAN DEFAULT FALSE,
    uploaded_at      TIMESTAMP DEFAULT NOW(),
    validated_at     TIMESTAMP
);

-- Audit log (immutable — no updates or deletes allowed)
CREATE TABLE audit_log (
    id                 SERIAL PRIMARY KEY,
    user_id            INTEGER REFERENCES users(id),
    action             TEXT NOT NULL,
    affected_table     VARCHAR(100),
    affected_record_id INTEGER,
    timestamp          TIMESTAMP DEFAULT NOW()
);
```

### Step 3 — Insert the Default Super Admin
The system needs at least one Super Admin to get started. Run this to generate a hashed password and insert the admin:

> **Note:** The hashed password below corresponds to `Admin2026`. You will generate a fresh one in Step 6 below and update this record.

```sql
INSERT INTO users (first_name, last_name, email, password, role, is_first_login, is_email_verified)
VALUES ('Super', 'Admin', 'testadmin@gmail.com', 'PASTE_HASH_HERE', 'super_admin', FALSE, TRUE);
```

You will come back and update `PASTE_HASH_HERE` after Step 6.

---

## Project Configuration

### Step 4 — Clone / Open the Project
Open **NetBeans** → File → Open Project → navigate to the `SanteDiagnostics` folder.

### Step 5 — Update config.properties
Open `src/config.properties ` and update it with your local PostgreSQL credentials or create one:

```properties
db.url=jdbc:postgresql://localhost:5432/sante_diagnostics
db.user=postgres
db.password=YOUR_POSTGRES_PASSWORD
mail.sender=youremail@gmail.com
mail.password=YOUR_GMAIL_APP_PASSWORD
mail.host=smtp.gmail.com
mail.port=587
```

> **Important:** Replace `YOUR_POSTGRES_PASSWORD` with your actual PostgreSQL password set during installation.

---

## Adding Required Libraries
The project requires 3 external JAR files. Download them and add them to NetBeans.

### Required JARs

| Library | Purpose | Download |
|---|---|---|
| `jbcrypt-0.4.jar` | BCrypt password hashing | https://mvnrepository.com/artifact/org.mindrot/jbcrypt |
| `postgresql-42.x.x.jar` | PostgreSQL JDBC driver | https://jdbc.postgresql.org/download |
| `jakarta.mail-2.x.x.jar` | Email sending via SMTP | https://mvnrepository.com/artifact/com.sun.mail/jakarta.mail |

### How to Add JARs in NetBeans
1. Right-click the project → **Properties**
2. Go to **Libraries** → **Compile** tab
3. Click **Add JAR/Folder**
4. Select each downloaded JAR file
5. Click **OK**

### Adding JavaFX
1. In NetBeans, go to **Tools** → **Libraries** → **New Library**
2. Name it `javaFX26` (or match the name in your `project.properties`)
3. Add all JARs from your JavaFX SDK `lib` folder
4. Right-click project → **Properties** → **Libraries** → add the JavaFX library

---

## Running the Project

### Step 6 — Generate the Admin Password Hash
Temporarily add this line to `main()` in `SanteDiagnosticsLtd.java`:

```java
public static void main(String[] args) {
    System.out.println(org.mindrot.jbcrypt.BCrypt.hashpw("Admin2026", org.mindrot.jbcrypt.BCrypt.gensalt()));
    launch(args);
}
```

Run the project — copy the hash printed in the console output, then run this in pgAdmin:

```sql
UPDATE users SET password = 'PASTE_HASH_HERE' WHERE email = 'testadmin@gmail.com';
```

Then **remove that print line** from `main()`.

### Step 7 — Clean and Build
In NetBeans: **Run** → **Clean and Build Project** (or press `Shift + F11`)

### Step 8 — Run
Press **F6** or click the green **Run** button.

The login screen should appear. Use the default admin credentials below.

---

## Default Login Credentials

| Role | Email | Password |
|---|---|---|
| Super Admin | `example@gmail.com` | `password` |
| Lab Attendant | Created by Super Admin | Temp password (check console or email) |
| Customer | Self-register via Signup | Password set during registration |

> **First login for staff accounts:** Lab Attendants and Customers created by staff are forced to change their password on first login.

---

## Email Setup (Optional)

Email notifications are sent when:
- A new staff/customer account is created (welcome email with temp password)
- A lab result is validated (notification to patient)

To enable real email sending:

1. Enable **2-Step Verification** on your Gmail account at [myaccount.google.com](https://myaccount.google.com)
2. Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
3. Generate an App Password for **Mail → Windows Computer**
4. Copy the 16-character password and paste it into `config.properties`:

```properties
mail.sender=youremail@gmail.com
mail.password=abcdefghijklmnop
```

> **Without email configured:** The app will not crash. It falls back gracefully and prints the email content to the NetBeans console output instead.

---

## Project Structure

```
SanteDiagnostics/
├── src/
│   ├── config.properties              ← DB and email credentials
│   ├── ChangePasswordController.java  ← Force password change on first login
│   └── santediagnosticsltd/
│       ├── SanteDiagnosticsLtd.java   ← App entry point
│       ├── DBConnection.java          ← PostgreSQL connection manager
│       ├── EmailService.java          ← Jakarta Mail email sender
│       ├── AuditLogger.java           ← Immutable audit trail logger
│       ├── Session.java               ← Logged-in user session state
│       ├── controllers/
│       │   ├── LoginController.java
│       │   ├── SignupController.java
│       │   ├── SuperAdminDashboardController.java
│       │   ├── LabAttendantDashboardController.java
│       │   └── CustomerDashboardController.java
│       ├── views/
│       │   ├── login.fxml
│       │   ├── signup.fxml
│       │   ├── change-password.fxml
│       │   ├── super-admin-dashboard.fxml
│       │   ├── lab-attendant-dashboard.fxml
│       │   └── customer-dashboard.fxml
│       └── css/
│           └── styles.css
└── results_storage/                   ← Auto-created folder for uploaded result files
```

---

## User Roles & Features

### 🔴 Super Admin
- Create Lab Attendant and Customer accounts
- Define test types (name, category, price, turnaround time, result format)
- View all test requests and mark payments as paid
- View immutable audit trail of all system actions

### 🟡 Lab Attendant
- View all test requests and mark payments as paid
- Update sample lifecycle status (pending → collected → processing → validated)
- Upload result files (PDF or image) for paid requests
- Validate results to release them to patients (triggers email notification)
- Create Customer accounts

### 🟢 Customer
- Self-register or receive a staff-created account
- Browse available tests and view prices
- Place test orders and receive bank transfer payment instructions
- View live countdown timer showing time remaining until results are ready
- Download validated PDF lab reports
- Receive email notification when results are ready

---



