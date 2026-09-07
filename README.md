# 🇮🇳 PAL INDIA COMPUTER EDUCATION - Enterprise ERP Ledger System

A high-performance, lightweight Enterprise Resource Planning (ERP) application designed for managing student admissions, recording installment fees, tracking debt structures, and reviewing live transaction logs. 

This architecture utilizes a clean, **non-dependency vanilla framework strategy**—bypassing heavy third-party software like Spring Boot or Maven. It runs an independent Java web server coupled with native browser runtime APIs and a live MySQL engine.

---

## 🗺️ System Architecture

Use code with caution.[ Frontend: HTML5 / CSS3 / Vanilla JS ]🔄 (HTTP REST Requests via Fetch API)[ Backend: Native Java HttpServer Loop (Port 8080) ]🔄 (SQL Queries via Classpath JDBC Driver)[ Database: MySQL Server Core Engine ]
---

## ⚡ Features Matrix

* **Admissions Hub Portal:** Streamlined enrollment pipeline validating scholar parameters (Name, Contact, Program, Admission Date) and writing entries instantly to core MySQL disk blocks.
* **Enterprise Monthly Installment Terminal:** Transaction logging engine processing payment values against active scholar entries. Employs ACID-compliant transactional rollbacks to guarantee data integrity.
* **Production Database Ledger Audit Window:** Search engine query trace allowing users to filter live database records dynamically by Name, Program, or Address tracking properties.
* **Double-Click Profile Profiler Dialog:** Intercept handler allowing administrative updates to student records (such as course transfers or adjusting gross cost parameters) live.
* **System Administration Diagnostic Panel:** Infrastructure health check verifying database server status and calculating real-time metrics (e.g., Active Programs, Total Enrolled Count).

---

## 🛠️ Infrastructure Directory Blueprint

Ensure your active production workspace folder reflects the following structural context:
C:\Tariff\PAL INDIA WEB├── Server.java                   # Core Java Web Server & API Handlers├── iNDEX.html                    # User Interface Dashboard Panel└── mysql-connector-j-9.7.0.jar   # Native JDBC Database Driver
---

## 🚀 Execution & Deployment Steps

Follow these sequential parameters to run the system:

### 1. Database Initialization
Ensure your target local MySQL server (XAMPP / phpMyAdmin) is active. Execute this schema layout script inside your database terminal:

```sql
CREATE DATABASE IF NOT EXISTS pal_india_db;
USE pal_india_db;

CREATE TABLE IF NOT EXISTS student_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mobile_no VARCHAR(20) NOT NULL,
    address TEXT NOT NULL,
    course VARCHAR(100) NOT NULL,
    bill DOUBLE DEFAULT 0.0,
    pending_balance DOUBLE DEFAULT 0.0,
    date_of_admission DATE NOT NULL,
    dynamic_notes TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS fee_transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    amount_paid DOUBLE NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remarks VARCHAR(255) DEFAULT 'Installment Entry',
    FOREIGN KEY (student_id) REFERENCES student_records(id) ON DELETE CASCADE
);
```

### 2. Compile and Launch the Backend Server
Open your VS Code Terminal inside the project workspace directory (`C:\Tariff\PAL INDIA WEB\`) and run the compilation loop:

```powershell
# Compile your Java code structure
javac Server.java

# Spin up the HttpServer linking the native JDBC driver classpath
java -cp ".;mysql-connector-j-9.7.0.jar" Server
```
*Expected Console Output:* `🚀 PAL INDIA Web Server running flawlessly on http://localhost:8080`

### 3. Open the Frontend Client Application
Navigate to your project directory and simply **double-click the `iNDEX.html` file** to open it in any modern browser window (Chrome, Edge, Firefox). Your dynamic workspace dashboard is now fully functional!

---

## ⚠️ Important Rules for Data Entry
To prevent string-parsing errors in the custom data splitter, **do not input commas (`,`) or double quotes (`"`) inside text input fields** (like *Address Location* or *Profile Notes*). The system includes an automated filter fallback to sanitize input fields, but using clean text ensures optimal database communication.