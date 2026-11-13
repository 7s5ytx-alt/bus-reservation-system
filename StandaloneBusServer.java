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
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-Performance Standalone Java Server for Bus Reservation System
 * - Supports Traditional Servlets (Tasks 3 & 4)
 * - Supports Live Real-Time REST API (GET /api/seats, POST /api/book, POST /api/cancel)
 * - Connects to MySQL when BUS_DB_URL, BUS_DB_USER and BUS_DB_PASSWORD are configured
 */
public class StandaloneBusServer {
    // Tomcat uses the public PORT. When deployed behind Tomcat this server runs
    // privately on INTERNAL_PORT and the Tomcat proxy servlet forwards /api/*.
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault(
        "INTERNAL_PORT", System.getenv().getOrDefault("PORT", "8080")
    ));

    // Bus Model
    public static class Bus {
        int busNumber;
        String busName;
        String source;
        String destination;
        int totalSeats;
        String agencyName;
        String driverName;
        String serviceType;
        String departureTime;
        String arrivalTime;
        String duration;
        int fare;
        volatile Double latitude;
        volatile Double longitude;
        volatile Double locationAccuracy;
        volatile long locationUpdatedAt;
        Map<Integer, BookingRecord> bookings = new ConcurrentHashMap<>();

        public Bus(int no, String name, String src, String dest, int seats) {
            this(no, name, src, dest, seats, "Triply Partner", "Driver not assigned", "AC Sleeper", "21:00", "06:00", "9h 00m", 999);
        }

        public Bus(int no, String name, String src, String dest, int seats, String agency, String driver, String type, String departure, String arrival, String travelDuration, int fare) {
            this.busNumber = no;
            this.busName = name;
            this.source = src;
            this.destination = dest;
            this.totalSeats = seats;
            this.agencyName = agency;
            this.driverName = driver;
            this.serviceType = type;
            this.departureTime = departure;
            this.arrivalTime = arrival;
            this.duration = travelDuration;
            this.fare = fare;
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
    private static final Map<String, Integer> driverSessions = new ConcurrentHashMap<>();
    private static int nextId = 100;
    private static final boolean DATABASE_CONFIGURED =
            !System.getenv().getOrDefault("BUS_DB_URL", "").isBlank()
            && !System.getenv().getOrDefault("BUS_DB_USER", "").isBlank()
            && !System.getenv().getOrDefault("BUS_DB_PASSWORD", "").isBlank();
    private static volatile boolean databaseConnected = false;
    // Local development can override this path; cloud hosts use the working
    // directory. For durable cloud reservations, use the JDBC database module.
    private static final File PERSISTENCE_FILE = new File(System.getenv().getOrDefault("BOOKINGS_FILE", "bookings_store.txt"));

    static {
        // Initialize default buses
        busMap.put(101, new Bus(101, "Kaveri Express", "Bangalore", "Chennai", 24, "Kaveri Travels", "R. Kumar", "AC Sleeper", "21:30", "05:45", "8h 15m", 899));
        busMap.put(102, new Bus(102, "Royal Volvo", "Hyderabad", "Bangalore", 28, "Royal Roadways", "S. Reddy", "Volvo Multi-Axle", "22:00", "06:30", "8h 30m", 1199));
        busMap.put(103, new Bus(103, "Night Rider", "Mumbai", "Goa", 20, "Konkan Mobility", "A. Naik", "AC Sleeper", "20:45", "07:15", "10h 30m", 1049));
        busMap.put(104, new Bus(104, "Tamil Nadu Express", "Chennai", "Coimbatore", 32, "Tamil Nadu Travels", "V. Prakash", "AC Seater Sleeper", "22:15", "06:30", "8h 15m", 749));
        busMap.put(105, new Bus(105, "Deccan Sleeper", "Bangalore", "Hyderabad", 30, "Deccan Lines", "M. Irfan", "AC Sleeper", "21:00", "05:30", "8h 30m", 1099));
        busMap.put(106, new Bus(106, "Pink City Volvo", "Delhi", "Jaipur", 36, "Rajasthan Transit", "P. Singh", "Volvo AC Seater", "06:15", "12:00", "5h 45m", 599));
        busMap.put(107, new Bus(107, "Western Ghats Travels", "Pune", "Mumbai", 34, "Western Ghats", "N. Patil", "AC Seater", "07:30", "11:15", "3h 45m", 499));
        busMap.put(108, new Bus(108, "Coastal Connect", "Vijayawada", "Hyderabad", 30, "Coastal Connect", "K. Rao", "AC Sleeper", "22:30", "04:45", "6h 15m", 699));

        // MySQL is the live source of truth when all database values are configured.
        // File persistence remains available for local preview without a database.
        if (DATABASE_CONFIGURED) {
            databaseConnected = initializeDatabase();
        }
        if (!databaseConnected) {
            loadPersistedBookings();
            System.out.println("[PERSISTENCE] File mode active. Configure BUS_DB_URL, BUS_DB_USER, and BUS_DB_PASSWORD for MySQL.");
        }
    }

    private static Connection databaseConnection() throws SQLException {
        return DriverManager.getConnection(
                System.getenv("BUS_DB_URL"),
                System.getenv("BUS_DB_USER"),
                System.getenv("BUS_DB_PASSWORD")
        );
    }

    private static boolean initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = databaseConnection()) {
                ensureDatabaseBuses(connection);
                loadDatabaseBookings(connection);
            }
            System.out.println("[DATABASE] MySQL connected. Live bookings are stored in bus_reservation_db.");
            return true;
        } catch (Exception exception) {
            System.err.println("[DATABASE] MySQL configuration was supplied but the connection failed: " + exception.getMessage());
            return false;
        }
    }

    private static void ensureDatabaseBuses(Connection connection) throws SQLException {
        String sql = "INSERT INTO bus (bus_no, bus_name, source, destination, total_seats) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE bus_name=VALUES(bus_name), source=VALUES(source), "
                + "destination=VALUES(destination), total_seats=VALUES(total_seats)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Bus bus : busMap.values()) {
                statement.setInt(1, bus.busNumber);
                statement.setString(2, bus.busName);
                statement.setString(3, bus.source);
                statement.setString(4, bus.destination);
                statement.setInt(5, bus.totalSeats);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void loadDatabaseBookings(Connection connection) throws SQLException {
        for (Bus bus : busMap.values()) bus.bookings.clear();
        String sql = "SELECT booking_id, bus_no, passenger_name, seat_no, booking_date, status "
                + "FROM booking WHERE status = 'CONFIRMED' ORDER BY booking_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                int bookingId = results.getInt("booking_id");
                int busNo = results.getInt("bus_no");
                Bus bus = busMap.get(busNo);
                if (bus != null) {
                    bus.bookings.put(results.getInt("seat_no"), new BookingRecord(
                            bookingId, busNo, results.getString("passenger_name"),
                            results.getInt("seat_no"), String.valueOf(results.getTimestamp("booking_date")),
                            results.getString("status")));
                    if (bookingId >= nextId) nextId = bookingId + 1;
                }
            }
        }
    }

    private static synchronized BookingRecord createBooking(int busNo, String passenger, int seatNo) throws SQLException {
        Bus bus = busMap.get(busNo);
        if (bus == null || seatNo < 1 || seatNo > bus.totalSeats || passenger.isBlank()) return null;

        if (!databaseConnected) {
            if (bus.bookings.containsKey(seatNo)) return null;
            BookingRecord record = new BookingRecord(nextId++, busNo, passenger, seatNo, new Date().toString(), "CONFIRMED");
            bus.bookings.put(seatNo, record);
            savePersistedBookings();
            return record;
        }

        String sql = "INSERT INTO booking (bus_no, passenger_name, seat_no, status) VALUES (?, ?, ?, 'CONFIRMED')";
        try (Connection connection = databaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, busNo);
            statement.setString(2, passenger);
            statement.setInt(3, seatNo);
            statement.executeUpdate();
            int bookingId = nextId++;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) bookingId = keys.getInt(1);
            }
            BookingRecord record = new BookingRecord(bookingId, busNo, passenger, seatNo, new Date().toString(), "CONFIRMED");
            bus.bookings.put(seatNo, record);
            return record;
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState())) return null;
            throw exception;
        }
    }

    private static synchronized boolean cancelBooking(int busNo, int seatNo) throws SQLException {
        Bus bus = busMap.get(busNo);
        if (bus == null) return false;
        if (!databaseConnected) {
            if (bus.bookings.remove(seatNo) == null) return false;
            savePersistedBookings();
            return true;
        }
        try (Connection connection = databaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE booking SET status = 'CANCELLED' WHERE bus_no = ? AND seat_no = ? AND status = 'CONFIRMED'")) {
            statement.setInt(1, busNo);
            statement.setInt(2, seatNo);
            if (statement.executeUpdate() == 0) return false;
            bus.bookings.remove(seatNo);
            return true;
        }
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
        server.createContext("/api/admin/buses", new AdminBusesHandler());
        server.createContext("/api/track", new ApiTrackHandler());
        server.createContext("/api/driver/login", new DriverLoginHandler());
        server.createContext("/api/driver/location", new DriverLocationHandler());
        server.createContext("/api/health", new ApiHealthHandler());

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
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Admin-Key, X-Driver-Key");
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

    private static boolean isAdminRequest(HttpExchange exchange) {
        String configuredKey = System.getenv("ADMIN_KEY");
        return configuredKey != null && !configuredKey.isBlank()
                && configuredKey.equals(exchange.getRequestHeaders().getFirst("X-Admin-Key"));
    }

    private static Integer driverBusFromRequest(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        return driverSessions.get(authorization.substring("Bearer ".length()).trim());
    }

    private static String busJson(Bus b) {
        int booked = b.bookings.size();
        return String.format(Locale.ROOT,
                "{\"busNo\":%d,\"name\":\"%s\",\"src\":\"%s\",\"dest\":\"%s\",\"totalSeats\":%d,\"bookedSeats\":%d,\"availableSeats\":%d,\"agencyName\":\"%s\",\"driverName\":\"%s\",\"serviceType\":\"%s\",\"departure\":\"%s\",\"arrival\":\"%s\",\"duration\":\"%s\",\"fare\":%d,\"trackingAvailable\":%s}",
                b.busNumber, escapeJson(b.busName), escapeJson(b.source), escapeJson(b.destination), b.totalSeats, booked, b.totalSeats - booked,
                escapeJson(b.agencyName), escapeJson(b.driverName), escapeJson(b.serviceType), escapeJson(b.departureTime), escapeJson(b.arrivalTime), escapeJson(b.duration), b.fare, b.locationUpdatedAt > 0);
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
                sb.append(busJson(b));
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    // GET and POST /api/admin/buses. The ADMIN_KEY environment variable must
    // be configured on the hosting service before the admin portal can change data.
    static class AdminBusesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            if (!isAdminRequest(exchange)) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"Admin access key is required\"}");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder output = new StringBuilder("[");
                boolean first = true;
                for (Bus bus : busMap.values()) { if (!first) output.append(","); first = false; output.append(busJson(bus)); }
                sendJson(exchange, 200, output.append("]").toString());
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Use GET or POST\"}");
                return;
            }
            try {
                Map<String, String> form = parseFormData(exchange);
                int busNo = Integer.parseInt(form.getOrDefault("busNumber", "0"));
                int seats = Integer.parseInt(form.getOrDefault("totalSeats", "0"));
                int fare = Integer.parseInt(form.getOrDefault("fare", "0"));
                String name = form.getOrDefault("busName", "").trim();
                String source = form.getOrDefault("source", "").trim();
                String destination = form.getOrDefault("destination", "").trim();
                String agency = form.getOrDefault("agencyName", "").trim();
                String driver = form.getOrDefault("driverName", "").trim();
                String type = form.getOrDefault("serviceType", "AC Sleeper").trim();
                String departure = form.getOrDefault("departure", "").trim();
                String arrival = form.getOrDefault("arrival", "").trim();
                String duration = form.getOrDefault("duration", "").trim();
                if (busNo < 1 || seats < 1 || fare < 1 || name.isEmpty() || source.isEmpty() || destination.isEmpty() || agency.isEmpty() || driver.isEmpty() || departure.isEmpty() || arrival.isEmpty() || duration.isEmpty()) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Complete all required bus, route, schedule, agency, and driver fields\"}");
                    return;
                }
                Bus created = new Bus(busNo, name, source, destination, seats, agency, driver, type, departure, arrival, duration, fare);
                if (busMap.putIfAbsent(busNo, created) != null) {
                    sendJson(exchange, 409, "{\"success\":false,\"message\":\"A bus with this number already exists\"}");
                    return;
                }
                sendJson(exchange, 201, "{\"success\":true,\"message\":\"Bus service added\",\"bus\":" + busJson(created) + "}");
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Bus number, capacity, and fare must be valid numbers\"}");
            }
        }
    }

    // Public, read-only live location for a selected service.
    static class ApiTrackHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange, 405, "{\"success\":false,\"message\":\"Use GET\"}"); return; }
            int busNo;
            try { busNo = Integer.parseInt(parseQueryParams(exchange).getOrDefault("busNumber", "0")); }
            catch (NumberFormatException e) { sendJson(exchange, 400, "{\"success\":false,\"message\":\"Invalid bus number\"}"); return; }
            Bus bus = busMap.get(busNo);
            if (bus == null) { sendJson(exchange, 404, "{\"success\":false,\"message\":\"Bus not found\"}"); return; }
            boolean available = bus.locationUpdatedAt > 0 && bus.latitude != null && bus.longitude != null;
            String json = String.format(Locale.ROOT, "{\"success\":true,\"busNo\":%d,\"driverName\":\"%s\",\"available\":%s,\"latitude\":%s,\"longitude\":%s,\"accuracy\":%s,\"updatedAt\":%d}",
                    bus.busNumber, escapeJson(bus.driverName), available,
                    available ? String.format(Locale.ROOT, "%.6f", bus.latitude) : "null",
                    available ? String.format(Locale.ROOT, "%.6f", bus.longitude) : "null",
                    available && bus.locationAccuracy != null ? String.format(Locale.ROOT, "%.1f", bus.locationAccuracy) : "null", bus.locationUpdatedAt);
            sendJson(exchange, 200, json);
        }
    }

    // Drivers authenticate with their assigned name, bus number, and a DRIVER_KEY
    // configured only on the server. The returned session stays in memory.
    static class DriverLoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange, 405, "{\"success\":false,\"message\":\"Use POST\"}"); return; }
            String key = System.getenv("DRIVER_KEY");
            if (key == null || key.isBlank()) { sendJson(exchange, 503, "{\"success\":false,\"message\":\"Driver tracking is not configured yet\"}"); return; }
            Map<String, String> form = parseFormData(exchange);
            try {
                int busNo = Integer.parseInt(form.getOrDefault("busNumber", "0"));
                String driver = form.getOrDefault("driverName", "").trim();
                String accessKey = form.getOrDefault("accessKey", "");
                Bus bus = busMap.get(busNo);
                if (bus == null || !bus.driverName.equalsIgnoreCase(driver) || !key.equals(accessKey)) { sendJson(exchange, 401, "{\"success\":false,\"message\":\"Check your bus number, driver name, and access key\"}"); return; }
                String token = UUID.randomUUID().toString(); driverSessions.put(token, busNo);
                sendJson(exchange, 200, "{\"success\":true,\"token\":\"" + token + "\",\"busNo\":" + busNo + ",\"busName\":\"" + escapeJson(bus.busName) + "\"}");
            } catch (NumberFormatException e) { sendJson(exchange, 400, "{\"success\":false,\"message\":\"Enter a valid bus number\"}"); }
        }
    }

    static class DriverLocationHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange, 405, "{\"success\":false,\"message\":\"Use POST\"}"); return; }
            Integer busNo = driverBusFromRequest(exchange);
            if (busNo == null) { sendJson(exchange, 401, "{\"success\":false,\"message\":\"Driver session is required\"}"); return; }
            try {
                Map<String, String> form = parseFormData(exchange);
                double latitude = Double.parseDouble(form.getOrDefault("latitude", "999"));
                double longitude = Double.parseDouble(form.getOrDefault("longitude", "999"));
                double accuracy = Double.parseDouble(form.getOrDefault("accuracy", "0"));
                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || accuracy < 0) throw new NumberFormatException();
                Bus bus = busMap.get(busNo); bus.latitude = latitude; bus.longitude = longitude; bus.locationAccuracy = accuracy; bus.locationUpdatedAt = System.currentTimeMillis();
                sendJson(exchange, 200, "{\"success\":true,\"message\":\"Location shared\",\"updatedAt\":" + bus.locationUpdatedAt + "}");
            } catch (NumberFormatException e) { sendJson(exchange, 400, "{\"success\":false,\"message\":\"Valid GPS coordinates are required\"}"); }
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
            BookingRecord record;
            try {
                record = createBooking(busNo, passenger, seatNo);
            } catch (SQLException exception) {
                sendJson(exchange, 503, "{\"success\":false,\"message\":\"Booking service could not reach the database\"}");
                return;
            }
            if (record == null) {
                sendJson(exchange, 409, String.format("{\"success\":false,\"message\":\"Seat %d is already booked or unavailable\"}", seatNo));
                return;
            }

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

            boolean cancelled;
            try {
                cancelled = cancelBooking(busNo, seatNo);
            } catch (SQLException exception) {
                sendJson(exchange, 503, "{\"success\":false,\"message\":\"Cancellation service could not reach the database\"}");
                return;
            }
            if (!cancelled) {
                sendJson(exchange, 404, "{\"success\":false,\"message\":\"No active booking found for this seat\"}");
                return;
            }

            System.out.println("[REAL-TIME CANCELLATION] Seat #" + seatNo + " cancelled on Bus " + busNo);

            sendJson(exchange, 200, String.format("{\"success\":true,\"message\":\"Booking cancelled successfully\",\"seatNo\":%d,\"busNo\":%d}", seatNo, busNo));
        }
    }

    // GET /api/health
    static class ApiHealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean connected = databaseConnected;
            if (connected) {
                try (Connection ignored = databaseConnection()) {
                    // Opening the connection makes the health value reflect the current database state.
                } catch (SQLException exception) {
                    connected = false;
                    databaseConnected = false;
                }
            }
            sendJson(exchange, 200, "{\"success\":true,\"databaseConfigured\":" + DATABASE_CONFIGURED
                    + ",\"databaseConnected\":" + connected
                    + ",\"persistence\":\"" + (connected ? "mysql" : "file") + "\"}");
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
            } else {
                try {
                    BookingRecord rec = createBooking(busNo, name, seatNo);
                    if (rec == null) {
                        out.append("<h2 class='error'>Seat Already Booked</h2><p>Please choose another available seat.</p>");
                    } else {
                        out.append("<h2 class='success'>Booking Confirmed (Task 4 JDBC)</h2>")
                           .append("<div class='details'>")
                           .append("<p><b>Ticket ID:</b> #").append(rec.bookingId).append("</p>")
                           .append("<p><b>Bus:</b> ").append(busNo).append(" (").append(bus.busName).append(")</p>")
                           .append("<p><b>Passenger:</b> ").append(escapeHtml(name)).append("</p>")
                           .append("<p><b>Seat Number:</b> ").append(seatNo).append("</p>")
                           .append("<p><b>Persistence:</b> ").append(databaseConnected ? "MySQL synced" : "local preview mode").append("</p>")
                           .append("</div>");
                    }
                } catch (SQLException exception) {
                    out.append("<h2 class='error'>Database Error</h2><p>Unable to store this booking.</p>");
                }
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

            try {
                if (!cancelBooking(busNo, seatNo)) {
                    out.append("<h2 class='error'>Cancellation Failed</h2><p>No active booking found for Seat ").append(seatNo).append(".</p>");
                } else {
                    out.append("<h2 class='success'>Booking Cancelled (JDBC)</h2><p>Seat ").append(seatNo).append(" is now released.</p>");
                }
            } catch (SQLException exception) {
                out.append("<h2 class='error'>Database Error</h2><p>Unable to cancel this booking.</p>");
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
