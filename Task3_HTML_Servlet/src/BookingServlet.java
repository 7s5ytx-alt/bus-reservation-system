import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Task 3: BookingServlet
 * Processes passenger seat booking requests from HTML form
 * and displays an HTML confirmation message.
 *
 * Note: If using Tomcat 10+, replace 'javax.servlet' with 'jakarta.servlet'.
 */
@WebServlet("/BookingServlet")
public class BookingServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // In-memory seat registry: "busNo-seatNo" -> Passenger Name
    public static final ConcurrentHashMap<String, String> bookingStore = new ConcurrentHashMap<>();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String busNoStr = request.getParameter("busNumber");
        String passengerName = request.getParameter("passengerName");
        String seatNoStr = request.getParameter("seatNumber");

        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Booking Status</title>");
        out.println("<style>");
        out.println("body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f4f8; display:flex; justify-content:center; align-items:center; min-height:100vh; margin:0; }");
        out.println(".card { background: #fff; padding: 30px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); max-width: 480px; width: 100%; text-align: center; }");
        out.println(".success { color: #27ae60; }");
        out.println(".error { color: #e74c3c; }");
        out.println(".details { text-align: left; background: #f8f9fa; padding: 15px; border-radius: 6px; margin: 20px 0; }");
        out.println(".btn { display: inline-block; padding: 10px 20px; background: #1e3c72; color: #fff; text-decoration: none; border-radius: 5px; font-weight: bold; }");
        out.println("</style></head><body>");
        out.println("<div class='card'>");

        try {
            if (busNoStr == null || passengerName == null || seatNoStr == null ||
                busNoStr.trim().isEmpty() || passengerName.trim().isEmpty() || seatNoStr.trim().isEmpty()) {
                out.println("<h2 class='error'>❌ Booking Failed</h2>");
                out.println("<p>All fields are mandatory!</p>");
            } else {
                int busNo = Integer.parseInt(busNoStr.trim());
                int seatNo = Integer.parseInt(seatNoStr.trim());
                passengerName = passengerName.trim();

                // Validation: Seat number range check
                if (seatNo < 1 || seatNo > 30) {
                    out.println("<h2 class='error'>❌ Invalid Seat Number</h2>");
                    out.println("<p>Seat numbers must be between 1 and 30.</p>");
                } else {
                    String seatKey = busNo + "-" + seatNo;

                    // Atomic check and insert
                    String existingPassenger = bookingStore.putIfAbsent(seatKey, passengerName);

                    if (existingPassenger != null) {
                        // Already booked
                        out.println("<h2 class='error'>❌ Seat Already Booked</h2>");
                        out.println("<p>Seat <b>" + seatNo + "</b> on Bus <b>" + busNo + "</b> is already occupied by " + HtmlEscaper.escape(existingPassenger) + ".</p>");
                    } else {
                        // Successfully booked
                        out.println("<h2 class='success'>✅ Booking Confirmed!</h2>");
                        out.println("<div class='details'>");
                        out.println("<p><b>Bus Number:</b> " + busNo + "</p>");
                        out.println("<p><b>Passenger Name:</b> " + HtmlEscaper.escape(passengerName) + "</p>");
                        out.println("<p><b>Seat Number:</b> " + seatNo + "</p>");
                        out.println("<p><b>Status:</b> <span class='success'>CONFIRMED</span></p>");
                        out.println("</div>");
                    }
                }
            }
        } catch (NumberFormatException e) {
            out.println("<h2 class='error'>❌ Invalid Input</h2>");
            out.println("<p>Seat and Bus numbers must be numeric values.</p>");
        }

        out.println("<br><a href='booking.html' class='btn'>Book Another Seat</a> ");
        out.println("<a href='index.html' class='btn' style='background:#666;'>Home</a>");
        out.println("</div></body></html>");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.sendRedirect("booking.html");
    }
}
