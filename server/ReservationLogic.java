/*
 * ReservationLogic.java
 * Purpose: Implements business rules for booking, querying availability, and changing bookings.
 * Design notes:
 * - Booking allowed only if new interval does not overlap existing bookings for same facility.
 * - Change booking applies offset minutes and validates no overlap; otherwise returns conflict.
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class ReservationLogic {
    private final FacilityStore store; // storage dependency

    public ReservationLogic(FacilityStore store) {
        this.store = store; // assign store
    }

    // Check if weekly time intervals overlap (using week minutes for comparison)
    private boolean hasOverlap(List<Types.Booking> bookings, Types.WeeklyTime start, Types.WeeklyTime end, Long excludeId) {
        int startMinutes = start.toWeekMinutes();                       // convert to week minutes
        int endMinutes = end.toWeekMinutes();                           // convert to week minutes
        
        for (Types.Booking b : bookings) {                              // iterate existing bookings
            if (excludeId != null && b.id == excludeId) continue;       // skip the booking being changed
            int bStart = b.start.toWeekMinutes();                       // booking start in week minutes
            int bEnd = b.end.toWeekMinutes();                           // booking end in week minutes
            if (Math.max(bStart, startMinutes) < Math.min(bEnd, endMinutes)) {
                return true;                                            // intervals overlap
            }
        }
        return false;                                                   // no overlap found
    }

    // Book a new interval; returns booking id or throws ConflictException
    public long book(String facility, String user, Types.WeeklyTime start, Types.WeeklyTime end) throws ConflictException {
        store.ensureFacility(facility);                                 // ensure facility exists
        List<Types.Booking> existing = store.getFacilityBookings(facility); // snapshot existing
        if (hasOverlap(existing, start, end, null)) {                   // detect overlap
            throw new ConflictException("overlap");                    // conflict error
        }
        long id = store.newBookingId();                                 // generate new id
        Types.Booking b = new Types.Booking(id, facility, user, start, end); // create booking
        store.addBooking(b);                                            // persist booking
        return id;                                                      // return id
    }

    // Change booking by offset minutes; returns updated interval; may throw not found or conflict
    public Types.Interval change(long bookingId, int offsetMinutes) throws NotFoundException, ConflictException {
        Types.Booking b = store.getBooking(bookingId);                  // lookup booking
        if (b == null) throw new NotFoundException("booking");         // not found
        
        // Calculate duration in minutes
        int startMinutes = b.start.toWeekMinutes();                     // current start in week minutes
        int endMinutes = b.end.toWeekMinutes();                         // current end in week minutes
        int duration = endMinutes - startMinutes;                       // duration in minutes
        
        // Apply offset
        int newStartMinutes = startMinutes + offsetMinutes;             // new start with offset
        int newEndMinutes = newStartMinutes + duration;                 // preserve duration
        
        // Validate within week bounds (0 to 7*24*60-1 minutes)
        if (newStartMinutes < 0 || newEndMinutes >= 7 * 24 * 60) {
            throw new ConflictException("time out of week bounds");     // out of bounds
        }
        
        Types.WeeklyTime newStart = Types.WeeklyTime.fromWeekMinutes(newStartMinutes);
        Types.WeeklyTime newEnd = Types.WeeklyTime.fromWeekMinutes(newEndMinutes);
        
        List<Types.Booking> existing = store.getFacilityBookings(b.facility); // get peers
        if (hasOverlap(existing, newStart, newEnd, b.id)) {             // check conflicts
            throw new ConflictException("overlap");                    // conflict
        }
        b.start = newStart;                                             // apply update
        b.end = newEnd;                                                 // apply update
        return new Types.Interval(newStart, newEnd);                    // return new interval
    }

    // Helper: get facility name for a booking id; returns null if not found
    public String getBookingFacility(long bookingId) {
        Types.Booking b = store.getBooking(bookingId);                  // lookup booking
        return b == null ? null : b.facility;                           // return facility or null
    }

    // Helper: get all bookings for a facility
    public List<Types.Booking> getFacilityBookings(String facility) {
        return store.getFacilityBookings(facility);                     // delegate to store
    }

    // Query available non-booked intervals for a specific day of the week
    public List<Types.Interval> queryDay(String facility, Types.Day day) {
        List<Types.Booking> existing = store.getFacilityBookings(facility); // fetch bookings
        
        // Filter bookings for the requested day
        List<Types.Booking> dayBookings = new ArrayList<>();
        for (Types.Booking b : existing) {
            if (b.start.day == day) {                                   // booking is on requested day
                dayBookings.add(b);
            }
        }
        
        // Sort by start time within the day
        dayBookings.sort(Comparator.comparingInt(x -> x.start.toWeekMinutes()));
        
        List<Types.Interval> result = new ArrayList<>();                // result intervals
        Types.WeeklyTime cursor = new Types.WeeklyTime(day, 0, 0);      // start at 00:00 of requested day
        Types.WeeklyTime dayEnd = new Types.WeeklyTime(day, 23, 59);    // end at 23:59 of requested day
        
        // If no bookings, return the entire day as available
        if (dayBookings.isEmpty()) {
            result.add(new Types.Interval(cursor, dayEnd));
            return result;
        }
        
        for (Types.Booking b : dayBookings) {                           // iterate day bookings
            // Check if there's a gap before this booking
            if (b.start.toWeekMinutes() > cursor.toWeekMinutes()) {
                result.add(new Types.Interval(cursor, b.start));        // add free gap
            }
            // Move cursor to end of this booking
            cursor = b.end;
            if (cursor.toWeekMinutes() >= dayEnd.toWeekMinutes()) break; // reached end of day
        }
        
        // Add remaining time if any
        if (cursor.toWeekMinutes() < dayEnd.toWeekMinutes()) {
            result.add(new Types.Interval(cursor, dayEnd));             // add remaining free time
        }
        
        return result;                                                  // return free intervals
    }

    // Reset facility schedule for a specific day (idempotent operation)
    // Removes all bookings for the specified day
    // Returns count of removed bookings (0 if already empty or repeated call)
    public int resetDaySchedule(String facility, Types.Day day) {
        return store.removeBookingsForDay(facility, day);               // delegate to store
    }

    // Exception types to map to protocol errors
    public static final class ConflictException extends Exception { public ConflictException(String m){super(m);} }
    public static final class NotFoundException extends Exception { public NotFoundException(String m){super(m);} }
}
