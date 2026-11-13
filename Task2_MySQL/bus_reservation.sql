-- ========================================================
-- TASK 2: MySQL - BUS RESERVATION SYSTEM
-- ========================================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS bus_reservation_db;
USE bus_reservation_db;

-- 2. Drop existing tables if re-running script
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS bus;

-- 3. Create 'bus' table
CREATE TABLE bus (
    bus_no INT PRIMARY KEY,
    bus_name VARCHAR(100) NOT NULL,
    source VARCHAR(50) NOT NULL,
    destination VARCHAR(50) NOT NULL,
    total_seats INT NOT NULL
);

-- 4. Create 'booking' table
CREATE TABLE booking (
    booking_id INT AUTO_INCREMENT PRIMARY KEY,
    bus_no INT NOT NULL,
    passenger_name VARCHAR(100) NOT NULL,
    seat_no INT NOT NULL,
    booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('CONFIRMED', 'CANCELLED') DEFAULT 'CONFIRMED',
    -- Supports quick per-bus lookups in both MySQL and TiDB.
    INDEX idx_booking_bus_no (bus_no),
    -- NULL for cancelled rows allows booking the seat again after cancellation.
    -- A confirmed row receives a stable per-bus/per-seat key, which prevents
    -- concurrent requests from confirming the same seat twice.
    active_seat_key VARCHAR(32) GENERATED ALWAYS AS (
        CASE WHEN status = 'CONFIRMED' THEN CONCAT(bus_no, ':', seat_no) ELSE NULL END
    ) STORED,
    UNIQUE KEY uq_booking_active_seat (active_seat_key)
);

-- ========================================================
-- 5. Insert Sample Data
-- ========================================================

-- Insert Buses
INSERT INTO bus (bus_no, bus_name, source, destination, total_seats) VALUES
(101, 'Kavveri Express', 'Bangalore', 'Chennai', 30),
(102, 'Royal Volvo', 'Hyderabad', 'Bangalore', 35),
(103, 'Night Rider Sleeper', 'Mumbai', 'Goa', 25);

-- Insert Initial Seat Bookings
INSERT INTO booking (bus_no, passenger_name, seat_no, status) VALUES
(101, 'Alice Smith', 1, 'CONFIRMED'),
(101, 'Bob Johnson', 2, 'CONFIRMED'),
(101, 'Charlie Brown', 5, 'CONFIRMED'),
(102, 'David Miller', 10, 'CONFIRMED'),
(102, 'Emma Watson', 12, 'CONFIRMED');

-- ========================================================
-- 6. REQUIRED SQL QUERIES
-- ========================================================

-- Query 1: Book a seat (e.g., Book Seat 7 on Bus 101 for passenger 'John Doe')
-- Ensure seat is not already booked before inserting
INSERT INTO booking (bus_no, passenger_name, seat_no, status)
SELECT 101, 'John Doe', 7, 'CONFIRMED'
WHERE NOT EXISTS (
    SELECT 1 FROM booking 
    WHERE bus_no = 101 AND seat_no = 7 AND status = 'CONFIRMED'
);

-- Standard direct insert for seat booking:
-- INSERT INTO booking (bus_no, passenger_name, seat_no, status) VALUES (101, 'John Doe', 7, 'CONFIRMED');


-- Query 2: Cancel a booking (by booking_id or bus_no & seat_no)
-- Method A: Update status to 'CANCELLED'
UPDATE booking 
SET status = 'CANCELLED' 
WHERE bus_no = 101 AND seat_no = 5 AND status = 'CONFIRMED';

-- Method B: Hard delete the booking row
-- DELETE FROM booking WHERE bus_no = 101 AND seat_no = 5;


-- Query 3: Display Available Seats summary for all buses
SELECT 
    b.bus_no,
    b.bus_name,
    b.source,
    b.destination,
    b.total_seats,
    COUNT(CASE WHEN bk.status = 'CONFIRMED' THEN 1 END) AS booked_seats_count,
    (b.total_seats - COUNT(CASE WHEN bk.status = 'CONFIRMED' THEN 1 END)) AS available_seats_count
FROM bus b
LEFT JOIN booking bk ON b.bus_no = bk.bus_no
GROUP BY b.bus_no, b.bus_name, b.source, b.destination, b.total_seats;


-- Query 4: Display which specific seats are booked for a given bus (e.g. Bus 101)
SELECT 
    booking_id, 
    seat_no, 
    passenger_name, 
    booking_date 
FROM booking 
WHERE bus_no = 101 AND status = 'CONFIRMED'
ORDER BY seat_no ASC;


-- Query 5: Find all available seat numbers for a specific bus (Bus 101)
-- (Using recursive CTE to generate seat numbers 1 to total_seats)
WITH RECURSIVE seat_numbers AS (
    SELECT 1 AS seat_no
    UNION ALL
    SELECT seat_no + 1 FROM seat_numbers 
    WHERE seat_no < (SELECT total_seats FROM bus WHERE bus_no = 101)
)
SELECT s.seat_no AS available_seat_no
FROM seat_numbers s
WHERE s.seat_no NOT IN (
    SELECT seat_no FROM booking 
    WHERE bus_no = 101 AND status = 'CONFIRMED'
);
