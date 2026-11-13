import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Task 4: CheckAvailabilityServlet
 * Queries MySQL using JDBC to calculate and display real-time seat availability.
 */
@WebServlet("/CheckAvailabilityServlet")
public class CheckAvailabilityServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String busNoParam = request.getParameter("busNumber");
        int selectedBusNo = (busNoParam != null && !busNoParam.trim().isEmpty()) 
                            ? Integer.parseInt(busNoParam.trim()) : 101;

        out.println("<!DOCTYPE html><html><head><title>Bus Seat Availability</title>");
        out.println("<style>");
        out.println("body { font-family: 'Segoe UI', Arial, sans-serif; background: #eef2f7; padding: 25px; margin: 0; }");
        out.println(".container { max-width: 800px; margin: 0 auto; background: #fff; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); }");
        out.println("h2 { color: #1e3c72; margin-top: 0; }");
        out.println(".filter-bar { margin-bottom: 25px; display: flex; gap: 10px; align-items: center; }");
        out.println("select, button { padding: 10px; border-radius: 6px; border: 1px solid #ccc; font-size: 15px; }");
        out.println("button { background: #1e3c72; color: #fff; border: none; cursor: pointer; font-weight: bold; }");
        out.println(".bus-info { background: #f1f6fb; border-left: 5px solid #1e3c72; padding: 15px; margin-bottom: 20px; border-radius: 4px; }");
        out.println(".stats { display: flex; gap: 20px; margin-bottom: 25px; }");
        out.println(".stat-box { flex: 1; padding: 15px; border-radius: 8px; text-align: center; color: #fff; }");
        out.println(".bg-blue { background: #3498db; }");
        out.println(".bg-red { background: #e74c3c; }");
        out.println(".bg-green { background: #2ecc71; }");
        out.println(".seat-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; margin-bottom: 25px; }");
        out.println(".seat { padding: 12px 6px; text-align: center; border-radius: 6px; font-weight: bold; font-size: 14px; }");
        out.println(".seat.booked { background: #fadbd8; color: #78281f; border: 1px solid #e74c3c; }");
        out.println(".seat.available { background: #d4efdf; color: #145a32; border: 1px solid #27ae60; }");
        out.println(".btn-nav { display: inline-block; padding: 10px 18px; background: #1e3c72; color: #fff; text-decoration: none; border-radius: 6px; font-weight: bold; }");
        out.println("</style></head><body><div class='container'>");
        out.println("<h2>💺 Real-Time Seat Availability (JDBC)</h2>");

        // Bus Selector Dropdown Form
        out.println("<form method='GET' action='CheckAvailabilityServlet' class='filter-bar'>");
        out.println("<label for='busNumber'><b>Select Bus:</b></label>");
        out.println("<select name='busNumber' id='busNumber'>");
        out.println("<option value='101' " + (selectedBusNo == 101 ? "selected" : "") + ">Bus 101 (Bangalore - Chennai)</option>");
        out.println("<option value='102' " + (selectedBusNo == 102 ? "selected" : "") + ">Bus 102 (Hyderabad - Bangalore)</option>");
        out.println("<option value='103' " + (selectedBusNo == 103 ? "selected" : "") + ">Bus 103 (Mumbai - Goa)</option>");
        out.println("</select>");
        out.println("<button type='submit'>Check Status</button>");
        out.println("</form>");

        try (Connection conn = DBUtil.getConnection()) {
            // 1. Fetch Bus Details
            String busSql = "SELECT * FROM bus WHERE bus_no = ?";
            String busName = "";
            String source = "";
            String dest = "";
            int totalSeats = 0;

            try (PreparedStatement psBus = conn.prepareStatement(busSql)) {
                psBus.setInt(1, selectedBusNo);
                try (ResultSet rs = psBus.executeQuery()) {
                    if (rs.next()) {
                        busName = rs.getString("bus_name");
                        source = rs.getString("source");
                        dest = rs.getString("destination");
                        totalSeats = rs.getInt("total_seats");
                    }
                }
            }

            // 2. Fetch Booked Seats
            Set<Integer> bookedSeats = new HashSet<>();
            String bookedSql = "SELECT seat_no, passenger_name FROM booking WHERE bus_no = ? AND status = 'CONFIRMED'";
            try (PreparedStatement psBooked = conn.prepareStatement(bookedSql)) {
                psBooked.setInt(1, selectedBusNo);
                try (ResultSet rs = psBooked.executeQuery()) {
                    while (rs.next()) {
                        bookedSeats.add(rs.getInt("seat_no"));
                    }
                }
            }

            int bookedCount = bookedSeats.size();
            int availableCount = totalSeats - bookedCount;

            // Display Bus Info
            out.println("<div class='bus-info'>");
            out.println("<h3>Bus " + selectedBusNo + ": " + HtmlEscaper.escape(busName) + "</h3>");
            out.println("<p><b>Route:</b> " + HtmlEscaper.escape(source) + " ➔ " + HtmlEscaper.escape(dest) + "</p>");
            out.println("</div>");

            // Stats
            out.println("<div class='stats'>");
            out.println("<div class='stat-box bg-blue'><h3>" + totalSeats + "</h3>Total Seats</div>");
            out.println("<div class='stat-box bg-red'><h3>" + bookedCount + "</h3>Booked</div>");
            out.println("<div class='stat-box bg-green'><h3>" + availableCount + "</h3>Available</div>");
            out.println("</div>");

            // Visual Seat Grid
            out.println("<h4>Seat Layout Map:</h4>");
            out.println("<div class='seat-grid'>");
            for (int i = 1; i <= totalSeats; i++) {
                if (bookedSeats.contains(i)) {
                    out.println("<div class='seat booked'>#" + i + "<br>(Booked)</div>");
                } else {
                    out.println("<div class='seat available'>#" + i + "<br>(Open)</div>");
                }
            }
            out.println("</div>");

        } catch (SQLException e) {
            out.println("<p style='color:red;'>Unable to load seat availability. Check the server logs and database configuration.</p>");
        }

        out.println("<br><a href='index.html' class='btn-nav'>⬅ Back to Reservation Dashboard</a>");
        out.println("</div></body></html>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
