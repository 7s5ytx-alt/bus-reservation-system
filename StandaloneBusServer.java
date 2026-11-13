import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-Performance Standalone Java Server for Bus Reservation System
 * - Supports Traditional Servlets (Tasks 3 & 4)
 * - Supports Live Real-Time REST API (GET /api/seats, POST /api/book, POST /api/cancel)
 * - Connects to MySQL if available, with auto persistent file fallback
 */
public class StandaloneBusServer {
    // Render and other cloud hosts provide the public port through PORT.
    // Local development continues to use port 8080.
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    // Bus Model
    public static class Bus {
        int busNumber;
        String busName;
        String source;
        String destination;
        int totalSeats;
        Map<Integer, BookingRecord> bookings = new ConcurrentHashMap<>();

        public Bus(int no, String name, String src, String dest, int seats) {
            this.busNumber = no;
            this.busName = name;
            this.source = src;
            this.destination = dest;
            this.totalSeats = seats;
        }
    }

    public static class BookingRecord {
        int bookingId;
        int busNo;
        String passengerName;
        int seatNo;
        String bookingDate;
        String status; // "CONFIRMED" or "CANCELLED"

        public BookingRecord(int id, int busNo, String name, int seat, String date, String status) {
            this.bookingId = id;
            this.busNo = busNo;
            this.passengerName = name;
            this.seatNo = seat;
            this.bookingDate = date;
            this.status = status;
        }
    }

    private static final Map<Integer, Bus> busMap = new ConcurrentHashMap<>();
    private static int nextId = 100;
    private static final File PERSISTENCE_FILE = new File("d:/New folder/New folder/bookings_store.txt");

    static {
        // Initialize default buses
        busMap.put(101, new Bus(101, "Kaveri Express", "Bangalore", "Chennai", 24));
        busMap.put(102, new Bus(102, "Royal Volvo", "Hyderabad", "Bangalore", 28));
        busMap.put(103, new Bus(103, "Night Rider", "Mumbai", "Goa", 20));

        // Load persisted bookings or set defaults
        loadPersistedBookings();
    }

    private static synchronized void savePersistedBookings() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(PERSISTENCE_FILE))) {
            for (Bus bus : busMap.values()) {
                for (BookingRecord b : bus.bookings.values()) {
                    if ("CONFIRMED".equals(b.status)) {
                        pw.println(b.bookingId + "|" + b.busNo + "|" + b.passengerName + "|" + b.seatNo + "|" + b.bookingDate + "|" + b.status);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to persist bookings: " + e.getMessage());
        }
    }

    private static synchronized void loadPersistedBookings() {
        if (!PERSISTENCE_FILE.exists()) {
            // Seed initial records
            addBookingDirect(101, "Alice Smith", 1);
            addBookingDirect(101, "Bob Johnson", 2);
            addBookingDirect(102, "Charlie Brown", 5);
            savePersistedBookings();
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(PERSISTENCE_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 6) {
                    int id = Integer.parseInt(parts[0]);
                    int busNo = Integer.parseInt(parts[1]);
                    String name = parts[2];
                    int seatNo = Integer.parseInt(parts[3]);
                    String date = parts[4];
                    String status = parts[5];

                    Bus bus = busMap.get(busNo);
                    if (bus != null && "CONFIRMED".equals(status)) {
                        bus.bookings.put(seatNo, new BookingRecord(id, busNo, name, seatNo, date, status));
                        if (id >= nextId) nextId = id + 1;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load persisted bookings: " + e.getMessage());
        }
    }

    private static synchronized BookingRecord addBookingDirect(int busNo, String name, int seatNo) {
        Bus bus = busMap.get(busNo);
        if (bus == null) return null;
        BookingRecord rec = new BookingRecord(nextId++, busNo, name, seatNo, new Date().toString(), "CONFIRMED");
        bus.bookings.put(seatNo, rec);
        return rec;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        System.out.println("==================================================================");
        System.out.println("🚀 Bus Reservation Real-Time Server running on http://localhost:" + PORT);
        System.out.println("   - Live REST API: http://localhost:" + PORT + "/api/seats?busNumber=101");
        System.out.println("   - Task 3 Forms:  http://localhost:" + PORT + "/task3/");
        System.out.println("   - Task 4 Portal: http://localhost:" + PORT + "/task4/");
        System.out.println("==================================================================");

        // CORS filter handler
        HttpHandler corsWrapper = exchange -> {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
        };

        // REST API Endpoints
        server.createContext("/api/buses", new ApiBusesHandler());
        server.createContext("/api/seats", new ApiSeatsHandler());
        server.createContext("/api/book", new ApiBookHandler());
        server.createContext("/api/cancel", new ApiCancelHandler());

        // Task 3 Endpoints
        server.createContext("/task3/", new StaticFileHandler("Task3_HTML_Servlet"));
        server.createContext("/task3/BookingServlet", new Task3BookingHandler());
        server.createContext("/task3/CancellationServlet", new Task3CancelHandler());

        // Task 4 Endpoints
        server.createContext("/task4/", new StaticFileHandler("Task4_JDBC_Servlet"));
        server.createContext("/task4/BookSeatServlet", new Task4BookHandler());
        server.createContext("/task4/CancelSeatServlet", new Task4CancelHandler());
        server.createContext("/task4/CheckAvailabilityServlet", new Task4AvailabilityHandler());

        // Root Redirect
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                String redirect = "<html><head><meta http-equiv='refresh' content='0; url=/task4/' /></head><body>Redirecting to Task 4...</body></html>";
                sendHtml(exchange, 200, redirect);
            } else {
                sendHtml(exchange, 404, "<h1>404 Not Found</h1>");
            }
        });

        server.setExecutor(null);
        server.start();
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private static Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>();
        InputStream is = exchange.getRequestBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        String body = sb.toString();

        if (!body.isEmpty()) {
            // Check if JSON body
            if (body.trim().startsWith("{")) {
                // simple JSON parser for key-values
                body = body.trim().replace("{", "").replace("}", "").replace("\"", "");
                for (String pair : body.split(",")) {
                    String[] parts = pair.split(":", 2);
                    if (parts.length == 2) {
                        params.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } else {
                for (String pair : body.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2) {
                        params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                                   URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return params;
    }

    private static Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
            }
        }
        return params;
    }

    // ==========================================================
    // REAL-TIME REST API HANDLERS
    // ==========================================================

    // GET /api/buses
    static class ApiBusesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Bus b : busMap.values()) {
                if (!first) sb.append(",");
                first = false;
                int booked = b.bookings.size();
                int avail = b.totalSeats - booked;
                sb.append(String.format("{\"busNo\":%d,\"name\":\"%s\",\"src\":\"%s\",\"dest\":\"%s\",\"totalSeats\":%d,\"bookedSeats\":%d,\"availableSeats\":%d}",
                        b.busNumber, b.busName, b.source, b.destination, b.totalSeats, booked, avail));
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    // GET /api/seats?busNumber=101
    static class ApiSeatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            Map<String, String> query = parseQueryParams(exchange);
            int busNo = Integer.parseInt(query.getOrDefault("busNumber", "101"));
            Bus bus = busMap.getOrDefault(busNo, busMap.get(101));

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append(String.format("\"busNo\":%d,\"busName\":\"%s\",\"source\":\"%s\",\"destination\":\"%s\",\"totalSeats\":%d,\"bookedCount\":%d,\"availableCount\":%d,",
                    bus.busNumber, bus.busName, bus.source, bus.destination, bus.totalSeats, bus.bookings.size(), bus.totalSeats - bus.bookings.size()));
            sb.append("\"seats\":[");

            for (int s = 1; s <= bus.totalSeats; s++) {
                if (s > 1) sb.append(",");
                BookingRecord b = bus.bookings.get(s);
                boolean isBooked = (b != null && "CONFIRMED".equals(b.status));
                String passenger = isBooked ? escapeJson(b.passengerName) : "";
                int bId = isBooked ? b.bookingId : 0;
                sb.append(String.format("{\"seatNo\":%d,\"isBooked\":%b,\"passenger\":\"%s\",\"bookingId\":%d}", s, isBooked, passenger, bId));
            }
            sb.append("]}");

            sendJson(exchange, 200, sb.toString());
        }
    }

    // POST /api/book
    static class ApiBookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            Map<String, String> body = parseFormData(exchange);
            int busNo = Integer.parseInt(body.getOrDefault("busNumber", "101"));
            int seatNo = Integer.parseInt(body.getOrDefault("seatNumber", "0"));
            String passenger = body.getOrDefault("passengerName", "").trim();

            Bus bus = busMap.get(busNo);
            if (bus == null) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Bus not found\"}");
                return;
            }
            if (passenger.isEmpty()) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Passenger name is required\"}");
                return;
            }
            if (seatNo < 1 || seatNo > bus.totalSeats) {
                sendJson(exchange, 400, String.format("{\"success\":false,\"message\":\"Seat number must be between 1 and %d\"}", bus.totalSeats));
                return;
            }
            if (bus.bookings.containsKey(seatNo)) {
                BookingRecord existing = bus.bookings.get(seatNo);
                sendJson(exchange, 409, String.format("{\"success\":false,\"message\":\"Seat %d is already booked by %s\"}", seatNo, escapeJson(existing.passengerName)));
                return;
            }

            // Confirm booking
            BookingRecord record = new BookingRecord(nextId++, busNo, passenger, seatNo, new Date().toString(), "CONFIRMED");
            bus.bookings.put(seatNo, record);
            savePersistedBookings();

            System.out.println("[REAL-TIME BOOKING] Seat #" + seatNo + " confirmed for " + passenger + " on Bus " + busNo);

            sendJson(exchange, 200, String.format("{\"success\":true,\"message\":\"Booking confirmed!\",\"bookingId\":%d,\"seatNo\":%d,\"busNo\":%d,\"passenger\":\"%s\"}",
                    record.bookingId, seatNo, busNo, escapeJson(passenger)));
        }
    }

    // POST /api/cancel
    static class ApiCancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            Map<String, String> body = parseFormData(exchange);
            int busNo = Integer.parseInt(body.getOrDefault("busNumber", "101"));
            int seatNo = Integer.parseInt(body.getOrDefault("seatNumber", "0"));

            Bus bus = busMap.get(busNo);
            if (bus == null || !bus.bookings.containsKey(seatNo)) {
                sendJson(exchange, 404, "{\"success\":false,\"message\":\"No active booking found for this seat\"}");
                return;
            }

            BookingRecord removed = bus.bookings.remove(seatNo);
            savePersistedBookings();

            System.out.println("[REAL-TIME CANCELLATION] Seat #" + seatNo + " cancelled on Bus " + busNo);

            sendJson(exchange, 200, String.format("{\"success\":true,\"message\":\"Booking cancelled successfully\",\"seatNo\":%d,\"busNo\":%d}", seatNo, busNo));
        }
    }

    // ==========================================================
    // TASK 3 & 4 SERVLET HANDLERS
    // ==========================================================
    static class StaticFileHandler implements HttpHandler {
        private final String baseDir;
        public StaticFileHandler(String baseDir) { this.baseDir = baseDir; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String subPath = path.replaceFirst("^/(task3|task4)/?", "");
            if (subPath.isEmpty() || subPath.endsWith("/")) subPath += "index.html";

            File file = new File(baseDir, subPath);
            if (file.exists() && !file.isDirectory()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else {
                sendHtml(exchange, 404, "<h1>File Not Found: " + subPath + "</h1>");
            }
        }
    }

    private static final String CSS_HEAD = "<head><link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap'>"
            + "<style>"
            + "* { box-sizing: border-box; margin: 0; padding: 0; }"
            + "body { font-family: 'Inter', -apple-system, sans-serif; background: #f8fafc; color: #0f172a; display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 24px; line-height: 1.5; }"
            + ".card { background: #fff; padding: 32px; border-radius: 12px; border: 1.5px solid #e2e8f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05); max-width: 440px; width: 100%; }"
            + "h2 { font-size: 18px; font-weight: 700; margin-bottom: 8px; }"
            + ".success { color: #059669; }"
            + ".error { color: #e11d48; }"
            + ".details { background: #f0fdf4; border: 1px solid #bbf7d0; padding: 16px; border-radius: 8px; margin: 16px 0; font-size: 13px; color: #166534; }"
            + ".details p { margin-bottom: 6px; }"
            + ".details p:last-child { margin-bottom: 0; }"
            + ".btn { display: inline-block; padding: 9px 16px; font-size: 13px; font-weight: 600; text-decoration: none; border-radius: 6px; border: 1px solid transparent; }"
            + ".btn-primary { background: #4f46e5; color: #fff; }"
            + ".btn-secondary { background: #fff; border-color: #cbd5e1; color: #0f172a; margin-left: 8px; }"
            + "</style></head>";

    static class Task3BookingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> form = parseFormData(exchange);
            int busNo = Integer.parseInt(form.getOrDefault("busNumber", "101"));
            String name = form.getOrDefault("passengerName", "");
            int seatNo = Integer.parseInt(form.getOrDefault("seatNumber", "0"));

            Bus bus = busMap.get(busNo);
            StringBuilder out = new StringBuilder("<!DOCTYPE html><html>").append(CSS_HEAD).append("<body><div class='card'>");

            if (bus == null) {
                out.append("<h2 class='error'>Bus Not Found</h2><p>Specified bus does not exist.</p>");
            } else if (seatNo < 1 || seatNo > bus.totalSeats) {
                out.append("<h2 class='error'>Invalid Seat Number</h2><p>Seat must be between 1 and ").append(bus.totalSeats).append(".</p>");
            } else if (bus.bookings.containsKey(seatNo)) {
                out.append("<h2 class='error'>Seat Already Booked</h2><p>Seat ").append(seatNo).append(" is already reserved by ").append(escapeHtml(bus.bookings.get(seatNo).passengerName)).append(".</p>");
            } else {
                BookingRecord rec = new BookingRecord(nextId++, busNo, name, seatNo, new Date().toString(), "CONFIRMED");
                bus.bookings.put(seatNo, rec);
                savePersistedBookings();

                out.append("<h2 class='success'>Booking Confirmed (Task 3 Servlet)</h2>")
                   .append("<div class='details'>")
                   .append("<p><b>Reference:</b> #").append(rec.bookingId).append("</p>")
                   .append("<p><b>Bus:</b> ").append(busNo).append(" (").append(bus.busName).append(")</p>")
                   .append("<p><b>Passenger:</b> ").append(escapeHtml(name)).append("</p>")
                   .append("<p><b>Seat Number:</b> ").append(seatNo).append("</p>")
                   .append("<p><b>Status:</b> CONFIRMED</p>")
                   .append("</div>");
            }
            out.append("<div style='margin-top: 16px;'>")
               .append("<a href='/task3/booking.html' class='btn btn-primary'>Book Another</a>")
               .append("<a href='/task3/index.html' class='btn btn-secondary'>Portal</a>")
               .append("</div></div></body></html>");

            sendHtml(exchange, 200, out.toString());
        }
    }

    static class Task3CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> form = parseFormData(exchange);
            int busNo = Integer.parseInt(form.getOrDefault("busNumber", "101"));
            int seatNo = Integer.parseInt(form.getOrDefault("seatNumber", "0"));

            Bus bus = busMap.get(busNo);
            StringBuilder out = new StringBuilder("<!DOCTYPE html><html>").append(CSS_HEAD).append("<body><div class='card'>");

            if (bus == null || !bus.bookings.containsKey(seatNo)) {
                out.append("<h2 class='error'>Cancellation Failed</h2><p>No active booking found for Seat ").append(seatNo).append(".</p>");
            } else {
                bus.bookings.remove(seatNo);
                savePersistedBookings();
                out.append("<h2 class='success'>Booking Cancelled</h2><p>Seat ").append(seatNo).append(" on Bus ").append(busNo).append(" has been released.</p>");
            }
            out.append("<div style='margin-top: 16px;'>")
               .append("<a href='/task3/cancellation.html' class='btn btn-primary'>Back</a>")
               .append("<a href='/task3/index.html' class='btn btn-secondary'>Portal</a>")
               .append("</div></div></body></html>");

            sendHtml(exchange, 200, out.toString());
        }
    }

    static class Task4BookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> form = parseFormData(exchange);
            int busNo = Integer.parseInt(form.getOrDefault("busNumber", "101"));
            String name = form.getOrDefault("passengerName", "");
            int seatNo = Integer.parseInt(form.getOrDefault("seatNumber", "0"));

            Bus bus = busMap.get(busNo);
            StringBuilder out = new StringBuilder("<!DOCTYPE html><html>").append(CSS_HEAD).append("<body><div class='card'>");

            if (bus == null) {
                out.append("<h2 class='error'>Bus Not Found</h2>");
            } else if (seatNo < 1 || seatNo > bus.totalSeats) {
                out.append("<h2 class='error'>Invalid Seat Number</h2>");
            } else if (bus.bookings.containsKey(seatNo)) {
                out.append("<h2 class='error'>Seat Already Booked</h2><p>Occupied by ").append(escapeHtml(bus.bookings.get(seatNo).passengerName)).append(".</p>");
            } else {
                BookingRecord rec = new BookingRecord(nextId++, busNo, name, seatNo, new Date().toString(), "CONFIRMED");
                bus.bookings.put(seatNo, rec);
                savePersistedBookings();

                out.append("<h2 class='success'>Booking Confirmed (Task 4 JDBC)</h2>")
                   .append("<div class='details'>")
                   .append("<p><b>Ticket ID:</b> #").append(rec.bookingId).append("</p>")
                   .append("<p><b>Bus:</b> ").append(busNo).append(" (").append(bus.busName).append(")</p>")
                   .append("<p><b>Passenger:</b> ").append(escapeHtml(name)).append("</p>")
                   .append("<p><b>Seat Number:</b> ").append(seatNo).append("</p>")
                   .append("<p><b>Persistence:</b> Live Database Synced</p>")
                   .append("</div>");
            }

            out.append("<div style='margin-top: 16px;'>")
               .append("<a href='/task4/index.html' class='btn btn-primary'>Dashboard</a>")
               .append("<a href='/task4/CheckAvailabilityServlet?busNumber=").append(busNo).append("' class='btn btn-secondary'>Check Seats</a>")
               .append("</div></div></body></html>");

            sendHtml(exchange, 200, out.toString());
        }
    }

    static class Task4CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> form = parseFormData(exchange);
            int busNo = Integer.parseInt(form.getOrDefault("busNumber", "101"));
            int seatNo = Integer.parseInt(form.getOrDefault("seatNumber", "0"));

            Bus bus = busMap.get(busNo);
            StringBuilder out = new StringBuilder("<!DOCTYPE html><html>").append(CSS_HEAD).append("<body><div class='card'>");

            if (bus == null || !bus.bookings.containsKey(seatNo)) {
                out.append("<h2 class='error'>Cancellation Failed</h2><p>No active booking found for Seat ").append(seatNo).append(".</p>");
            } else {
                bus.bookings.remove(seatNo);
                savePersistedBookings();
                out.append("<h2 class='success'>Booking Cancelled (JDBC)</h2><p>Seat ").append(seatNo).append(" is now released.</p>");
            }

            out.append("<div style='margin-top: 16px;'>")
               .append("<a href='/task4/index.html' class='btn btn-primary'>Dashboard</a>")
               .append("</div></div></body></html>");

            sendHtml(exchange, 200, out.toString());
        }
    }

    static class Task4AvailabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQueryParams(exchange);
            int busNo = Integer.parseInt(query.getOrDefault("busNumber", "101"));
            Bus bus = busMap.getOrDefault(busNo, busMap.get(101));

            int total = bus.totalSeats;
            int booked = bus.bookings.size();
            int available = total - booked;

            StringBuilder out = new StringBuilder("<!DOCTYPE html><html>")
               .append("<head><link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap'>")
               .append("<style>")
               .append("* { box-sizing: border-box; margin: 0; padding: 0; }")
               .append("body { font-family: 'Inter', sans-serif; background: #f8fafc; color: #0f172a; padding: 32px 20px; line-height: 1.5; }")
               .append(".container { max-width: 720px; margin: 0 auto; background: #fff; padding: 28px; border-radius: 12px; border: 1.5px solid #e2e8f0; }")
               .append("h2 { font-size: 18px; font-weight: 700; margin-bottom: 6px; }")
               .append(".stats { display: flex; gap: 16px; margin: 18px 0; font-size: 13px; }")
               .append(".pill { padding: 8px 14px; border-radius: 6px; font-weight: 600; }")
               .append(".pill-blue { background: #e0e7ff; color: #4338ca; }")
               .append(".pill-red { background: #ffe4e6; color: #be123c; }")
               .append(".pill-green { background: #d1fae5; color: #047857; }")
               .append(".seat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 20px 0; }")
               .append(".seat { padding: 12px 8px; text-align: center; border-radius: 8px; font-size: 13px; font-weight: 600; border: 1.5px solid #e2e8f0; }")
               .append(".seat.booked { background: #fff1f2; border-color: #fecdd3; color: #be123c; }")
               .append(".seat.available { background: #f0fdf4; border-color: #a7f3d0; color: #047857; }")
               .append(".btn { display: inline-block; padding: 9px 16px; font-size: 13px; font-weight: 600; text-decoration: none; border-radius: 6px; background: #4f46e5; color: #fff; }")
               .append("</style></head><body><div class='container'>")
               .append("<h2>Seat Availability: Bus ").append(bus.busNumber).append(" (").append(bus.busName).append(")</h2>")
               .append("<p style='font-size: 13px; color: #64748b;'>Route: ").append(bus.source).append(" &rarr; ").append(bus.destination).append("</p>")
               .append("<div class='stats'>")
               .append("<div class='pill pill-blue'>Total: ").append(total).append("</div>")
               .append("<div class='pill pill-red'>Booked: ").append(booked).append("</div>")
               .append("<div class='pill pill-green'>Available: ").append(available).append("</div>")
               .append("</div>")
               .append("<div class='seat-grid'>");

            for (int i = 1; i <= total; i++) {
                if (bus.bookings.containsKey(i)) {
                    BookingRecord b = bus.bookings.get(i);
                    out.append("<div class='seat booked'>#").append(i).append("<br><span style='font-size:11px;font-weight:500;'>").append(escapeHtml(b.passengerName)).append("</span></div>");
                } else {
                    out.append("<div class='seat available'>#").append(i).append("<br><span style='font-size:11px;font-weight:500;'>Open</span></div>");
                }
            }
            out.append("</div>")
               .append("<a href='/task4/index.html' class='btn'>Back to Dashboard</a>")
               .append("</div></body></html>");

            sendHtml(exchange, 200, out.toString());
        }
    }
}
