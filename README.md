# 🚌 Bus Reservation System - Complete Project Solution

This project implements all 4 tasks specified in the lab assignment:

---

## 📁 Project Structure

```
d:/New folder/New folder/
├── Task1_Java/
│   └── BusReservationSystem.java      (Java CLI seat reservation with validation)
├── Task2_MySQL/
│   └── bus_reservation.sql            (DDL, DML, sample data, and query scripts)
├── Task3_HTML_Servlet/
│   ├── index.html                     (Main navigation page)
│   ├── booking.html                   (Passenger booking HTML form)
│   ├── cancellation.html              (Passenger cancellation HTML form)
│   ├── WEB-INF/
│   │   └── web.xml                    (Servlet mapping deployment descriptor)
│   └── src/
│       ├── BookingServlet.java        (Processes booking & sends HTML confirmation)
│       └── CancellationServlet.java   (Processes cancellation & sends HTML confirmation)
└── Task4_JDBC_Servlet/
    ├── index.html                     (Interactive dashboard for all 3 operations)
    ├── WEB-INF/
    │   └── web.xml                    (Servlet mapping descriptor)
    └── src/
        ├── DBUtil.java                (MySQL JDBC connection manager)
        ├── BookSeatServlet.java       (JDBC seat booking into MySQL)
        ├── CancelSeatServlet.java     (JDBC seat cancellation in MySQL)
        └── CheckAvailabilityServlet.java (JDBC real-time available seat grid)
```

---

## 🚀 How to Run Each Task

### **Task 1: Core Java Program**
1. Navigate to `Task1_Java`:
   ```bash
   cd "Task1_Java"
   ```
2. Compile and run:
   ```bash
   javac BusReservationSystem.java
   java BusReservationSystem
   ```

---

### **Task 2: MySQL Database**
1. Open MySQL CLI or MySQL Workbench.
2. Execute the script:
   ```sql
   SOURCE Task2_MySQL/bus_reservation.sql;
   ```
   Or run directly via command line:
   ```bash
   mysql -u root -p < "Task2_MySQL/bus_reservation.sql"
   ```

---

### **Task 3: HTML & Servlet Application**
1. Deploy `Task3_HTML_Servlet` into your Apache Tomcat `webapps/` folder (or open as a Dynamic Web Project in Eclipse / IntelliJ).
2. Start Apache Tomcat.
3. Open in browser:
   ```
   http://localhost:8080/Task3_HTML_Servlet/index.html
   ```

---

### **Task 4: JDBC & Servlet Application**
1. Ensure MySQL server is running and `bus_reservation_db` from Task 2 is loaded.
2. Ensure `mysql-connector-j-x.x.x.jar` is placed in Tomcat's `lib/` directory or `WEB-INF/lib`.
3. Set database connection values in your environment before starting Tomcat. In PowerShell:
   ```powershell
   $env:BUS_DB_URL = "jdbc:mysql://localhost:3306/bus_reservation_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   $env:BUS_DB_USER = "root"
   $env:BUS_DB_PASSWORD = "your-mysql-password"
   ```
   Do not commit database credentials to source code.
4. Deploy `Task4_JDBC_Servlet` to Apache Tomcat.
5. Open in browser:
   ```
   http://localhost:8080/Task4_JDBC_Servlet/index.html
   ```

---

## Render MySQL Connection

The deployed REST server uses MySQL as its booking source of truth when all
three environment variables are configured. Add the following values in the
Render service environment settings, then deploy:

```text
BUS_DB_URL=jdbc:mysql://HOST:3306/bus_reservation_db?useSSL=true&serverTimezone=UTC
BUS_DB_USER=your_database_user
BUS_DB_PASSWORD=your_database_password
```

Run `Task2_MySQL/bus_reservation.sql` once against the database before the
first deployment. The server upserts its configured buses, reads confirmed
bookings on startup, writes new bookings to MySQL, and marks cancellations as
`CANCELLED`.

After deployment, open:

```text
https://bus-reservation-api-uqnc.onrender.com/api/health
```

`"databaseConnected": true` and `"persistence":"mysql"` confirm the live
connection. Without those variables, the service deliberately falls back to
the local booking file for preview mode.
