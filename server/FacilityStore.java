/*
 * FacilityStore.java
 * Purpose: In-memory storage for facilities and bookings.
 * Design notes:
 * - Thread-safe via synchronized blocks; server can be single-threaded but we prepare for concurrency.
 * - Stores facilityName -> Facility and bookingId -> Booking maps.
 */

import java.util.*;

public class FacilityStore {
    private final Map<String, Types.Facility> facilities = new HashMap<>(); // name->facility map
    private final Map<Long, Types.Booking> bookings = new HashMap<>();      // id->booking map
    private long nextBookingId = 1L;                                        // simple id generator

    // Ensure facility exists; create if absent
    public synchronized Types.Facility ensureFacility(String name) {
        return facilities.computeIfAbsent(name, Types.Facility::new); // create new facility if missing
    }

    // Get facility or null
    public synchronized Types.Facility getFacility(String name) {
        return facilities.get(name); // return facility by name
    }

    // Generate a new booking id
    public synchronized long newBookingId() {
        return nextBookingId++; // increment and return; non-persistent
    }

    // Save booking into global and facility lists
    public synchronized void addBooking(Types.Booking b) {
        bookings.put(b.id, b);                         // put into id map
        Types.Facility f = ensureFacility(b.facility); // ensure facility exists
        f.bookings.add(b);                             // add booking to facility list
    }

    // Lookup booking by id
    public synchronized Types.Booking getBooking(long id) {
        return bookings.get(id); // return booking or null
    }

    // Remove booking (if needed)
    public synchronized void removeBooking(long id) {
        Types.Booking b = bookings.remove(id);         // remove from id map
        if (b != null) {
            Types.Facility f = facilities.get(b.facility); // get facility
            if (f != null) f.bookings.remove(b);       // remove from facility list
        }
    }

    // Get snapshot list of bookings for facility
    public synchronized List<Types.Booking> getFacilityBookings(String name) {
        Types.Facility f = facilities.get(name);             // lookup facility
        if (f == null) return Collections.emptyList();       // no facility
        return new ArrayList<>(f.bookings);                  // copy to avoid external mutation
    }

    // Remove all bookings for a facility within a specific day range [dayStart, dayEnd)
    public synchronized int removeBookingsInRange(String facilityName, long dayStart, long dayEnd) {
        Types.Facility f = facilities.get(facilityName);     // lookup facility
        if (f == null) return 0;                             // no facility, nothing to remove
        
        List<Types.Booking> toRemove = new ArrayList<>();    // collect bookings to remove
        for (Types.Booking b : f.bookings) {                 // iterate facility bookings
            // Check if booking overlaps with [dayStart, dayEnd)
            if (b.startEpochMs < dayEnd && b.endEpochMs > dayStart) {
                toRemove.add(b);                             // mark for removal
            }
        }
        
        // Remove collected bookings
        for (Types.Booking b : toRemove) {
            bookings.remove(b.id);                           // remove from global map
            f.bookings.remove(b);                            // remove from facility list
        }
        
        return toRemove.size();                              // return count of removed bookings
    }
}
