import java.util.*;

public class BusReservationSystem {

    // Helper class to represent a Bus
    static class Bus {
        int busNumber;
        String busName;
        int totalSeats;
        Map<Integer, String> bookedSeats; // SeatNumber -> PassengerName

        public Bus(int busNumber, String busName, int totalSeats) {
            this.busNumber = busNumber;
            this.busName = busName;
            this.totalSeats = totalSeats;
            this.bookedSeats = new HashMap<>();
        }

        public boolean isValidSeat(int seatNo) {
            return seatNo >= 1 && seatNo <= totalSeats;
        }

        public boolean isSeatBooked(int seatNo) {
            return bookedSeats.containsKey(seatNo);
        }

        public boolean bookSeat(int seatNo, String passengerName) {
            if (!isValidSeat(seatNo)) {
                System.out.println("❌ Invalid seat number! Seats available are from 1 to " + totalSeats + ".");
                return false;
            }
            if (isSeatBooked(seatNo)) {
                System.out.println("❌ Seat " + seatNo + " is already booked by " + bookedSeats.get(seatNo) + ".");
                return false;
            }
            bookedSeats.put(seatNo, passengerName);
            System.out.println("✅ Booking Successful! Seat " + seatNo + " confirmed for " + passengerName + " on Bus " + busNumber + ".");
            return true;
        }

        public void displayAvailableSeats() {
            System.out.println("\n--- Bus " + busNumber + " (" + busName + ") Seat Status ---");
            System.out.print("Available Seats: ");
            boolean anyAvailable = false;
            for (int i = 1; i <= totalSeats; i++) {
                if (!bookedSeats.containsKey(i)) {
                    System.out.print(i + " ");
                    anyAvailable = true;
                }
            }
            if (!anyAvailable) {
                System.out.print("No seats available (Bus is FULL)");
            }
            System.out.println("\nBooked Seats: " + bookedSeats.keySet());
        }

        public void displayBookings() {
            System.out.println("\nBookings for Bus " + busNumber + " (" + busName + "):");
            if (bookedSeats.isEmpty()) {
                System.out.println("  No bookings yet.");
            } else {
                for (Map.Entry<Integer, String> entry : bookedSeats.entrySet()) {
                    System.out.println("  Seat " + entry.getKey() + " -> " + entry.getValue());
                }
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Map<Integer, Bus> buses = new HashMap<>();

        // Initialize sample buses
        buses.put(101, new Bus(101, "City Express", 20));
        buses.put(102, new Bus(102, "Super Luxury", 30));
        buses.put(103, new Bus(103, "Night Rider", 25));

        System.out.println("============================================");
        System.out.println("     BUS SEAT RESERVATION SYSTEM (JAVA)     ");
        System.out.println("============================================");

        while (true) {
            System.out.println("\nSelect an option:");
            System.out.println("1. Book Bus Seat");
            System.out.println("2. View Seat Availability");
            System.out.println("3. View All Bookings");
            System.out.println("4. Exit");
            System.out.print("Enter choice (1-4): ");

            if (!sc.hasNextInt()) {
                System.out.println("Invalid input. Please enter a number.");
                sc.next();
                continue;
            }

            int choice = sc.nextInt();
            sc.nextLine(); // consume newline

            switch (choice) {
                case 1:
                    System.out.println("\n--- Book a Seat ---");
                    System.out.println("Available Buses: ");
                    for (Bus b : buses.values()) {
                        System.out.println("  Bus No: " + b.busNumber + " | " + b.busName + " | Total Seats: " + b.totalSeats);
                    }

                    System.out.print("Enter Bus Number: ");
                    int busNo = sc.nextInt();
                    sc.nextLine();

                    Bus selectedBus = buses.get(busNo);
                    if (selectedBus == null) {
                        System.out.println("❌ Error: Bus with number " + busNo + " does not exist!");
                        break;
                    }

                    System.out.print("Enter Passenger Name: ");
                    String passengerName = sc.nextLine().trim();
                    if (passengerName.isEmpty()) {
                        System.out.println("❌ Passenger name cannot be empty!");
                        break;
                    }

                    System.out.print("Enter Seat Number: ");
                    int seatNo = sc.nextInt();
                    sc.nextLine();

                    // Book seat with validation checks
                    selectedBus.bookSeat(seatNo, passengerName);
                    break;

                case 2:
                    System.out.print("Enter Bus Number to check seats: ");
                    int bNo = sc.nextInt();
                    sc.nextLine();
                    Bus b = buses.get(bNo);
                    if (b != null) {
                        b.displayAvailableSeats();
                    } else {
                        System.out.println("❌ Bus not found!");
                    }
                    break;

                case 3:
                    for (Bus bus : buses.values()) {
                        bus.displayBookings();
                    }
                    break;

                case 4:
                    System.out.println("Thank you for using Bus Reservation System. Goodbye!");
                    sc.close();
                    return;

                default:
                    System.out.println("Invalid choice! Please choose between 1 and 4.");
            }
        }
    }
}
