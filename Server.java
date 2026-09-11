import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /*
     * IMPORTANT:
     *
     * Your project currently contains index.html in the root:
     *
     *     Server.java
     *     index.html
     *
     * Therefore "." is used as the default public directory.
     *
     * If you create a public folder later, you can set:
     *
     * PUBLIC_DIR=public
     */
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

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println("========================================");
        System.out.println("          PAL INDIA SERVER");
        System.out.println("========================================");
        System.out.println("Database : " +
                DB_HOST + ":" + DB_PORT + "/" + DB_NAME);
        System.out.println("DB User  : " + DB_USER);
        System.out.println("Web      : " +
                SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("Public   : " + PUBLIC_DIR);
        System.out.println("========================================");

        /*
         * MySQL Connector/J 9.x supports JDBC 4 automatic driver
         * loading, so Class.forName(...) is intentionally not used.
         *
         * The connector JAR must still be present on the runtime
         * classpath.
         */

        // --------------------------------------------------------
        // Test database connection before starting HTTP server
        // --------------------------------------------------------

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
                    "----------------------------------------"
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
            System.err.println();
            System.err.println(
                    "Check that:"
            );
            System.err.println(
                    "1. MySQL is running."
            );
            System.err.println(
                    "2. The database exists."
            );
            System.err.println(
                    "3. The username/password are correct."
            );
            System.err.println(
                    "4. mysql-connector-j-9.7.0.jar is on the classpath."
            );
            System.err.println();
            System.err.println(
                    "Original error: " + e.getMessage()
            );

            return;
        }

        // --------------------------------------------------------
        // Create HTTP server
        // --------------------------------------------------------

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(
                                SERVER_HOST,
                                SERVER_PORT
                        ),
                        0
                );

        // --------------------------------------------------------
        // API endpoints
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // Frontend
        // --------------------------------------------------------

        server.createContext(
                "/",
                new StaticFileHandler()
        );

        // --------------------------------------------------------
        // Thread pool
        // --------------------------------------------------------

        int threads =
                Math.max(
                        4,
                        Runtime.getRuntime()
                                .availableProcessors() * 2
                );

        server.setExecutor(
                Executors.newFixedThreadPool(threads)
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

    private static int parsePort(String value) {

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
    // HTTP SECURITY HEADERS
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
    // READ REQUEST BODY
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
    // JSON RESPONSE
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

    // ============================================================
    // ERROR RESPONSE
    // ============================================================

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
    // HEALTH CHECK
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

            double bill =
                    parseMoney(
                            body.getOrDefault(
                                    "bill",
                                    "0"
                            )
                    );

            if (bill < 0) {

                throw new IllegalArgumentException(
                        "Bill cannot be negative"
                );
            }

            String date =
                    body.getOrDefault(
                            "date_of_admission",
                            ""
                    );

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

                stmt.setDouble(
                        5,
                        bill
                );

                stmt.setDouble(
                        6,
                        bill
                );

                if (date.trim().isEmpty()) {

                    stmt.setNull(
                            7,
                            java.sql.Types.VARCHAR
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

            double newBill =
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

            if (newBill < 0) {

                throw new IllegalArgumentException(
                        "Bill cannot be negative"
                );
            }

            try (Connection conn =
                         getConnection()) {

                conn.setAutoCommit(false);

                try {

                    double oldBill;
                    double pendingBalance;

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
                                    rs.getDouble(
                                            "bill"
                                    );

                            pendingBalance =
                                    rs.getDouble(
                                            "pending_balance"
                                    );
                        }
                    }

                    double difference =
                            newBill - oldBill;

                    double newPending =
                            pendingBalance +
                                    difference;

                    /*
                     * Example:
                     *
                     * Old bill = 10,000
                     * Paid     = 3,000
                     * Pending  = 7,000
                     *
                     * New bill = 8,000
                     * New pending = 5,000
                     *
                     * This is allowed.
                     *
                     * New bill = 2,000
                     * New pending = -1,000
                     *
                     * This is rejected.
                     */

                    if (newPending < 0) {

                        conn.rollback();

                        sendError(
                                exchange,
                                400,
                                "Bill cannot be lower than the amount already paid"
                        );

                        return;
                    }

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

                        stmt.setDouble(
                                5,
                                newBill
                        );

                        stmt.setDouble(
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

                    conn.commit();

                    sendJson(
                            exchange,
                            200,
                            "{\"success\":true}"
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
                        "ORDER BY id DESC";

                StringBuilder result =
                        new StringBuilder();

                result.append("[");

                boolean first = true;

                try (Connection conn =
                             getConnection();
                     PreparedStatement stmt =
                             conn.prepareStatement(sql)) {

                    stmt.setString(
                            1,
                            pattern
                    );

                    stmt.setString(
                            2,
                            pattern
                    );

                    stmt.setString(
                            3,
                            pattern
                    );

                    stmt.setString(
                            4,
                            pattern
                    );

                    try (ResultSet rs =
                                 stmt.executeQuery()) {

                        while (rs.next()) {

                            if (!first) {
                                result.append(",");
                            }

                            first = false;

                            int id =
                                    rs.getInt(
                                            "id"
                                    );

                            result.append("{");

                            result.append(
                                    "\"id\":"
                            );

                            result.append(
                                    id
                            );

                            result.append(",");

                            result.append(
                                    "\"name\":"
                            );

                            result.append(
                                    Json.quote(
                                            rs.getString(
                                                    "name"
                                            )
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
                                            rs.getDouble(
                                                    "bill"
                                            )
                                    )
                            );

                            result.append(",");

                            result.append(
                                    "\"pending_balance\":"
                            );

                            result.append(
                                    money(
                                            rs.getDouble(
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

                        String date =
                                timestamp == null
                                        ? ""
                                        : timestamp.toString();

                        result.append("{");

                        result.append(
                                "\"date\":"
                        );

                        result.append(
                                Json.quote(date)
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
                                        rs.getDouble(
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

                double payment =
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
                        );

                if (payment <= 0) {

                    sendError(
                            exchange,
                            400,
                            "Payment amount must be greater than zero"
                    );

                    return;
                }

                try (Connection conn =
                             getConnection()) {

                    conn.setAutoCommit(false);

                    try {

                        double pending;

                        String select =
                                "SELECT pending_balance " +
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

                                pending =
                                        rs.getDouble(
                                                "pending_balance"
                                        );
                            }
                        }

                        if (payment > pending) {

                            conn.rollback();

                            sendError(
                                    exchange,
                                    400,
                                    "Payment cannot exceed pending balance"
                            );

                            return;
                        }

                        // ------------------------------------------------
                        // Update pending balance
                        // ------------------------------------------------

                        String update =
                                "UPDATE student_records " +
                                "SET pending_balance = " +
                                "pending_balance - ? " +
                                "WHERE id=?";

                        try (PreparedStatement stmt =
                                     conn.prepareStatement(update)) {

                            stmt.setDouble(
                                    1,
                                    payment
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
                        // Save transaction
                        // ------------------------------------------------

                        String insert =
                                "INSERT INTO fee_transactions " +
                                "(student_id,amount_paid,remarks) " +
                                "VALUES (?,?,?)";

                        try (PreparedStatement stmt =
                                     conn.prepareStatement(insert)) {

                            stmt.setInt(
                                    1,
                                    studentId
                            );

                            stmt.setDouble(
                                    2,
                                    payment
                            );

                            stmt.setString(
                                    3,
                                    remarks
                            );

                            stmt.executeUpdate();
                        }

                        conn.commit();

                        sendJson(
                                exchange,
                                200,
                                "{\"success\":true}"
                        );

                    } catch (Exception e) {

                        try {
                            conn.rollback();
                        } catch (SQLException ignored) {
                        }

                        throw e;
                    }
                }

            } catch (NumberFormatException e) {

                sendError(
                        exchange,
                        400,
                        "Invalid student ID or payment amount"
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
                        "Unable to process payment"
                );
            }
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

            // Remove leading slash.
            String relativePath =
                    requestPath.startsWith("/")
                            ? requestPath.substring(1)
                            : requestPath;

            Path requested =
                    PUBLIC_DIR
                            .resolve(relativePath)
                            .normalize();

            // --------------------------------------------------------
            // Prevent path traversal
            // --------------------------------------------------------

            if (!requested.startsWith(
                    PUBLIC_DIR)) {

                sendError(
                        exchange,
                        403,
                        "Forbidden"
                );

                return;
            }

            // --------------------------------------------------------
            // File does not exist
            // --------------------------------------------------------

            if (!Files.exists(requested)) {

                sendError(
                        exchange,
                        404,
                        "Page not found"
                );

                return;
            }

            if (Files.isDirectory(requested)) {

                sendError(
                        exchange,
                        404,
                        "Page not found"
                );

                return;
            }

            byte[] data;

            try {

                data =
                        Files.readAllBytes(
                                requested
                        );

            } catch (IOException e) {

                sendError(
                        exchange,
                        500,
                        "Unable to read file"
                );

                return;
            }

            String contentType =
                    contentType(requested);

            securityHeaders(exchange);

            exchange
                    .getResponseHeaders()
                    .set(
                            "Content-Type",
                            contentType
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

        // ========================================================
        // CONTENT TYPE
        // ========================================================

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
                        "Invalid student ID"
                );
            }

            return id;

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid student ID"
            );
        }
    }

    private static double parseMoney(
            String value) {

        if (value == null ||
                value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invalid monetary value"
            );
        }

        try {

            double result =
                    Double.parseDouble(
                            value.trim()
                    );

            if (!Double.isFinite(result)) {

                throw new IllegalArgumentException(
                        "Invalid monetary value"
                );
            }

            return result;

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid monetary value"
            );
        }
    }

    private static String money(
            double value) {

        return String.format(
                Locale.US,
                "%.2f",
                value
        );
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
                                "UTF-8"
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
                            "UTF-8"
                    );

            String value =
                    URLDecoder.decode(
                            pair.substring(
                                    equals + 1
                            ),
                            "UTF-8"
                    );

            result.put(
                    key,
                    value
            );
        }

        return result;
    }

    // ============================================================
    // MINIMAL JSON PARSER
    // ============================================================

    static class Json {

        // --------------------------------------------------------
        // Parse JSON object
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // Quote JSON string
        // --------------------------------------------------------

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

        // ========================================================
        // JSON PARSER
        // ========================================================

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

            // ----------------------------------------------------
            // Read JSON string
            // ----------------------------------------------------

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

            // ----------------------------------------------------
            // Read JSON value
            // ----------------------------------------------------

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

                /*
                 * Convert JSON null to empty string because
                 * this application uses String values internally.
                 */

                if ("null".equals(value)) {

                    return "";
                }

                return value;
            }
        }
    }
}
