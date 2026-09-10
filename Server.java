import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;

public class Server {

    /*
     * Production configuration is read from environment variables.
     *
     * DB_HOST
     * DB_PORT
     * DB_NAME
     * DB_USER
     * DB_PASSWORD
     * SERVER_HOST
     * SERVER_PORT
     */

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
            Integer.parseInt(env("SERVER_PORT", "8080"));

    private static final Path PUBLIC_DIR =
            Paths.get(env("PUBLIC_DIR", "public"))
                 .toAbsolutePath()
                 .normalize();

    public static final String DB_URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
                    + "?useSSL=false"
                    + "&serverTimezone=UTC"
                    + "&characterEncoding=UTF-8";

    public static void main(String[] args) throws Exception {

        Class.forName("com.mysql.cj.jdbc.Driver");

        System.out.println("========================================");
        System.out.println(" PAL INDIA SERVER");
        System.out.println("========================================");
        System.out.println("Database : " + DB_HOST + ":" + DB_PORT + "/" + DB_NAME);
        System.out.println("Web      : " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("Public   : " + PUBLIC_DIR);
        System.out.println("========================================");

        // Verify database connection before starting.
        try (Connection ignored =
                     DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            System.out.println("Database connection: OK");
        }

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(SERVER_HOST, SERVER_PORT),
                        0
                );

        // API endpoints
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/students/search", new SearchHandler());
        server.createContext("/api/students", new StudentsHandler());
        server.createContext("/api/payments", new PaymentsHandler());

        // Frontend/static files
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(
                Executors.newFixedThreadPool(
                        Math.max(4,
                                Runtime.getRuntime().availableProcessors() * 2)
                )
        );

        server.start();

        System.out.println();
        System.out.println("Server started successfully.");
        System.out.println(
                "Local URL: http://" + SERVER_HOST + ":" + SERVER_PORT
        );
    }

    // ============================================================
    // ENVIRONMENT
    // ============================================================

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);

        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        return value.trim();
    }

    // ============================================================
    // DATABASE
    // ============================================================

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD
        );
    }

    // ============================================================
    // COMMON HTTP HELPERS
    // ============================================================

    public static void securityHeaders(HttpExchange exchange) {

        Headers h = exchange.getResponseHeaders();

        h.set(
                "X-Content-Type-Options",
                "nosniff"
        );

        h.set(
                "X-Frame-Options",
                "DENY"
        );

        h.set(
                "Referrer-Policy",
                "strict-origin-when-cross-origin"
        );

        h.set(
                "Permissions-Policy",
                "camera=(), microphone=(), geolocation=()"
        );

        h.set(
                "Cache-Control",
                "no-store"
        );
    }

    public static boolean handleOptions(HttpExchange exchange)
            throws IOException {

        securityHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin",
                    "*"
            );

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Methods",
                    "GET,POST,PUT,OPTIONS"
            );

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Headers",
                    "Content-Type,Authorization"
            );

            exchange.sendResponseHeaders(204, -1);
            exchange.close();

            return true;
        }

        return false;
    }

    public static String readBody(HttpExchange exchange)
            throws IOException {

        try (InputStream input =
                     exchange.getRequestBody()) {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];

            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            return output.toString(
                    StandardCharsets.UTF_8
            );
        }
    }

    public static void sendJson(
            HttpExchange exchange,
            int status,
            String json) throws IOException {

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
                json.getBytes(StandardCharsets.UTF_8);

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
            String message) throws IOException {

        sendJson(
                exchange,
                status,
                "{\"success\":false,\"error\":"
                        + Json.quote(message)
                        + "}"
        );
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    static class HealthHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
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

            try (Connection ignored = getConnection()) {

                sendJson(
                        exchange,
                        200,
                        "{\"success\":true,\"database\":\"connected\"}"
                );

            } catch (Exception e) {

                sendJson(
                        exchange,
                        503,
                        "{\"success\":false,\"database\":\"unavailable\"}"
                );
            }
        }
    }

    // ============================================================
    // STUDENTS
    // ============================================================

    static class StudentsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
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

            } catch (Exception e) {

                e.printStackTrace();

                sendError(
                        exchange,
                        500,
                        "Internal server error"
                );
            }
        }

        private void createStudent(
                HttpExchange exchange) throws Exception {

            Map<String, String> body =
                    Json.parse(readBody(exchange));

            String name =
                    required(body, "name");

            String mobile =
                    required(body, "mobile_no");

            String address =
                    required(body, "address");

            String course =
                    required(body, "course");

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
                            null
                    );

            String sql =
                    "INSERT INTO student_records " +
                    "(name,mobile_no,address,course,bill," +
                    "pending_balance,date_of_admission) " +
                    "VALUES (?,?,?,?,?,?,?)";

            try (Connection conn = getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement(
                                 sql,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                stmt.setString(1, name);
                stmt.setString(2, mobile);
                stmt.setString(3, address);
                stmt.setString(4, course);
                stmt.setDouble(5, bill);
                stmt.setDouble(6, bill);
                stmt.setString(7, date);

                stmt.executeUpdate();

                long id = -1;

                try (ResultSet keys =
                             stmt.getGeneratedKeys()) {

                    if (keys.next()) {
                        id = keys.getLong(1);
                    }
                }

                sendJson(
                        exchange,
                        201,
                        "{\"success\":true,\"id\":"
                                + id
                                + "}"
                );
            }
        }

        private void updateStudent(
                HttpExchange exchange) throws Exception {

            Map<String, String> body =
                    Json.parse(readBody(exchange));

            int id =
                    Integer.parseInt(
                            required(body, "id")
                    );

            String name =
                    required(body, "name");

            String mobile =
                    required(body, "mobile_no");

            String address =
                    required(body, "address");

            String course =
                    required(body, "course");

            double newBill =
                    parseMoney(
                            required(body, "bill")
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

            try (Connection conn = getConnection()) {

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

                        stmt.setInt(1, id);

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
                                    rs.getDouble("bill");

                            pendingBalance =
                                    rs.getDouble(
                                            "pending_balance"
                                    );
                        }
                    }

                    double difference =
                            newBill - oldBill;

                    double newPending =
                            pendingBalance + difference;

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
                            "name=?,mobile_no=?,address=?," +
                            "course=?,bill=?," +
                            "pending_balance=?," +
                            "dynamic_notes=? " +
                            "WHERE id=?";

                    try (PreparedStatement stmt =
                                 conn.prepareStatement(update)) {

                        stmt.setString(1, name);
                        stmt.setString(2, mobile);
                        stmt.setString(3, address);
                        stmt.setString(4, course);
                        stmt.setDouble(5, newBill);
                        stmt.setDouble(6, newPending);
                        stmt.setString(7, notes);
                        stmt.setInt(8, id);

                        stmt.executeUpdate();
                    }

                    conn.commit();

                    sendJson(
                            exchange,
                            200,
                            "{\"success\":true}"
                    );

                } catch (Exception e) {

                    conn.rollback();

                    throw e;
                }
            }
        }
    }

    // ============================================================
    // SEARCH
    // ============================================================

    static class SearchHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
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
                                exchange.getRequestURI()
                                        .getRawQuery()
                        );

                String filter =
                        params.getOrDefault(
                                "filter",
                                ""
                        );

                String pattern =
                        "%" + filter + "%";

                String sql =
                        "SELECT id,name,mobile_no,address," +
                        "course,bill,pending_balance," +
                        "dynamic_notes,date_of_admission " +
                        "FROM student_records " +
                        "WHERE name LIKE ? " +
                        "OR mobile_no LIKE ? " +
                        "OR course LIKE ? " +
                        "OR address LIKE ? " +
                        "ORDER BY id DESC";

                StringBuilder result =
                        new StringBuilder("[ ");

                boolean first = true;

                try (Connection conn = getConnection();
                     PreparedStatement stmt =
                             conn.prepareStatement(sql)) {

                    stmt.setString(1, pattern);
                    stmt.setString(2, pattern);
                    stmt.setString(3, pattern);
                    stmt.setString(4, pattern);

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
                            ).append(id).append(",");

                            result.append(
                                    "\"name\":"
                            ).append(
                                    Json.quote(
                                            rs.getString("name")
                                    )
                            ).append(",");

                            result.append(
                                    "\"mobile_no\":"
                            ).append(
                                    Json.quote(
                                            rs.getString("mobile_no")
                                    )
                            ).append(",");

                            result.append(
                                    "\"address\":"
                            ).append(
                                    Json.quote(
                                            rs.getString("address")
                                    )
                            ).append(",");

                            result.append(
                                    "\"course\":"
                            ).append(
                                    Json.quote(
                                            rs.getString("course")
                                    )
                            ).append(",");

                            result.append(
                                    "\"bill\":"
                            ).append(
                                    money(
                                            rs.getDouble("bill")
                                    )
                            ).append(",");

                            result.append(
                                    "\"pending_balance\":"
                            ).append(
                                    money(
                                            rs.getDouble(
                                                    "pending_balance"
                                            )
                                    )
                            ).append(",");

                            result.append(
                                    "\"notes\":"
                            ).append(
                                    Json.quote(
                                            rs.getString(
                                                    "dynamic_notes"
                                            )
                                    )
                            ).append(",");

                            result.append(
                                    "\"date_of_admission\":"
                            ).append(
                                    Json.quote(
                                            rs.getString(
                                                    "date_of_admission"
                                            )
                                    )
                            ).append(",");

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

                result.append(" ]");

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

        private void appendPaymentHistory(
                Connection conn,
                int studentId,
                StringBuilder result)
                throws SQLException {

            result.append("[");

            String sql =
                    "SELECT amount_paid,payment_date,remarks " +
                    "FROM fee_transactions " +
                    "WHERE student_id=? " +
                    "ORDER BY transaction_id ASC";

            boolean first = true;

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql)) {

                stmt.setInt(1, studentId);

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
                        ).append(
                                Json.quote(date)
                        ).append(",");

                        result.append(
                                "\"remarks\":"
                        ).append(
                                Json.quote(
                                        rs.getString(
                                                "remarks"
                                        )
                                )
                        ).append(",");

                        result.append(
                                "\"amount\":"
                        ).append(
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

    static class PaymentsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
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
                        Json.parse(readBody(exchange));

                int studentId =
                        Integer.parseInt(
                                required(body, "id")
                        );

                double payment =
                        parseMoney(
                                required(body, "amount")
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

                            stmt.setInt(1, studentId);

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

                        String update =
                                "UPDATE student_records " +
                                "SET pending_balance=" +
                                "pending_balance-? " +
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

                            stmt.executeUpdate();
                        }

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

                        conn.rollback();

                        throw e;
                    }
                }

            } catch (NumberFormatException e) {

                sendError(
                        exchange,
                        400,
                        "Invalid student ID or payment amount"
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

    static class StaticFileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
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

            String path =
                    exchange.getRequestURI()
                            .getPath();

            if (path == null ||
                    path.equals("/") ||
                    path.isEmpty()) {

                path = "/index.html";
            }

            // Prevent path traversal.
            Path requested =
                    PUBLIC_DIR.resolve(
                            path.substring(
                                    path.startsWith("/")
                                            ? 1
                                            : 0
                            )
                    ).normalize();

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
                    Files.readAllBytes(requested);

            String contentType =
                    contentType(requested);

            securityHeaders(exchange);

            exchange.getResponseHeaders().set(
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

        private String contentType(Path file) {

            String name =
                    file.getFileName()
                            .toString()
                            .toLowerCase();

            if (name.endsWith(".html")) {
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

            if (name.endsWith(".svg")) {
                return "image/svg+xml";
            }

            if (name.endsWith(".ico")) {
                return "image/x-icon";
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

        String value = body.get(key);

        if (value == null ||
                value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Missing required field: " + key
            );
        }

        return value.trim();
    }

    private static double parseMoney(String value) {

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
    }

    private static String money(double value) {

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
            throws UnsupportedEncodingException {

        Map<String, String> result =
                new HashMap<>();

        if (query == null ||
                query.isEmpty()) {

            return result;
        }

        for (String pair :
                query.split("&")) {

            int equals =
                    pair.indexOf('=');

            if (equals < 0) {

                result.put(
                        URLDecoder.decode(
                                pair,
                                "UTF-8"
                        ),
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

            result.put(key, value);
        }

        return result;
    }

    // ============================================================
    // MINIMAL JSON PARSER
    // Supports strings, numbers, booleans and null.
    // ============================================================

    static class Json {

        static Map<String, String> parse(
                String input) {

            Map<String, String> result =
                    new HashMap<>();

            if (input == null) {
                return result;
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

                String key =
                        parser.readString();

                parser.skipWhitespace();

                if (parser.read() != ':') {
                    throw new IllegalArgumentException(
                            "Invalid JSON"
                    );
                }

                parser.skipWhitespace();

                String value =
                        parser.readValue();

                result.put(key, value);

                parser.skipWhitespace();

                char next =
                        parser.read();

                if (next == '}') {
                    break;
                }

                if (next != ',') {
                    throw new IllegalArgumentException(
                            "Invalid JSON"
                    );
                }
            }

            return result;
        }

        static String quote(String value) {

            if (value == null) {
                return "null";
            }

            StringBuilder result =
                    new StringBuilder("\"");

            for (char c : value.toCharArray()) {

                switch (c) {

                    case '"':
                        result.append("\\\"");
                        break;

                    case '\\':
                        result.append("\\\\");
                        break;

                    case '\b':
                        result.append("\\b");
                        break;

                    case '\f':
                        result.append("\\f");
                        break;

                    case '\n':
                        result.append("\\n");
                        break;

                    case '\r':
                        result.append("\\r");
                        break;

                    case '\t':
                        result.append("\\t");
                        break;

                    default:

                        if (c < 32) {

                            result.append(
                                    String.format(
                                            "\\u%04x",
                                            (int) c
                                    )
                            );

                        } else {

                            result.append(c);
                        }
                }
            }

            result.append("\"");

            return result.toString();
        }

        static class Parser {

            private final String input;

            private int position = 0;

            Parser(String input) {
                this.input = input.trim();
            }

            char peek() {

                if (position >= input.length()) {
                    return '\0';
                }

                return input.charAt(position);
            }

            char read() {

                if (position >= input.length()) {
                    throw new IllegalArgumentException(
                            "Unexpected end of JSON"
                    );
                }

                return input.charAt(position++);
            }

            void skipWhitespace() {

                while (position <
                        input.length() &&
                        Character.isWhitespace(
                                input.charAt(position))) {

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

                    char c = read();

                    if (c == '"') {
                        return result.toString();
                    }

                    if (c == '\\') {

                        char escaped = read();

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

                                String hex =
                                        input.substring(
                                                position,
                                                position + 4
                                        );

                                result.append(
                                        (char) Integer.parseInt(
                                                hex,
                                                16
                                        )
                                );

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

                int start = position;

                while (position <
                        input.length()) {

                    char c =
                            input.charAt(position);

                    if (c == ',' ||
                            c == '}' ||
                            Character.isWhitespace(c)) {

                        break;
                    }

                    position++;
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
