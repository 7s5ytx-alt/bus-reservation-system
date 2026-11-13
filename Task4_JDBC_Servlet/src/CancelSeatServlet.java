import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Task 4: CancelSeatServlet
 * Uses JDBC to cancel an active bus seat booking in MySQL.
 */
@WebServlet("/CancelSeatServlet")
public class CancelSeatServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String busNoStr = request.getParameter("busNumber");
        String seatNoStr = request.getParameter("seatNumber");
        String passengerName = request.getParameter("passengerName");

        out.println("<!DOCTYPE html><html><head><title>JDBC Cancellation</title>");
        out.println("<style>");
        out.println("body { font-family: 'Segoe UI', Arial, sans-serif; background: #eef2f7; display:flex; justify-content:center; align-items:center; min-height:100vh; margin:0; }");
        out.println(".card { background: #fff; padding: 30px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); max-width: 500px; width: 100%; text-align: center; }");
        out.println(".success { color: #27ae60; }");
        out.println(".error { color: #e74c3c; }");
        out.println(".details { text-align: left; background: #f8f9fa; padding: 15px; border-radius: 6px; margin: 20px 0; }");
        out.println(".btn { display: inline-block; padding: 10px 18px; margin: 5px; background: #1e3c72; color: #fff; text-decoration: none; border-radius: 5px; font-weight: bold; }");
        out.println("</style></head><body><div class='card'>");

        if (busNoStr == null || seatNoStr == null || passengerName == null ||
            busNoStr.trim().isEmpty() || seatNoStr.trim().isEmpty() || passengerName.trim().isEmpty()) {
            out.println("<h2 class='error'>❌ Missing Required Fields</h2>");
            out.println("<p>Please provide Bus No, Seat No, and Passenger Name to cancel.</p>");
        } else {
            try {
                int busNo = Integer.parseInt(busNoStr.trim());
                int seatNo = Integer.parseInt(seatNoStr.trim());
                passengerName = passengerName.trim();

                try (Connection conn = DBUtil.getConnection()) {
                    // Update the status to 'CANCELLED' for matching active booking
                    String cancelSql = "UPDATE booking SET status = 'CANCELLED' "
                                     + "WHERE bus_no = ? AND seat_no = ? AND passenger_name = ? AND status = 'CONFIRMED'";

                    try (PreparedStatement ps = conn.prepareStatement(cancelSql)) {
                        ps.setInt(1, busNo);
                        ps.setInt(2, seatNo);
                        ps.setString(3, passengerName);

                        int rowsAffected = ps.executeUpdate();

                        if (rowsAffected > 0) {
                            out.println("<h2 class='success'>✅ Booking Cancelled via JDBC!</h2>");
                            out.println("<div class='details'>");
                            out.println("<p><b>Bus Number:</b> " + busNo + "</p>");
                            out.println("<p><b>Seat Number:</b> " + seatNo + "</p>");
                            out.println("<p><b>Passenger Name:</b> " + HtmlEscaper.escape(passengerName) + "</p>");
                            out.println("<p><b>Updated Status:</b> <span class='error'>CANCELLED</span></p>");
                            out.println("</div>");
                        } else {
                            out.println("<h2 class='error'>❌ Cancellation Failed</h2>");
                            out.println("<p>No active booking matched Bus #" + busNo + ", Seat #" + seatNo + " for passenger '" + HtmlEscaper.escape(passengerName) + "'.</p>");
                        }
                    }

                } catch (SQLException e) {
                    out.println("<h2 class='error'>❌ Database Error</h2>");
                    out.println("<p>Unable to complete the cancellation. Check the server logs and database configuration.</p>");
                }

            } catch (NumberFormatException e) {
                out.println("<h2 class='error'>❌ Invalid Number Format</h2>");
                out.println("<p>Bus No and Seat No must be valid numbers.</p>");
            }
        }

        out.println("<br><a href='index.html' class='btn'>Back to Dashboard</a>");
        out.println("</div></body></html>");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.sendRedirect("index.html");
    }
}
