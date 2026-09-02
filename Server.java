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

    public static final String DB_URL = "jdbc:mysql://localhost:3306/pal_india_db";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "";

       public static void main(String[] args) throws IOException {
        try {
            // Explicitly force register the MySQL Driver class into the JVM memory space
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("⚠️ Warning: MySQL Driver Jar file is missing from folder location!");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/students", new StudentsHandler());
        server.createContext("/api/students/search", new SearchHandler());
        server.createContext("/api/payments", new PaymentsHandler());
        
        server.setExecutor(null);
        server.start();
        System.out.println("🚀 PAL INDIA Web Server running flawlessly on http://localhost:8080");
    }


    public static void handleCORS(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }
    }

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
// --- ENDPOINT 1: REGISTER NEW STUDENT RECORD ---
class StudentsHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        Server.handleCORS(exchange);
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String response = "{\"success\":false}";
            int statusCode = 500;
            
            try {
                String rawBody = Server.readRequestBody(exchange);
                Map<String, String> body = Server.parseJsonSimple(rawBody);
                
                String query = "INSERT INTO student_records (name, mobile_no, address, course, bill, pending_balance, date_of_admission) VALUES (?, ?, ?, ?, ?, ?, ?)";
                
                try (Connection conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement(query)) {
                    
                    String rawBill = body.getOrDefault("bill", "0");
                    double billVal = 0.0;
                    if(rawBill != null && !rawBill.isEmpty()) {
                        billVal = Double.parseDouble(rawBill);
                    }
                    
                    pstmt.setString(1, body.get("name"));
                    pstmt.setString(2, body.get("mobile_no"));
                    pstmt.setString(3, body.get("address"));
                    pstmt.setString(4, body.get("course"));
                    pstmt.setDouble(5, billVal);
                    pstmt.setDouble(6, billVal);
                    pstmt.setString(7, body.get("date_of_admission"));
                    
                    pstmt.executeUpdate();
                    response = "{\"success\":true}";
                    statusCode = 200;
                }
            } catch (Exception e) {
                System.err.println("❌ Database Processing Error: " + e.getMessage());
                e.printStackTrace();
                response = "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                statusCode = 400;
            } finally {
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                exchange.close();
            }
        }
    }
}

// --- ENDPOINT 2: SEARCH DIRECTORY MATRIX ---
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
                String query = "SELECT id, name, mobile_no, address, course, bill, pending_balance FROM student_records WHERE name LIKE ? OR course LIKE ?";
                
                try (Connection conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, "%" + filterValue + "%");
                    pstmt.setString(2, "%" + filterValue + "%");
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) jsonResult.append(",");
                            jsonResult.append(String.format(Locale.US,
                                "{\"id\":%d,\"name\":\"%s\",\"mobile_no\":\"%s\",\"address\":\"%s\",\"course\":\"%s\",\"bill\":%.2f,\"pending_balance\":%.2f}",
                                rs.getInt("id"), rs.getString("name"), rs.getString("mobile_no"), 
                                rs.getString("address"), rs.getString("course"), rs.getDouble("bill"), rs.getDouble("pending_balance")
                            ));
                            first = false;
                        }
                    }
                }
                jsonResult.append("]");
                byte[] responseBytes = jsonResult.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                exchange.close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }
}
// --- ENDPOINT 3: POST MONTHLY DEDUCTION PAYMENT ---
class PaymentsHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        Server.handleCORS(exchange);
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String response = "{\"success\":false}";
            int statusCode = 500;
            try {
                Map<String, String> body = Server.parseJsonSimple(Server.readRequestBody(exchange));
                int studentId = Integer.parseInt(body.get("student_id"));
                double amount = Double.parseDouble(body.get("amount"));
                String remarks = body.get("remarks");

                try (Connection conn = DriverManager.getConnection(Server.DB_URL, Server.DB_USER, Server.DB_PASSWORD)) {
                    conn.setAutoCommit(false);
                    try {
                        try (PreparedStatement tx = conn.prepareStatement("INSERT INTO fee_transactions (student_id, amount_paid, remarks) VALUES (?, ?, ?)")) {
                            tx.setInt(1, studentId);
                            tx.setDouble(2, amount);
                            tx.setString(3, remarks);
                            tx.executeUpdate();
                        }
                        try (PreparedStatement updateBal = conn.prepareStatement("UPDATE student_records SET pending_balance = pending_balance - ? WHERE id = ?")) {
                            updateBal.setDouble(1, amount);
                            updateBal.setInt(2, studentId);
                            updateBal.executeUpdate();
                        }
                        conn.commit();
                        response = "{\"success\":true}";
                        statusCode = 200;
                    } catch (SQLException se) {
                        conn.rollback();
                        throw se;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Payment Error: " + e.getMessage());
                response = "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
                statusCode = 400;
            } finally {
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                exchange.close();
            }
        }
    }
}

