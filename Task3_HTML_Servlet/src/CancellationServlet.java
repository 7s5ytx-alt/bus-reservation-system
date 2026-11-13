import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Task 3: CancellationServlet
 * Processes passenger seat cancellation requests from HTML form
 * and displays an HTML confirmation message.
 *
 * Note: If using Tomcat 10+, replace 'javax.servlet' with 'jakarta.servlet'.
 */
@WebServlet("/CancellationServlet")
public class CancellationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String busNoStr = request.getParameter("busNumber");
        String seatNoStr = request.getParameter("seatNumber");
        String passengerName = request.getParameter("passengerName");

        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Cancellation Status</title>");
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
            if (busNoStr == null || seatNoStr == null || passengerName == null ||
                busNoStr.trim().isEmpty() || seatNoStr.trim().isEmpty() || passengerName.trim().isEmpty()) {
                out.println("<h2 class='error'>❌ Cancellation Failed</h2>");
                out.println("<p>Please provide Bus Number, Seat Number, and Passenger Name!</p>");
            } else {
                int busNo = Integer.parseInt(busNoStr.trim());
                int seatNo = Integer.parseInt(seatNoStr.trim());
                passengerName = passengerName.trim();

                String seatKey = busNo + "-" + seatNo;
                String bookedPassenger = BookingServlet.bookingStore.get(seatKey);

                if (bookedPassenger == null) {
                    out.println("<h2 class='error'>❌ No Booking Found</h2>");
                    out.println("<p>Seat <b>" + seatNo + "</b> on Bus <b>" + busNo + "</b> is not currently booked.</p>");
                } else if (!bookedPassenger.equalsIgnoreCase(passengerName)) {
                    out.println("<h2 class='error'>❌ Name Mismatch</h2>");
                    out.println("<p>The passenger name provided does not match the name on record for this seat.</p>");
                } else if (BookingServlet.bookingStore.remove(seatKey, bookedPassenger)) {
                    // Remove only the booking that was verified above. This prevents a
                    // concurrent rebooking from being cancelled by this request.
                    out.println("<h2 class='success'>✅ Booking Cancelled Successfully!</h2>");
                    out.println("<div class='details'>");
                    out.println("<p><b>Bus Number:</b> " + busNo + "</p>");
                    out.println("<p><b>Seat Number:</b> " + seatNo + "</p>");
                    out.println("<p><b>Passenger Name:</b> " + HtmlEscaper.escape(passengerName) + "</p>");
                    out.println("<p><b>Status:</b> <span class='error'>CANCELLED</span></p>");
                    out.println("</div>");
                } else {
                    out.println("<h2 class='error'>❌ Booking Changed</h2>");
                    out.println("<p>The booking changed while cancellation was in progress. Please refresh and try again.</p>");
                }
            }
        } catch (NumberFormatException e) {
            out.println("<h2 class='error'>❌ Invalid Input</h2>");
            out.println("<p>Seat and Bus numbers must be numeric values.</p>");
        }

        out.println("<br><a href='cancellation.html' class='btn'>Back to Cancel Page</a> ");
        out.println("<a href='index.html' class='btn' style='background:#666;'>Home</a>");
        out.println("</div></body></html>");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.sendRedirect("cancellation.html");
    }
}
