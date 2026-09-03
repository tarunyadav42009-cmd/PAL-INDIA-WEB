import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class Server {

    // Target MySQL Connection URL Parameters matching your original setup context
    public static final String DB_URL = "jdbc:mysql://localhost:3306/pal_india_db";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "";

    public static void main(String[] args) throws IOException {
        try {
            // Explicitly force register the MySQL Driver class into JVM memory space
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("⚠️ Warning: MySQL Driver Jar file is missing from folder location!");
        }

        // Initialize HttpServer instance listening on Port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Operational endpoints mapping pathway triggers to custom handler classes
        server.createContext("/api/students", new StudentsHandler());
        server.createContext("/api/students/search", new SearchHandler());
        server.createContext("/api/payments", new PaymentsHandler());
        
        server.setExecutor(null);
        server.start();
        System.out.println("🚀 PAL INDIA Web Server running flawlessly on http://localhost:8080");
    }

    // Globally shared method handling cross-origin infrastructure pipeline requests
    public static void handleCORS(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }
    }

    // Reader buffer script ensuring complete payload ingest cycles
    public static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            bos.write(buffer, 0, length);
        }
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    // Custom non-dependency text string splitter to build structured data key-value maps
    public static Map<String, String> parseJsonSimple(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            if (kv.length >= 2) {
                String key = kv[0].trim().replace("\"", "");
                String val = kv[1].trim().replace("\"", "");
                map.put(key, val);
            }
        }
        return map;
    }
}
// --- COMPONENT HANDLER 1: EXECUTES REGISTER ENTRIES (POST) & RECORD MODIFICATIONS (PUT) ---
class StudentsHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        Server.handleCORS(exchange);
        String method = exchange.getRequestMethod();
        String response = "{\"success\":false}";
        int statusCode = 500;

        // SUB-ROUTINE A: ADMISSIONS REGISTER ENGINE (MAPPED TO PART 1 JDIALOG ADMISSIONS)
        if ("POST".equalsIgnoreCase(method)) {
            try {
                String rawBody = Server.readRequestBody(exchange);
                Map<String, String> body = Server.parseJsonSimple(rawBody);
                String query = "INSERT INTO student_records (name, mobile_no, address, course, bill, pending_balance, date_of_admission) VALUES (?, ?, ?, ?, ?, ?, ?)";
                
                try (Connection conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement(query)) {
                    String rawBill = body.getOrDefault("bill", "0");
                    double billVal = !rawBill.isEmpty() ? Double.parseDouble(rawBill) : 0.0;
                    
                    pstmt.setString(1, body.get("name"));
                    pstmt.setString(2, body.get("mobile_no"));
                    pstmt.setString(3, body.get("address"));
                    pstmt.setString(4, body.get("course"));
                    pstmt.setDouble(5, billVal);
                    pstmt.setDouble(6, billVal); // Set initial outstanding ledger figures to target bill cost
                    pstmt.setString(7, body.get("date_of_admission"));
                    
                    pstmt.executeUpdate();
                    response = "{\"success\":true}";
                    statusCode = 200;
                }
            } catch (Exception e) {
                response = "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                statusCode = 400;
            }
        } 
        // SUB-ROUTINE B: GLOBAL PROFILE UPDATE MANAGER (MAPPED TO PART 5 MODIFICATION CODES)
        else if ("PUT".equalsIgnoreCase(method)) {
            try {
                String rawBody = Server.readRequestBody(exchange);
                Map<String, String> body = Server.parseJsonSimple(rawBody);

                int studentId = Integer.parseInt(body.get("id"));
                String newName = body.get("name");
                String newMobile = body.get("mobile_no");
                String newAddress = body.get("address");
                String newCourse = body.get("course");
                double newBill = Double.parseDouble(body.get("bill"));
                String notes = body.get("notes");

                try (Connection conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD)) {
                    // Pull current baseline statement details to solve calculation adjustments
                    double oldBill = 0.0;
                    try (PreparedStatement checkStmt = conn.prepareStatement("SELECT bill FROM student_records WHERE id = ?")) {
                        checkStmt.setInt(1, studentId);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next()) { oldBill = rs.getDouble("bill"); }
                        }
                    }

                    // Mathematical variance block tracking debt metrics matching your Part 5 logic
                    double billDiff = newBill - oldBill;

                    String upSQL = "UPDATE student_records SET name=?, mobile_no=?, address=?, course=?, bill=?, pending_balance = pending_balance + ?, dynamic_notes=? WHERE id=?";
                    try (PreparedStatement pstmt = conn.prepareStatement(upSQL)) {
                        pstmt.setString(1, newName);
                        pstmt.setString(2, newMobile);
                        pstmt.setString(3, newAddress);
                        pstmt.setString(4, newCourse);
                        pstmt.setDouble(5, newBill);
                        pstmt.setDouble(6, billDiff);
                        pstmt.setString(7, notes);
                        pstmt.setInt(8, studentId);
                        
                        pstmt.executeUpdate();
                        response = "{\"success\":true}";
                        statusCode = 200;
                    }
                }
            } catch (Exception e) {
                response = "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                statusCode = 400;
            }
        }

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
        exchange.close();
    }
}
// --- COMPONENT HANDLER 2: SEARCH REGISTER ARCHIVES BY MULTIPLE FIELDS (GET ROUTE) ---
class SearchHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        Server.handleCORS(exchange);
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                String queryParam = exchange.getRequestURI().getQuery();
                String filterValue = "";
                if (queryParam != null && queryParam.contains("filter=")) {
                    filterValue = URLDecoder.decode(queryParam.split("filter=")[1], StandardCharsets.UTF_8.name());
                }
                
                StringBuilder jsonResult = new StringBuilder("[");
                // Core selective filter trace matching criteria parameters from Part 4
                String query = "SELECT id, name, mobile_no, address, course, bill, pending_balance, dynamic_notes FROM student_records WHERE name LIKE ? OR course LIKE ? OR address LIKE ? ORDER BY id DESC";
                
                try (Connection conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement(query)) {
                    String pattern = "%" + filterValue + "%";
                    pstmt.setString(1, pattern);
                    pstmt.setString(2, pattern);
                    pstmt.setString(3, pattern);
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) jsonResult.append(",");
                            
                            // Load recent transaction arrays associated with this student's index
                            StringBuilder historyJson = new StringBuilder("[");
                            try (PreparedStatement txP = conn.prepareStatement("SELECT amount_paid, payment_date, remarks FROM fee_transactions WHERE student_id=? ORDER BY transaction_id ASC")) {
                                txP.setInt(1, rs.getInt("id"));
                                try (ResultSet txRs = txP.executeQuery()) {
                                    boolean fTx = true;
                                    while (txRs.next()) {
                                        if (!fTx) historyJson.append(",");
                                        historyJson.append(String.format(Locale.US, "{\"date\":\"%s\",\"remarks\":\"%s\",\"amount\":%.2f}",
                                            txRs.getTimestamp("payment_date").toString().substring(0, 16), txRs.getString("remarks"), txRs.getDouble("amount_paid")));
                                        fTx = false;
                                    }
                                }
                            }
                            historyJson.append("]");

                            String notes = rs.getString("dynamic_notes") == null ? "" : rs.getString("dynamic_notes");

                            jsonResult.append(String.format(Locale.US,
                                "{\"id\":%d,\"name\":\"%s\",\"mobile_no\":\"%s\",\"address\":\"%s\",\"course\":\"%s\",\"bill\":%.2f,\"pending_balance\":%.2f,\"notes\":\"%s\",\"history\":%s}",
                                rs.getInt("id"), rs.getString("name"), rs.getString("mobile_no"), 
                                rs.getString("address"), rs.getString("course"), rs.getDouble("bill"), rs.getDouble("pending_balance"), notes, historyJson.toString()
                            ));
                            first = false;
                        }
                    }
                }
                jsonResult.append("]");
                byte[] responseBytes = jsonResult.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally { exchange.close(); }
        }
    }
}

// --- COMPONENT HANDLER 3: RECORDS MONTHLY FEES ACCOUNT TRANSACTIONS (POST ROUTE) ---
class PaymentsHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        Server.handleCORS(exchange);
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String response = "{\"success\":false}";
            int statusCode = 500;
            Connection conn = null;
            try {
                String rawBody = Server.readRequestBody(exchange);
                Map<String, String> body = Server.parseJsonSimple(rawBody);
                
                int studentId = Integer.parseInt(body.get("id"));
                double paymentAmount = Double.parseDouble(body.get("amount"));
                String remarks = body.getOrDefault("remarks", "Installment Entry");

                conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD);
                conn.setAutoCommit(false); // Enable strict ACID tracking parameters

                // Task A: Deduct pending figures value from profile record row
                String updateQuery = "UPDATE student_records SET pending_balance = pending_balance - ? WHERE id = ?";
                try (PreparedStatement pstmt1 = conn.prepareStatement(updateQuery)) {
                    pstmt1.setDouble(1, paymentAmount);
                    pstmt1.setInt(2, studentId);
                    pstmt1.executeUpdate();
                }

                // Task B: Insert row tracking point history into fee_transactions table matching Part 4
                String insertTxQuery = "INSERT INTO fee_transactions (student_id, amount_paid, remarks) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt2 = conn.prepareStatement(insertTxQuery)) {
                    pstmt2.setInt(1, studentId);
                    pstmt2.setDouble(2, paymentAmount);
                    pstmt2.setString(3, remarks);
                    pstmt2.executeUpdate();
                }

                conn.commit();
                response = "{\"success\":true}";
                statusCode = 200;
            } catch (Exception e) {
                if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
                response = "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                statusCode = 400;
            } finally {
                if (conn != null) { try { conn.close(); } catch (SQLException ex) { ex.printStackTrace(); } }
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
                exchange.close();
            }
        }
    }
}
