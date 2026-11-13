import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Proxies public Tomcat API requests to the in-container booking engine. */
@WebServlet(urlPatterns = "/api/*")
public class ApiProxyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String INTERNAL_BASE = "http://127.0.0.1:" +
        System.getenv().getOrDefault("INTERNAL_PORT", "10001");

    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) return false;
        String configured = System.getenv().getOrDefault("CORS_ORIGIN", "https://livedemo-olive.vercel.app");
        return origin.equals(configured)
            || origin.matches("https://livedemo-[a-z0-9-]+\\.vercel\\.app")
            || origin.matches("http://localhost(:[0-9]+)?");
    }

    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (!isAllowedOrigin(origin)) return;
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Admin-Key, X-Driver-Key");
        response.setHeader("Access-Control-Max-Age", "86400");
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        addCorsHeaders(request, response);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        String path = request.getRequestURI();
        String query = request.getQueryString();
        URL target = new URL(INTERNAL_BASE + path + (query == null ? "" : "?" + query));
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setRequestMethod(request.getMethod());
        connection.setDoInput(true);

        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                connection.setRequestProperty(name, request.getHeader(name));
            }
        }
        if (request.getContentLengthLong() > 0 || "POST".equalsIgnoreCase(request.getMethod())
                || "PUT".equalsIgnoreCase(request.getMethod())) {
            connection.setDoOutput(true);
            try (InputStream input = request.getInputStream(); OutputStream output = connection.getOutputStream()) {
                input.transferTo(output);
            }
        }

        int status = connection.getResponseCode();
        response.setStatus(status);
        if (connection.getContentType() != null) response.setContentType(connection.getContentType());
        InputStream body = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (body != null) {
            try (InputStream input = body; OutputStream output = response.getOutputStream()) {
                input.transferTo(output);
            }
        }
        connection.disconnect();
    }
}
