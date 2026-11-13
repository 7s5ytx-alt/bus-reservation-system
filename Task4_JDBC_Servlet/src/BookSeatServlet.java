import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Task 4: BookSeatServlet
 * Uses JDBC to book a seat into MySQL database with validation checks.
 */
@WebServlet("/BookSeatServlet")
public class BookSeatServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String busNoStr = request.getParameter("busNumber");
        String passengerName = request.getParameter("passengerName");
        String seatNoStr = request.getParameter("seatNumber");

        out.println("<!DOCTYPE html><html><head><title>JDBC Booking Confirmation</title>");
        out.println("<style>");
        out.println("body { font-family: 'Segoe UI', Arial, sans-serif; background: #eef2f7; display:flex; justify-content:center; align-items:center; min-height:100vh; margin:0; }");
        out.println(".card { background: #fff; padding: 30px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); max-width: 500px; width: 100%; text-align: center; }");
        out.println(".success { color: #27ae60; }");
        out.println(".error { color: #e74c3c; }");
        out.println(".details { text-align: left; background: #f8f9fa; padding: 15px; border-radius: 6px; margin: 20px 0; }");
        out.println(".btn { display: inline-block; padding: 10px 18px; margin: 5px; background: #1e3c72; color: #fff; text-decoration: none; border-radius: 5px; font-weight: bold; }");
        out.println("</style></head><body><div class='card'>");

        if (busNoStr == null || passengerName == null || seatNoStr == null ||
            busNoStr.trim().isEmpty() || passengerName.trim().isEmpty() || seatNoStr.trim().isEmpty()) {
            out.println("<h2 class='error'>❌ Missing Required Fields</h2>");
            out.println("<p>Please fill out all booking details.</p>");
        } else {
            try {
                int busNo = Integer.parseInt(busNoStr.trim());
                int seatNo = Integer.parseInt(seatNoStr.trim());
                passengerName = passengerName.trim();

                try (Connection conn = DBUtil.getConnection()) {
                    // 1. Check if the bus exists and get its total seats
                    String busCheckSql = "SELECT bus_name, total_seats FROM bus WHERE bus_no = ?";
                    String busName = "";
                    int totalSeats = 0;
                    boolean busFound = false;

                    try (PreparedStatement psBus = conn.prepareStatement(busCheckSql)) {
                        psBus.setInt(1, busNo);
                        try (ResultSet rs = psBus.executeQuery()) {
                            if (rs.next()) {
                                busFound = true;
                                busName = rs.getString("bus_name");
                                totalSeats = rs.getInt("total_seats");
                            }
                        }
                    }

                    if (!busFound) {
                        out.println("<h2 class='error'>❌ Bus Not Found</h2>");
                        out.println("<p>Bus number <b>" + busNo + "</b> does not exist in the database.</p>");
                    } else if (seatNo < 1 || seatNo > totalSeats) {
                        out.println("<h2 class='error'>❌ Invalid Seat Number</h2>");
                        out.println("<p>Seat number must be between 1 and " + totalSeats + " for " + busName + ".</p>");
                    } else {
                        // 2. Check if seat is already booked
                        String seatCheckSql = "SELECT passenger_name FROM booking WHERE bus_no = ? AND seat_no = ? AND status = 'CONFIRMED'";
                        boolean isAlreadyBooked = false;
                        String bookedBy = "";

                        try (PreparedStatement psSeat = conn.prepareStatement(seatCheckSql)) {
                            psSeat.setInt(1, busNo);
                            psSeat.setInt(2, seatNo);
                            try (ResultSet rs = psSeat.executeQuery()) {
                                if (rs.next()) {
                                    isAlreadyBooked = true;
                                    bookedBy = rs.getString("passenger_name");
                                }
                            }
                        }

                        if (isAlreadyBooked) {
                            out.println("<h2 class='error'>❌ Seat Already Booked</h2>");
                            out.println("<p>Seat <b>" + seatNo + "</b> is already reserved by <b>" + HtmlEscaper.escape(bookedBy) + "</b>.</p>");
                        } else {
                            // 3. Insert new booking record
                            String insertSql = "INSERT INTO booking (bus_no, passenger_name, seat_no, status) VALUES (?, ?, ?, 'CONFIRMED')";
                            int generatedId = -1;

                            try (PreparedStatement psInsert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                                psInsert.setInt(1, busNo);
                                psInsert.setString(2, passengerName);
                                psInsert.setInt(3, seatNo);
                                psInsert.executeUpdate();

                                try (ResultSet gk = psInsert.getGeneratedKeys()) {
                                    if (gk.next()) {
                                        generatedId = gk.getInt(1);
                                    }
                                }

                                out.println("<h2 class='success'>✅ Booking Confirmed via JDBC!</h2>");
                                out.println("<div class='details'>");
                                out.println("<p><b>Booking Reference ID:</b> #" + generatedId + "</p>");
                                out.println("<p><b>Bus:</b> " + busNo + " (" + HtmlEscaper.escape(busName) + ")</p>");
                                out.println("<p><b>Passenger Name:</b> " + HtmlEscaper.escape(passengerName) + "</p>");
                                out.println("<p><b>Seat Number:</b> " + seatNo + "</p>");
                                out.println("<p><b>Status:</b> <span class='success'>CONFIRMED</span></p>");
                                out.println("</div>");
                            } catch (SQLIntegrityConstraintViolationException e) {
                                // uq_booking_active_seat makes this safe even when two requests race.
                                out.println("<h2 class='error'>❌ Seat Already Booked</h2>");
                                out.println("<p>Another booking was confirmed for Seat <b>" + seatNo + "</b> just before this request completed.</p>");
                            }
                        }
                    }

                } catch (SQLException e) {
                    out.println("<h2 class='error'>❌ Database Error</h2>");
                    out.println("<p>Unable to complete the booking. Check the server logs and database configuration.</p>");
                }

            } catch (NumberFormatException e) {
                out.println("<h2 class='error'>❌ Invalid Number Format</h2>");
                out.println("<p>Please enter valid numbers for Bus No and Seat No.</p>");
            }
        }

        out.println("<br><a href='index.html' class='btn'>Back to Dashboard</a>");
        out.println("<a href='CheckAvailabilityServlet?busNumber=" + (busNoStr != null ? busNoStr : "101") + "' class='btn' style='background:#27ae60;'>View Bus Seats</a>");
        out.println("</div></body></html>");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.sendRedirect("index.html");
    }
}
