import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;


public class Server {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static final String DB_HOST =
            env("DB_HOST", "127.0.0.1");

    private static final String DB_PORT =
            env("DB_PORT", "3306");

    private static final String DB_NAME =
            env("DB_NAME", "pal_india_db");

    private static final String DB_USER =
            env("DB_USER", "palindia");

    private static final String DB_PASSWORD =
            env("DB_PASSWORD", "");

    private static final String SERVER_HOST =
            env("SERVER_HOST", "127.0.0.1");

    private static final int SERVER_PORT =
            parsePort(env("SERVER_PORT", "8080"));

    private static final Path PUBLIC_DIR =
            Paths.get(env("PUBLIC_DIR", "."))
                    .toAbsolutePath()
                    .normalize();

    public static final String DB_URL =
            "jdbc:mysql://" +
            DB_HOST +
            ":" +
            DB_PORT +
            "/" +
            DB_NAME +
            "?useSSL=false" +
            "&serverTimezone=UTC" +
            "&characterEncoding=UTF-8" +
            "&allowPublicKeyRetrieval=true";

    private static final ZoneId INDIA_ZONE =
            ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyyMMddHHmmss"
            );

    private static final DateTimeFormatter PAYMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd-MM-yyyy HH:mm:ss"
            );

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println("========================================");
        System.out.println("          PAL INDIA SERVER");
        System.out.println("========================================");

        System.out.println(
                "Database : " +
                DB_HOST +
                ":" +
                DB_PORT +
                "/" +
                DB_NAME
        );

        System.out.println(
                "DB User  : " +
                DB_USER
        );

        System.out.println(
                "Web      : " +
                SERVER_HOST +
                ":" +
                SERVER_PORT
        );

        System.out.println(
                "Public   : " +
                PUBLIC_DIR
        );

        System.out.println("========================================");

        try (Connection ignored = getConnection()) {

            System.out.println(
                    "Database connection: OK"
            );

        } catch (SQLException e) {

            System.err.println();
            System.err.println(
                    "DATABASE CONNECTION FAILED"
            );

            System.err.println(
                    "Host     : " + DB_HOST
            );

            System.err.println(
                    "Port     : " + DB_PORT
            );

            System.err.println(
                    "Database : " + DB_NAME
            );

            System.err.println(
                    "User     : " + DB_USER
            );

            System.err.println(
                    "Original error: " + e.getMessage()
            );

            return;
        }

        try {

            initializeDatabase();

        } catch (SQLException e) {

            System.err.println();
            System.err.println(
                    "DATABASE INITIALIZATION FAILED"
            );

            System.err.println(
                    e.getMessage()
            );

            return;
        }

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(
                                SERVER_HOST,
                                SERVER_PORT
                        ),
                        0
                );

        server.createContext(
                "/api/health",
                new HealthHandler()
        );

        server.createContext(
                "/api/students/search",
                new SearchHandler()
        );

        server.createContext(
                "/api/students",
                new StudentsHandler()
        );

        server.createContext(
                "/api/payments",
                new PaymentsHandler()
        );

        server.createContext(
                "/api/receipts",
                new ReceiptHandler()
        );

        server.createContext(
                "/",
                new StaticFileHandler()
        );

        int threads =
                Math.max(
                        4,
                        Runtime.getRuntime()
                                .availableProcessors() * 2
                );

        server.setExecutor(
                Executors.newFixedThreadPool(
                        threads
                )
        );

        server.start();

        System.out.println();
        System.out.println(
                "Server started successfully."
        );

        System.out.println(
                "Open: http://" +
                SERVER_HOST +
                ":" +
                SERVER_PORT
        );

        System.out.println();
    }

    // ============================================================
    // DATABASE INITIALIZATION
    // ============================================================

    private static void initializeDatabase()
            throws SQLException {

        String sql =
                "CREATE TABLE IF NOT EXISTS fee_adjustments (" +
                "adjustment_id BIGINT NOT NULL AUTO_INCREMENT," +
                "student_id INT NOT NULL," +
                "old_bill DECIMAL(12,2) NOT NULL," +
                "new_bill DECIMAL(12,2) NOT NULL," +
                "adjustment_amount DECIMAL(12,2) NOT NULL," +
                "reason VARCHAR(500) NOT NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (adjustment_id)," +
                "INDEX idx_fee_adjustments_student (student_id)," +
                "CONSTRAINT fk_fee_adjustments_student " +
                "FOREIGN KEY (student_id) " +
                "REFERENCES student_records(id) " +
                "ON DELETE CASCADE" +
                ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
        }

        System.out.println(
                "Database initialization: OK"
        );
    }

    // ============================================================
    // ENVIRONMENT
    // ============================================================

    private static String env(
            String name,
            String defaultValue) {

        String value =
                System.getenv(name);

        if (value == null ||
                value.trim().isEmpty()) {

            return defaultValue;
        }

        return value.trim();
    }

    private static int parsePort(
            String value) {

        try {

            int port =
                    Integer.parseInt(
                            value.trim()
                    );

            if (port < 1 || port > 65535) {

                throw new IllegalArgumentException(
                        "Port must be between 1 and 65535"
                );
            }

            return port;

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid SERVER_PORT: " + value
            );
        }
    }

    // ============================================================
    // DATABASE
    // ============================================================

    public static Connection getConnection()
            throws SQLException {

        return DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD
        );
    }

    // ============================================================
    // HTTP SECURITY
    // ============================================================

    public static void securityHeaders(
            HttpExchange exchange) {

        Headers headers =
                exchange.getResponseHeaders();

        headers.set(
                "X-Content-Type-Options",
                "nosniff"
        );

        headers.set(
                "X-Frame-Options",
                "DENY"
        );

        headers.set(
                "Referrer-Policy",
                "strict-origin-when-cross-origin"
        );

        headers.set(
                "Permissions-Policy",
                "camera=(), microphone=(), geolocation=()"
        );

        headers.set(
                "Cache-Control",
                "no-store"
        );
    }

    // ============================================================
    // OPTIONS / CORS
    // ============================================================

    public static boolean handleOptions(
            HttpExchange exchange)
            throws IOException {

        securityHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            Headers headers =
                    exchange.getResponseHeaders();

            headers.set(
                    "Access-Control-Allow-Origin",
                    "*"
            );

            headers.set(
                    "Access-Control-Allow-Methods",
                    "GET,POST,PUT,OPTIONS"
            );

            headers.set(
                    "Access-Control-Allow-Headers",
                    "Content-Type,Authorization"
            );

            exchange.sendResponseHeaders(
                    204,
                    -1
            );

            exchange.close();

            return true;
        }

        return false;
    }

    // ============================================================
    // REQUEST BODY
    // ============================================================

    public static String readBody(
            HttpExchange exchange)
            throws IOException {

        try (InputStream input =
                     exchange.getRequestBody()) {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read = input.read(buffer)) != -1) {

                output.write(
                        buffer,
                        0,
                        read
                );
            }

            return output.toString(
                    StandardCharsets.UTF_8
            );
        }
    }

    // ============================================================
    // JSON
    // ============================================================

    public static void sendJson(
            HttpExchange exchange,
            int status,
            String json)
            throws IOException {

        securityHeaders(exchange);

        Headers headers =
                exchange.getResponseHeaders();

        headers.set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        headers.set(
                "Access-Control-Allow-Origin",
                "*"
        );

        byte[] bytes =
                json.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }

    public static void sendError(
            HttpExchange exchange,
            int status,
            String message)
            throws IOException {

        sendJson(
                exchange,
                status,
                "{\"success\":false,\"error\":" +
                        Json.quote(message) +
                        "}"
        );
    }

    // ============================================================
    // HEALTH
    // ============================================================

    static class HealthHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {

            if (handleOptions(exchange)) {
                return;
            }

            if (!"GET".equalsIgnoreCase(
                    exchange.getRequestMethod())) {

                sendError(
                        exchange,
                        405,
                        "Method not allowed"
                );

                return;
            }

            try (Connection ignored =
                         getConnection()) {

                sendJson(
                        exchange,
                        200,
                        "{\"success\":true," +
                                "\"database\":\"connected\"}"
                );

            } catch (Exception e) {

                sendJson(
                        exchange,
                        503,
                        "{\"success\":false," +
                                "\"database\":\"unavailable\"}"
                );
            }
        }
    }

    // ============================================================
    // STUDENTS
    // ============================================================

    static class StudentsHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {

            if (handleOptions(exchange)) {
                return;
            }

            String method =
                    exchange.getRequestMethod();

            try {

                if ("POST".equalsIgnoreCase(method)) {

                    createStudent(exchange);

                } else if ("PUT".equalsIgnoreCase(method)) {

                    updateStudent(exchange);

                } else {

                    sendError(
                            exchange,
                            405,
                            "Method not allowed"
                    );
                }

            } catch (IllegalArgumentException e) {

                sendError(
                        exchange,
                        400,
                        e.getMessage()
                );

            } catch (Exception e) {

                e.printStackTrace();

                sendError(
                        exchange,
                        500,
                        "Internal server error"
                );
            }
        }

        // ========================================================
        // CREATE STUDENT
        // ========================================================

        private void createStudent(
                HttpExchange exchange)
                throws Exception {

            Map<String, String> body =
                    Json.parse(
                            readBody(exchange)
                    );

            String name =
                    required(
                            body,
                            "name"
                    );

            String mobile =
                    required(
                            body,
                            "mobile_no"
                    );

            String address =
                    required(
                            body,
                            "address"
                    );

            String course =
                    required(
                            body,
                            "course"
                    );

            BigDecimal bill =
                    parseMoney(
                            body.getOrDefault(
                                    "bill",
                                    "0"
                            )
                    );

            String date =
                    body.getOrDefault(
                            "date_of_admission",
                            ""
                    ).trim();

            String sql =
                    "INSERT INTO student_records " +
                    "(name,mobile_no,address,course,bill," +
                    "pending_balance,date_of_admission) " +
                    "VALUES (?,?,?,?,?,?,?)";

            try (Connection conn =
                         getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement(
                                 sql,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                stmt.setString(
                        1,
                        name
                );

                stmt.setString(
                        2,
                        mobile
                );

                stmt.setString(
                        3,
                        address
                );

                stmt.setString(
                        4,
                        course
                );

                stmt.setBigDecimal(
                        5,
                        bill
                );

                stmt.setBigDecimal(
                        6,
                        bill
                );

                if (date.isEmpty()) {

                    stmt.setNull(
                            7,
                            Types.VARCHAR
                    );

                } else {

                    stmt.setString(
                            7,
                            date
                    );
                }

                stmt.executeUpdate();

                long id = -1;

                try (ResultSet keys =
                             stmt.getGeneratedKeys()) {

                    if (keys.next()) {

                        id =
                                keys.getLong(1);
                    }
                }

                sendJson(
                        exchange,
                        201,
                        "{\"success\":true," +
                                "\"id\":" +
                                id +
                                "}"
                );
            }
        }

        // ========================================================
        // UPDATE STUDENT
        // ========================================================

        private void updateStudent(
                HttpExchange exchange)
                throws Exception {

            Map<String, String> body =
                    Json.parse(
                            readBody(exchange)
                    );

            int id =
                    parseId(
                            required(
                                    body,
                                    "id"
                            )
                    );

            String name =
                    required(
                            body,
                            "name"
                    );

            String mobile =
                    required(
                            body,
                            "mobile_no"
                    );

            String address =
                    required(
                            body,
                            "address"
                    );

            String course =
                    required(
                            body,
                            "course"
                    );

            BigDecimal newBill =
                    parseMoney(
                            required(
                                    body,
                                    "bill"
                            )
                    );

            String notes =
                    body.getOrDefault(
                            "notes",
                            ""
                    );

            String adjustmentReason =
                    body.getOrDefault(
                            "adjustment_reason",
                            ""
                    ).trim();

            try (Connection conn =
                         getConnection()) {

                conn.setAutoCommit(false);

                try {

                    BigDecimal oldBill;
                    BigDecimal pendingBalance;

                    String select =
                            "SELECT bill,pending_balance " +
                            "FROM student_records " +
                            "WHERE id=? FOR UPDATE";

                    try (PreparedStatement stmt =
                                 conn.prepareStatement(select)) {

                        stmt.setInt(
                                1,
                                id
                        );

                        try (ResultSet rs =
                                     stmt.executeQuery()) {

                            if (!rs.next()) {

                                conn.rollback();

                                sendError(
                                        exchange,
                                        404,
                                        "Student not found"
                                );

                                return;
                            }

                            oldBill =
                                    moneyValue(
                                            rs.getBigDecimal(
                                                    "bill"
                                            )
                                    );

                            pendingBalance =
                                    moneyValue(
                                            rs.getBigDecimal(
                                                    "pending_balance"
                                            )
                                    );
                        }
                    }

                    BigDecimal totalPaid =
                            oldBill.subtract(
                                    pendingBalance
                            );

                    if (totalPaid.compareTo(
                            BigDecimal.ZERO
                    ) < 0) {

                        throw new SQLException(
                                "Existing student balance is inconsistent"
                        );
                    }

                    BigDecimal newPending =
                            newBill.subtract(
                                    totalPaid
                            );

                    if (newPending.compareTo(
                            BigDecimal.ZERO
                    ) < 0) {

                        conn.rollback();

                        sendError(
                                exchange,
                                400,
                                "Course fee cannot be lower than the amount already paid"
                        );

                        return;
                    }

                    BigDecimal adjustment =
                            newBill.subtract(
                                    oldBill
                            );

                    String update =
                            "UPDATE student_records SET " +
                            "name=?," +
                            "mobile_no=?," +
                            "address=?," +
                            "course=?," +
                            "bill=?," +
                            "pending_balance=?," +
                            "dynamic_notes=? " +
                            "WHERE id=?";

                    try (PreparedStatement stmt =
                                 conn.prepareStatement(update)) {

                        stmt.setString(
                                1,
                                name
                        );

                        stmt.setString(
                                2,
                                mobile
                        );

                        stmt.setString(
                                3,
                                address
                        );

                        stmt.setString(
                                4,
                                course
                        );

                        stmt.setBigDecimal(
                                5,
                                newBill
                        );

                        stmt.setBigDecimal(
                                6,
                                newPending
                        );

                        stmt.setString(
                                7,
                                notes
                        );

                        stmt.setInt(
                                8,
                                id
                        );

                        stmt.executeUpdate();
                    }

                    if (adjustment.compareTo(
                            BigDecimal.ZERO
                    ) != 0) {

                        String reason =
                                adjustmentReason.isEmpty()
                                        ? (
                                            adjustment.compareTo(
                                                    BigDecimal.ZERO
                                            ) > 0
                                                    ? "Course fee increased"
                                                    : "Course fee reduced"
                                        )
                                        : adjustmentReason;

                        String insert =
                                "INSERT INTO fee_adjustments " +
                                "(student_id,old_bill,new_bill," +
                                "adjustment_amount,reason) " +
                                "VALUES (?,?,?,?,?)";

                        try (PreparedStatement stmt =
                                     conn.prepareStatement(insert)) {

                            stmt.setInt(
                                    1,
                                    id
                            );

                            stmt.setBigDecimal(
                                    2,
                                    oldBill
                            );

                            stmt.setBigDecimal(
                                    3,
                                    newBill
                            );

                            stmt.setBigDecimal(
                                    4,
                                    adjustment
                            );

                            stmt.setString(
                                    5,
                                    reason
                            );

                            stmt.executeUpdate();
                        }
                    }

                    conn.commit();

                    StringBuilder response =
                            new StringBuilder();

                    response.append("{");

                    response.append(
                            "\"success\":true,"
                    );

                    response.append(
                            "\"old_bill\":"
                    );

                    response.append(
                            money(oldBill)
                    );

                    response.append(",");

                    response.append(
                            "\"new_bill\":"
                    );

                    response.append(
                            money(newBill)
                    );

                    response.append(",");

                    response.append(
                            "\"adjustment\":"
                    );

                    response.append(
                            money(adjustment)
                    );

                    response.append(",");

                    response.append(
                            "\"total_paid\":"
                    );

                    response.append(
                            money(totalPaid)
                    );

                    response.append(",");

                    response.append(
                            "\"pending_balance\":"
                    );

                    response.append(
                            money(newPending)
                    );

                    response.append("}");

                    sendJson(
                            exchange,
                            200,
                            response.toString()
                    );

                } catch (Exception e) {

                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                    }

                    throw e;
                }
            }
        }
    }

    // ============================================================
    // SEARCH
    // ============================================================

    static class SearchHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {

            if (handleOptions(exchange)) {
                return;
            }

            if (!"GET".equalsIgnoreCase(
                    exchange.getRequestMethod())) {

                sendError(
                        exchange,
                        405,
                        "Method not allowed"
                );

                return;
            }

            try {

                Map<String, String> params =
                        parseQuery(
                                exchange
                                        .getRequestURI()
                                        .getRawQuery()
                        );

                String filter =
                        params.getOrDefault(
                                "filter",
                                ""
                        ).trim();

                String pattern =
                        "%" + filter + "%";

                String sql =
                        "SELECT " +
                        "id," +
                        "name," +
                        "mobile_no," +
                        "address," +
                        "course," +
                        "bill," +
                        "pending_balance," +
                        "dynamic_notes," +
                        "date_of_admission " +
                        "FROM student_records " +
                        "WHERE name LIKE ? " +
                        "OR mobile_no LIKE ? " +
                        "OR course LIKE ? " +
                        "OR address LIKE ? " +
                        "OR CAST(id AS CHAR) LIKE ? " +
                        "ORDER BY id DESC";

                StringBuilder result =
                        new StringBuilder();

                result.append("[");

                boolean first = true;

                try (Connection conn =
                             getConnection();
                     PreparedStatement stmt =
                             conn.prepareStatement(sql)) {

                    stmt.setString(1, pattern);
                    stmt.setString(2, pattern);
                    stmt.setString(3, pattern);
                    stmt.setString(4, pattern);
                    stmt.setString(5, pattern);

                    try (ResultSet rs =
                                 stmt.executeQuery()) {

                        while (rs.next()) {

                            if (!first) {
                                result.append(",");
                            }

                            first = false;

                            int id =
                                    rs.getInt("id");

                            result.append("{");

                            result.append(
                                    "\"id\":"
                            );

                            result.append(id);
                            result.append(",");

                            result.append(
                                    "\"name\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString("name")
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"mobile_no\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString(
                                                    "mobile_no"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"address\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString(
                                                    "address"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"course\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString(
                                                    "course"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"bill\":"
                            );

                            result.append(
                                    money(
                                            rs.getBigDecimal("bill")
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"pending_balance\":"
                            );

                            result.append(
                                    money(
                                            rs.getBigDecimal(
                                                    "pending_balance"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"notes\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString(
                                                    "dynamic_notes"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"date_of_admission\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString(
                                                    "date_of_admission"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"history\":"
                            );

                            appendPaymentHistory(
                                    conn,
                                    id,
                                    result
                            );

                            result.append(",");

                            result.append(
                                    "\"adjustments\":"
                            );

                            appendAdjustmentHistory(
                                    conn,
                                    id,
                                    result
                            );

                            result.append("}");
                        }
                    }
                }

                result.append("]");

                sendJson(
                        exchange,
                        200,
                        result.toString()
                );

            } catch (Exception e) {

                e.printStackTrace();

                sendError(
                        exchange,
                        500,
                        "Unable to search students"
                );
            }
        }

        // ========================================================
        // PAYMENT HISTORY
        // ========================================================

        private void appendPaymentHistory(
                Connection conn,
                int studentId,
                StringBuilder result)
                throws SQLException {

            result.append("[");

            String sql =
                    "SELECT " +
                    "transaction_id," +
                    "amount_paid," +
                    "payment_date," +
                    "remarks " +
                    "FROM fee_transactions " +
                    "WHERE student_id=? " +
                    "ORDER BY transaction_id ASC";

            boolean first = true;

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(
                        1,
                        studentId
                );

                try (ResultSet rs =
                             stmt.executeQuery()) {

                    while (rs.next()) {

                        if (!first) {
                            result.append(",");
                        }

                        first = false;

                        Timestamp timestamp =
                                rs.getTimestamp(
                                        "payment_date"
                                );

                        result.append("{");

                        result.append(
                                "\"transaction_id\":"
                        );

                        result.append(
                                rs.getLong(
                                        "transaction_id"
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"date\":"
                        );

                        result.append(
                                Json.quote(
                                        timestamp == null
                                                ? ""
                                                : timestamp.toString()
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"remarks\":"
                        );

                        result.append(
                                Json.quote(
                                        rs.getString(
                                                "remarks"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"amount\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "amount_paid"
                                        )
                                )
                        );

                        result.append("}");
                    }
                }
            }

            result.append("]");
        }

        // ========================================================
        // FEE ADJUSTMENT HISTORY
        // ========================================================

        private void appendAdjustmentHistory(
                Connection conn,
                int studentId,
                StringBuilder result)
                throws SQLException {

            result.append("[");

            String sql =
                    "SELECT " +
                    "adjustment_id," +
                    "old_bill," +
                    "new_bill," +
                    "adjustment_amount," +
                    "reason," +
                    "created_at " +
                    "FROM fee_adjustments " +
                    "WHERE student_id=? " +
                    "ORDER BY adjustment_id ASC";

            boolean first = true;

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(
                        1,
                        studentId
                );

                try (ResultSet rs =
                             stmt.executeQuery()) {

                    while (rs.next()) {

                        if (!first) {
                            result.append(",");
                        }

                        first = false;

                        Timestamp timestamp =
                                rs.getTimestamp(
                                        "created_at"
                                );

                        result.append("{");

                        result.append(
                                "\"adjustment_id\":"
                        );

                        result.append(
                                rs.getLong(
                                        "adjustment_id"
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"old_bill\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "old_bill"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"new_bill\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "new_bill"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"adjustment_amount\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "adjustment_amount"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"reason\":"
                        );

                        result.append(
                                Json.quote(
                                        rs.getString(
                                                "reason"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"date\":"
                        );

                        result.append(
                                Json.quote(
                                        timestamp == null
                                                ? ""
                                                : timestamp.toString()
                                )
                        );

                        result.append("}");
                    }
                }
            }

            result.append("]");
        }
    }

    // ============================================================
    // PAYMENTS
    // ============================================================

    static class PaymentsHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {

            if (handleOptions(exchange)) {
                return;
            }

            if (!"POST".equalsIgnoreCase(
                    exchange.getRequestMethod())) {

                sendError(
                        exchange,
                        405,
                        "Method not allowed"
                );

                return;
            }

            try {

                Map<String, String> body =
                        Json.parse(
                                readBody(exchange)
                        );

                int studentId =
                        parseId(
                                required(
                                        body,
                                        "id"
                                )
                        );

                BigDecimal payment =
                        parseMoney(
                                required(
                                        body,
                                        "amount"
                                )
                        );

                String remarks =
                        body.getOrDefault(
                                "remarks",
                                "Installment Entry"
                        ).trim();

                if (payment.compareTo(
                        BigDecimal.ZERO
                ) <= 0) {

                    throw new IllegalArgumentException(
                            "Payment amount must be greater than zero"
                    );
                }

                try (Connection conn =
                             getConnection()) {

                    conn.setAutoCommit(false);

                    try {

                        String name;
                        String mobile;
                        String address;
                        String course;

                        BigDecimal courseFee;
                        BigDecimal previousPending;

                        String select =
                                "SELECT " +
                                "name," +
                                "mobile_no," +
                                "address," +
                                "course," +
                                "bill," +
                                "pending_balance " +
                                "FROM student_records " +
                                "WHERE id=? FOR UPDATE";

                        try (PreparedStatement stmt =
                                     conn.prepareStatement(select)) {

                            stmt.setInt(
                                    1,
                                    studentId
                            );

                            try (ResultSet rs =
                                         stmt.executeQuery()) {

                                if (!rs.next()) {

                                    conn.rollback();

                                    sendError(
                                            exchange,
                                            404,
                                            "Student not found"
                                    );

                                    return;
                                }

                                name =
                                        rs.getString("name");

                                mobile =
                                        rs.getString(
                                                "mobile_no"
                                        );

                                address =
                                        rs.getString(
                                                "address"
                                        );

                                course =
                                        rs.getString("course");

                                courseFee =
                                        moneyValue(
                                                rs.getBigDecimal(
                                                        "bill"
                                                )
                                        );

                                previousPending =
                                        moneyValue(
                                                rs.getBigDecimal(
                                                        "pending_balance"
                                                )
                                        );
                            }
                        }

                        if (previousPending.compareTo(
                                BigDecimal.ZERO
                        ) < 0) {

                            throw new SQLException(
                                    "Student balance is invalid"
                            );
                        }

                        if (payment.compareTo(
                                previousPending
                        ) > 0) {

                            conn.rollback();

                            sendError(
                                    exchange,
                                    400,
                                    "Payment cannot exceed pending balance"
                            );

                            return;
                        }

                        BigDecimal previousPaid =
                                courseFee.subtract(
                                        previousPending
                                );

                        if (previousPaid.compareTo(
                                BigDecimal.ZERO
                        ) < 0) {

                            throw new SQLException(
                                    "Student payment history is inconsistent"
                            );
                        }

                        BigDecimal totalPaid =
                                previousPaid.add(
                                        payment
                                );

                        BigDecimal newPending =
                                courseFee.subtract(
                                        totalPaid
                                );

                        newPending =
                                normalizeMoney(
                                        newPending
                                );

                        if (newPending.compareTo(
                                BigDecimal.ZERO
                        ) < 0) {

                            throw new SQLException(
                                    "Calculated balance cannot be negative"
                            );
                        }

                        // ------------------------------------------------
                        // Update balance
                        // ------------------------------------------------

                        String update =
                                "UPDATE student_records " +
                                "SET pending_balance=? " +
                                "WHERE id=?";

                        try (PreparedStatement stmt =
                                     conn.prepareStatement(update)) {

                            stmt.setBigDecimal(
                                    1,
                                    newPending
                            );

                            stmt.setInt(
                                    2,
                                    studentId
                            );

                            int affected =
                                    stmt.executeUpdate();

                            if (affected != 1) {

                                throw new SQLException(
                                        "Student balance could not be updated"
                                );
                            }
                        }

                        // ------------------------------------------------
                        // Save payment
                        // ------------------------------------------------

                        long transactionId = -1;

                        String insert =
                                "INSERT INTO fee_transactions " +
                                "(student_id,amount_paid,remarks) " +
                                "VALUES (?,?,?)";

                        try (PreparedStatement stmt =
                                     conn.prepareStatement(
                                             insert,
                                             Statement.RETURN_GENERATED_KEYS
                                     )) {

                            stmt.setInt(
                                    1,
                                    studentId
                            );

                            stmt.setBigDecimal(
                                    2,
                                    payment
                            );

                            stmt.setString(
                                    3,
                                    remarks.isEmpty()
                                            ? "Installment Entry"
                                            : remarks
                            );

                            stmt.executeUpdate();

                            try (ResultSet keys =
                                         stmt.getGeneratedKeys()) {

                                if (keys.next()) {

                                    transactionId =
                                            keys.getLong(1);
                                }
                            }
                        }

                        if (transactionId <= 0) {

                            throw new SQLException(
                                    "Unable to create payment receipt number"
                            );
                        }

                        /*
                         * Generate the receipt timestamp using
                         * India Standard Time.
                         */
                        ZonedDateTime indiaNow =
                                ZonedDateTime.now(
                                        INDIA_ZONE
                                );

                        String receiptNumber =
                                "PAY-" +
                                indiaNow.format(
                                        RECEIPT_DATE_FORMAT
                                ) +
                                "-" +
                                transactionId;

                        String paymentDate =
                                indiaNow.format(
                                        PAYMENT_DATE_FORMAT
                                );

                        conn.commit();

                        // ------------------------------------------------
                        // Build response
                        // ------------------------------------------------

                        StringBuilder response =
                                new StringBuilder();

                        response.append("{");

                        response.append(
                                "\"success\":true,"
                        );

                        response.append(
                                "\"receipt_no\":"
                        );

                        response.append(
                                Json.quote(
                                        receiptNumber
                                )
                        );

                        response.append(",");

                        response.append(
                                "\"transaction_id\":"
                        );

                        response.append(
                                transactionId
                        );

                        response.append(",");

                        response.append(
                                "\"payment_date\":"
                        );

                        response.append(
                                Json.quote(
                                        paymentDate
                                )
                        );

                        response.append(",");

                        response.append(
                                "\"student\":{"
                        );

                        response.append(
                                "\"id\":"
                        );

                        response.append(
                                studentId
                        );

                        response.append(",");

                        response.append(
                                "\"name\":"
                        );

                        response.append(
                                Json.quote(name)
                        );

                        response.append(",");

                        response.append(
                                "\"mobile_no\":"
                        );

                        response.append(
                                Json.quote(mobile)
                        );

                        response.append(",");

                        response.append(
                                "\"address\":"
                        );

                        response.append(
                                Json.quote(address)
                        );

                        response.append(",");

                        response.append(
                                "\"course\":"
                        );

                        response.append(
                                Json.quote(course)
                        );

                        response.append("}");

                        response.append(",");

                        response.append(
                                "\"course_fee\":"
                        );

                        response.append(
                                money(courseFee)
                        );

                        response.append(",");

                        response.append(
                                "\"previous_paid\":"
                        );

                        response.append(
                                money(previousPaid)
                        );

                        response.append(",");

                        response.append(
                                "\"payment_amount\":"
                        );

                        response.append(
                                money(payment)
                        );

                        response.append(",");

                        response.append(
                                "\"total_paid\":"
                        );

                        response.append(
                                money(totalPaid)
                        );

                        response.append(",");

                        response.append(
                                "\"previous_pending\":"
                        );

                        response.append(
                                money(previousPending)
                        );

                        response.append(",");

                        response.append(
                                "\"pending_balance\":"
                        );

                        response.append(
                                money(newPending)
                        );

                        response.append(",");

                        response.append(
                                "\"remarks\":"
                        );

                        response.append(
                                Json.quote(
                                        remarks.isEmpty()
                                                ? "Installment Entry"
                                                : remarks
                                )
                        );

                        response.append(",");

                        response.append(
                                "\"adjustments\":"
                        );

                        /*
                         * This connection is still available after
                         * commit, so adjustment history can be read.
                         */
                        appendAdjustmentHistory(
                                conn,
                                studentId,
                                response
                        );

                        response.append("}");

                        sendJson(
                                exchange,
                                200,
                                response.toString()
                        );

                    } catch (Exception e) {

                        try {
                            conn.rollback();
                        } catch (SQLException ignored) {
                        }

                        throw e;
                    }
                }

            } catch (IllegalArgumentException e) {

                sendError(
                        exchange,
                        400,
                        e.getMessage()
                );

            } catch (Exception e) {

                e.printStackTrace();

                sendError(
                        exchange,
                        500,
                        "Unable to process payment"
                );
            }
        }

        private void appendAdjustmentHistory(
                Connection conn,
                int studentId,
                StringBuilder result)
                throws SQLException {

            result.append("[");

            String sql =
                    "SELECT " +
                    "adjustment_id," +
                    "old_bill," +
                    "new_bill," +
                    "adjustment_amount," +
                    "reason," +
                    "created_at " +
                    "FROM fee_adjustments " +
                    "WHERE student_id=? " +
                    "ORDER BY adjustment_id ASC";

            boolean first = true;

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(
                        1,
                        studentId
                );

                try (ResultSet rs =
                             stmt.executeQuery()) {

                    while (rs.next()) {

                        if (!first) {
                            result.append(",");
                        }

                        first = false;

                        Timestamp timestamp =
                                rs.getTimestamp(
                                        "created_at"
                                );

                        result.append("{");

                        result.append(
                                "\"adjustment_id\":"
                        );

                        result.append(
                                rs.getLong(
                                        "adjustment_id"
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"old_bill\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "old_bill"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"new_bill\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "new_bill"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"adjustment_amount\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "adjustment_amount"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"reason\":"
                        );

                        result.append(
                                Json.quote(
                                        rs.getString(
                                                "reason"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"date\":"
                        );

                        result.append(
                                Json.quote(
                                        timestamp == null
                                                ? ""
                                                : timestamp.toString()
                                )
                        );

                        result.append("}");
                    }
                }
            }

            result.append("]");
        }
    }

    // ============================================================
    // RECEIPT API
    // ============================================================

    static class ReceiptHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {

            if (handleOptions(exchange)) {
                return;
            }

            if (!"GET".equalsIgnoreCase(
                    exchange.getRequestMethod())) {

                sendError(
                        exchange,
                        405,
                        "Method not allowed"
                );

                return;
            }

            try {

                Map<String, String> params =
                        parseQuery(
                                exchange
                                        .getRequestURI()
                                        .getRawQuery()
                        );

                int transactionId =
                        parseId(
                                required(
                                        params,
                                        "transaction_id"
                                )
                        );

                String json =
                        buildReceiptJson(
                                transactionId
                        );

                if (json == null) {

                    sendError(
                            exchange,
                            404,
                            "Receipt not found"
                    );

                    return;
                }

                sendJson(
                        exchange,
                        200,
                        json
                );

            } catch (IllegalArgumentException e) {

                sendError(
                        exchange,
                        400,
                        e.getMessage()
                );

            } catch (Exception e) {

                e.printStackTrace();

                sendError(
                        exchange,
                        500,
                        "Unable to load receipt"
                );
            }
        }

        private String buildReceiptJson(
                int transactionId)
                throws SQLException {

            String sql =
                    "SELECT " +
                    "t.transaction_id," +
                    "t.student_id," +
                    "t.amount_paid," +
                    "t.payment_date," +
                    "t.remarks," +
                    "s.name," +
                    "s.mobile_no," +
                    "s.address," +
                    "s.course," +
                    "s.bill," +
                    "s.pending_balance " +
                    "FROM fee_transactions t " +
                    "INNER JOIN student_records s " +
                    "ON s.id=t.student_id " +
                    "WHERE t.transaction_id=?";

            try (Connection conn =
                         getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(
                        1,
                        transactionId
                );

                try (ResultSet rs =
                             stmt.executeQuery()) {

                    if (!rs.next()) {
                        return null;
                    }

                    BigDecimal courseFee =
                            moneyValue(
                                    rs.getBigDecimal("bill")
                            );

                    BigDecimal pending =
                            moneyValue(
                                    rs.getBigDecimal(
                                            "pending_balance"
                                    )
                            );

                    BigDecimal payment =
                            moneyValue(
                                    rs.getBigDecimal(
                                            "amount_paid"
                                    )
                            );

                    BigDecimal totalPaid =
                            courseFee.subtract(
                                    pending
                            );

                    BigDecimal previousPending =
                            pending.add(payment);

                    BigDecimal previousPaid =
                            courseFee.subtract(
                                    previousPending
                            );

                    /*
                     * Build the receipt number using the exact
                     * same YYYYMMDDHHMMSS format used when the
                     * payment was created.
                     *
                     * The database connection uses UTC, so the
                     * stored timestamp is converted to India time
                     * before formatting.
                     */
                    Timestamp paymentTimestamp =
                            rs.getTimestamp(
                                    "payment_date"
                            );

                    ZonedDateTime receiptTime;

                    if (paymentTimestamp == null) {

                        receiptTime =
                                ZonedDateTime.now(
                                        INDIA_ZONE
                                );

                    } else {

                        receiptTime =
                                paymentTimestamp
                                        .toInstant()
                                        .atZone(
                                                INDIA_ZONE
                                        );
                    }

                    String receiptNumber =
                            "PAY-" +
                            receiptTime.format(
                                    RECEIPT_DATE_FORMAT
                            ) +
                            "-" +
                            transactionId;

                    String paymentDate =
                            receiptTime.format(
                                    PAYMENT_DATE_FORMAT
                            );

                    StringBuilder result =
                            new StringBuilder();

                    result.append("{");

                    result.append(
                            "\"success\":true,"
                    );

                    result.append(
                            "\"receipt_no\":"
                    );

                    result.append(
                            Json.quote(
                                    receiptNumber
                            )
                    );

                    result.append(",");

                    result.append(
                            "\"transaction_id\":"
                    );

                    result.append(
                            transactionId
                    );

                    result.append(",");

                    result.append(
                            "\"payment_date\":"
                    );

                    result.append(
                            Json.quote(
                                    paymentDate
                            )
                    );

                    result.append(",");

                    result.append(
                            "\"student\":{"
                    );

                    result.append(
                            "\"id\":"
                    );

                    result.append(
                            rs.getInt("student_id")
                    );

                    result.append(",");

                    result.append(
                            "\"name\":"
                    );

                    result.append(
                            Json.quote(
                                    rs.getString("name")
                            )
                    );

                    result.append(",");

                    result.append(
                            "\"mobile_no\":"
                    );

                    result.append(
                            Json.quote(
                                    rs.getString(
                                            "mobile_no"
                                    )
                            )
                    );

                    result.append(",");

                    result.append(
                            "\"address\":"
                    );

                    result.append(
                            Json.quote(
                                    rs.getString("address")
                            )
                    );

                    result.append(",");

                    result.append(
                            "\"course\":"
                    );

                    result.append(
                            Json.quote(
                                    rs.getString("course")
                            )
                    );

                    result.append("}");

                    result.append(",");

                    result.append(
                            "\"course_fee\":"
                    );

                    result.append(
                            money(courseFee)
                    );

                    result.append(",");

                    result.append(
                            "\"previous_paid\":"
                    );

                    result.append(
                            money(previousPaid)
                    );

                    result.append(",");

                    result.append(
                            "\"payment_amount\":"
                    );

                    result.append(
                            money(payment)
                    );

                    result.append(",");

                    result.append(
                            "\"total_paid\":"
                    );

                    result.append(
                            money(totalPaid)
                    );

                    result.append(",");

                    result.append(
                            "\"previous_pending\":"
                    );

                    result.append(
                            money(previousPending)
                    );

                    result.append(",");

                    result.append(
                            "\"pending_balance\":"
                    );

                    result.append(
                            money(pending)
                    );

                    result.append(",");

                    result.append(
                            "\"remarks\":"
                    );

                    result.append(
                            Json.quote(
                                    rs.getString(
                                            "remarks"
                                    )
                            )
                    );

                    result.append(",");

                    result.append(
                            "\"adjustments\":"
                    );

                    appendAdjustments(
                            conn,
                            rs.getInt("student_id"),
                            result
                    );

                    result.append(",");

                    result.append(
                            "\"payments\":"
                    );

                    appendPayments(
                            conn,
                            rs.getInt("student_id"),
                            result
                    );

                    result.append("}");

                    return result.toString();
                }
            }
        }

        private void appendAdjustments(
                Connection conn,
                int studentId,
                StringBuilder result)
                throws SQLException {

            result.append("[");

            String sql =
                    "SELECT " +
                    "adjustment_id," +
                    "old_bill," +
                    "new_bill," +
                    "adjustment_amount," +
                    "reason," +
                    "created_at " +
                    "FROM fee_adjustments " +
                    "WHERE student_id=? " +
                    "ORDER BY adjustment_id ASC";

            boolean first = true;

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(
                        1,
                        studentId
                );

                try (ResultSet rs =
                             stmt.executeQuery()) {

                    while (rs.next()) {

                        if (!first) {
                            result.append(",");
                        }

                        first = false;

                        result.append("{");

                        result.append(
                                "\"adjustment_id\":"
                        );

                        result.append(
                                rs.getLong(
                                        "adjustment_id"
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"old_bill\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "old_bill"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"new_bill\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "new_bill"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"adjustment_amount\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "adjustment_amount"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"reason\":"
                        );

                        result.append(
                                Json.quote(
                                        rs.getString(
                                                "reason"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"date\":"
                        );

                        result.append(
                                Json.quote(
                                        String.valueOf(
                                                rs.getTimestamp(
                                                        "created_at"
                                                )
                                        )
                                )
                        );

                        result.append("}");
                    }
                }
            }

            result.append("]");
        }

        private void appendPayments(
                Connection conn,
                int studentId,
                StringBuilder result)
                throws SQLException {

            result.append("[");

            String sql =
                    "SELECT " +
                    "transaction_id," +
                    "amount_paid," +
                    "payment_date," +
                    "remarks " +
                    "FROM fee_transactions " +
                    "WHERE student_id=? " +
                    "ORDER BY transaction_id ASC";

            boolean first = true;

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(
                        1,
                        studentId
                );

                try (ResultSet rs =
                             stmt.executeQuery()) {

                    while (rs.next()) {

                        if (!first) {
                            result.append(",");
                        }

                        first = false;

                        result.append("{");

                        result.append(
                                "\"transaction_id\":"
                        );

                        result.append(
                                rs.getLong(
                                        "transaction_id"
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"amount\":"
                        );

                        result.append(
                                money(
                                        rs.getBigDecimal(
                                                "amount_paid"
                                        )
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"date\":"
                        );

                        Timestamp timestamp =
                                rs.getTimestamp(
                                        "payment_date"
                                );

                        String formattedDate = "";

                        if (timestamp != null) {

                            formattedDate =
                                    timestamp
                                            .toInstant()
                                            .atZone(
                                                    INDIA_ZONE
                                            )
                                            .format(
                                                    PAYMENT_DATE_FORMAT
                                            );
                        }

                        result.append(
                                Json.quote(
                                        formattedDate
                                )
                        );

                        result.append(",");

                        result.append(
                                "\"remarks\":"
                        );

                        result.append(
                                Json.quote(
                                        rs.getString(
                                                "remarks"
                                        )
                                )
                        );

                        result.append("}");
                    }
                }
            }

            result.append("]");
        }
    }

    // ============================================================
    // STATIC FILE SERVER
    // ============================================================

    static class StaticFileHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange)
                throws IOException {

            if (handleOptions(exchange)) {
                return;
            }

            if (!"GET".equalsIgnoreCase(
                    exchange.getRequestMethod())) {

                sendError(
                        exchange,
                        405,
                        "Method not allowed"
                );

                return;
            }

            String requestPath =
                    exchange
                            .getRequestURI()
                            .getPath();

            if (requestPath == null ||
                    requestPath.isEmpty() ||
                    "/".equals(requestPath)) {

                requestPath =
                        "/index.html";
            }

            String relativePath =
                    requestPath.startsWith("/")
                            ? requestPath.substring(1)
                            : requestPath;

            Path requested =
                    PUBLIC_DIR
                            .resolve(relativePath)
                            .normalize();

            if (!requested.startsWith(
                    PUBLIC_DIR)) {

                sendError(
                        exchange,
                        403,
                        "Forbidden"
                );

                return;
            }

            if (!Files.exists(requested) ||
                    Files.isDirectory(requested)) {

                sendError(
                        exchange,
                        404,
                        "Page not found"
                );

                return;
            }

            byte[] data =
                    Files.readAllBytes(
                            requested
                    );

            securityHeaders(exchange);

            exchange
                    .getResponseHeaders()
                    .set(
                            "Content-Type",
                            contentType(requested)
                    );

            exchange.sendResponseHeaders(
                    200,
                    data.length
            );

            try (OutputStream output =
                         exchange.getResponseBody()) {

                output.write(data);
            }
        }

        private String contentType(
                Path file) {

            String name =
                    file.getFileName()
                            .toString()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (name.endsWith(".html") ||
                    name.endsWith(".htm")) {

                return "text/html; charset=UTF-8";
            }

            if (name.endsWith(".css")) {

                return "text/css; charset=UTF-8";
            }

            if (name.endsWith(".js")) {

                return "application/javascript; charset=UTF-8";
            }

            if (name.endsWith(".json")) {

                return "application/json; charset=UTF-8";
            }

            if (name.endsWith(".png")) {

                return "image/png";
            }

            if (name.endsWith(".jpg") ||
                    name.endsWith(".jpeg")) {

                return "image/jpeg";
            }

            if (name.endsWith(".gif")) {

                return "image/gif";
            }

            if (name.endsWith(".webp")) {

                return "image/webp";
            }

            if (name.endsWith(".svg")) {

                return "image/svg+xml";
            }

            if (name.endsWith(".ico")) {

                return "image/x-icon";
            }

            if (name.endsWith(".txt")) {

                return "text/plain; charset=UTF-8";
            }

            return "application/octet-stream";
        }
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private static String required(
            Map<String, String> body,
            String key) {

        String value =
                body.get(key);

        if (value == null ||
                value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Missing required field: " + key
            );
        }

        return value.trim();
    }

    private static int parseId(
            String value) {

        try {

            int id =
                    Integer.parseInt(
                            value.trim()
                    );

            if (id <= 0) {

                throw new IllegalArgumentException(
                        "Invalid ID"
                );
            }

            return id;

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid ID"
            );
        }
    }

    private static BigDecimal parseMoney(
            String value) {

        if (value == null ||
                value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invalid monetary value"
            );
        }

        try {

            BigDecimal amount =
                    new BigDecimal(
                            value.trim()
                    );

            if (amount.compareTo(
                    BigDecimal.ZERO
            ) < 0) {

                throw new IllegalArgumentException(
                        "Monetary value cannot be negative"
                );
            }

            return normalizeMoney(
                    amount
            );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid monetary value"
            );
        }
    }

    private static BigDecimal moneyValue(
            BigDecimal value) {

        if (value == null) {

            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return normalizeMoney(value);
    }

    private static BigDecimal normalizeMoney(
            BigDecimal value) {

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private static String money(
            BigDecimal value) {

        return normalizeMoney(
                value
        ).toPlainString();
    }

    // ============================================================
    // QUERY STRING
    // ============================================================

    private static Map<String, String> parseQuery(
            String query)
            throws IOException {

        Map<String, String> result =
                new HashMap<>();

        if (query == null ||
                query.isEmpty()) {

            return result;
        }

        String[] pairs =
                query.split("&");

        for (String pair : pairs) {

            if (pair.isEmpty()) {
                continue;
            }

            int equals =
                    pair.indexOf('=');

            if (equals < 0) {

                String key =
                        URLDecoder.decode(
                                pair,
                                StandardCharsets.UTF_8
                                        .name()
                        );

                result.put(
                        key,
                        ""
                );

                continue;
            }

            String key =
                    URLDecoder.decode(
                            pair.substring(
                                    0,
                                    equals
                            ),
                            StandardCharsets.UTF_8
                                    .name()
                    );

            String value =
                    URLDecoder.decode(
                            pair.substring(
                                    equals + 1
                            ),
                            StandardCharsets.UTF_8
                                    .name()
                    );

            result.put(
                    key,
                    value
            );
        }

        return result;
    }

    // ============================================================
    // JSON
    // ============================================================

    static class Json {

        static Map<String, String> parse(
                String input) {

            Map<String, String> result =
                    new HashMap<>();

            if (input == null ||
                    input.trim().isEmpty()) {

                throw new IllegalArgumentException(
                        "Request body is empty"
                );
            }

            Parser parser =
                    new Parser(input);

            parser.skipWhitespace();

            if (parser.peek() != '{') {

                throw new IllegalArgumentException(
                        "JSON object expected"
                );
            }

            parser.read();

            parser.skipWhitespace();

            if (parser.peek() == '}') {

                parser.read();

                return result;
            }

            while (true) {

                parser.skipWhitespace();

                if (parser.peek() != '"') {

                    throw new IllegalArgumentException(
                            "JSON property name expected"
                    );
                }

                String key =
                        parser.readString();

                parser.skipWhitespace();

                if (parser.read() != ':') {

                    throw new IllegalArgumentException(
                            "Invalid JSON: expected ':'"
                    );
                }

                parser.skipWhitespace();

                String value =
                        parser.readValue();

                result.put(
                        key,
                        value
                );

                parser.skipWhitespace();

                char next =
                        parser.read();

                if (next == '}') {
                    break;
                }

                if (next != ',') {

                    throw new IllegalArgumentException(
                            "Invalid JSON: expected ',' or '}'"
                    );
                }
            }

            parser.skipWhitespace();

            if (!parser.isEnd()) {

                throw new IllegalArgumentException(
                        "Invalid JSON: unexpected data"
                );
            }

            return result;
        }

        static String quote(
                String value) {

            if (value == null) {
                return "null";
            }

            StringBuilder result =
                    new StringBuilder();

            result.append('"');

            for (char c :
                    value.toCharArray()) {

                switch (c) {

                    case '"':

                        result.append(
                                "\\\""
                        );

                        break;

                    case '\\':

                        result.append(
                                "\\\\"
                        );

                        break;

                    case '\b':

                        result.append(
                                "\\b"
                        );

                        break;

                    case '\f':

                        result.append(
                                "\\f"
                        );

                        break;

                    case '\n':

                        result.append(
                                "\\n"
                        );

                        break;

                    case '\r':

                        result.append(
                                "\\r"
                        );

                        break;

                    case '\t':

                        result.append(
                                "\\t"
                        );

                        break;

                    default:

                        if (c < 32) {

                            result.append(
                                    String.format(
                                            Locale.US,
                                            "\\u%04x",
                                            (int) c
                                    )
                            );

                        } else {

                            result.append(c);
                        }

                        break;
                }
            }

            result.append('"');

            return result.toString();
        }

        static class Parser {

            private final String input;

            private int position;

            Parser(String input) {

                this.input =
                        input.trim();

                this.position = 0;
            }

            boolean isEnd() {

                return position >=
                        input.length();
            }

            char peek() {

                if (position >=
                        input.length()) {

                    return '\0';
                }

                return input.charAt(
                        position
                );
            }

            char read() {

                if (position >=
                        input.length()) {

                    throw new IllegalArgumentException(
                            "Unexpected end of JSON"
                    );
                }

                return input.charAt(
                        position++
                );
            }

            void skipWhitespace() {

                while (position <
                        input.length()) {

                    char c =
                            input.charAt(
                                    position
                            );

                    if (!Character.isWhitespace(c)) {
                        break;
                    }

                    position++;
                }
            }

            String readString() {

                if (read() != '"') {

                    throw new IllegalArgumentException(
                            "JSON string expected"
                    );
                }

                StringBuilder result =
                        new StringBuilder();

                while (position <
                        input.length()) {

                    char c =
                            read();

                    if (c == '"') {

                        return result.toString();
                    }

                    if (c == '\\') {

                        if (position >=
                                input.length()) {

                            throw new IllegalArgumentException(
                                    "Invalid JSON escape"
                            );
                        }

                        char escaped =
                                read();

                        switch (escaped) {

                            case '"':

                                result.append('"');

                                break;

                            case '\\':

                                result.append('\\');

                                break;

                            case '/':

                                result.append('/');

                                break;

                            case 'b':

                                result.append('\b');

                                break;

                            case 'f':

                                result.append('\f');

                                break;

                            case 'n':

                                result.append('\n');

                                break;

                            case 'r':

                                result.append('\r');

                                break;

                            case 't':

                                result.append('\t');

                                break;

                            case 'u':

                                if (position + 4 >
                                        input.length()) {

                                    throw new IllegalArgumentException(
                                            "Invalid Unicode escape"
                                    );
                                }

                                String hex =
                                        input.substring(
                                                position,
                                                position + 4
                                        );

                                try {

                                    result.append(
                                            (char)
                                                    Integer.parseInt(
                                                            hex,
                                                            16
                                                    )
                                    );

                                } catch (
                                        NumberFormatException e) {

                                    throw new IllegalArgumentException(
                                            "Invalid Unicode escape"
                                    );
                                }

                                position += 4;

                                break;

                            default:

                                throw new IllegalArgumentException(
                                        "Invalid JSON escape"
                                );
                        }

                    } else {

                        result.append(c);
                    }
                }

                throw new IllegalArgumentException(
                        "Unterminated JSON string"
                );
            }

            String readValue() {

                if (peek() == '"') {

                    return readString();
                }

                int start =
                        position;

                while (position <
                        input.length()) {

                    char c =
                            input.charAt(
                                    position
                            );

                    if (c == ',' ||
                            c == '}' ||
                            Character.isWhitespace(c)) {

                        break;
                    }

                    position++;
                }

                if (start == position) {

                    throw new IllegalArgumentException(
                            "JSON value expected"
                    );
                }

                String value =
                        input.substring(
                                start,
                                position
                        );

                if ("null".equals(value)) {
                    return "";
                }

                return value;
            }
        }
    }
}
