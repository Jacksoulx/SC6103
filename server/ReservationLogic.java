/*
 * ReservationLogic.java
 * Purpose: Implements business rules for booking, querying availability, and changing bookings.
 * Design notes:
 * - Booking allowed only if new interval does not overlap existing bookings for same facility.
 * - Change booking applies offset minutes and validates no overlap; otherwise returns conflict.
 */

import java.util.*;

public class ReservationLogic {
    private final FacilityStore store; // storage dependency

    public ReservationLogic(FacilityStore store) {
        this.store = store; // assign store
    }

    // Check if [start,end) overlaps any existing booking except optional excludeId
    private boolean hasOverlap(List<Types.Booking> bookings, long start, long end, Long excludeId) {
        for (Types.Booking b : bookings) {                              // iterate existing bookings
            if (excludeId != null && b.id == excludeId) continue;       // skip the booking being changed
            if (Math.max(b.startEpochMs, start) < Math.min(b.endEpochMs, end)) {
                return true;                                            // intervals overlap
            }
        }
        return false;                                                   // no overlap found
    }

    // Book a new interval; returns booking id or throws ConflictException
    public long book(String facility, String user, long startMs, long endMs) throws ConflictException {
        Types.Facility f = store.ensureFacility(facility);              // ensure facility exists
        List<Types.Booking> existing = store.getFacilityBookings(facility); // snapshot existing
        if (hasOverlap(existing, startMs, endMs, null)) {               // detect overlap
            throw new ConflictException("overlap");                    // conflict error
        }
        long id = store.newBookingId();                                 // generate new id
        Types.Booking b = new Types.Booking(id, facility, user, startMs, endMs); // create booking
        store.addBooking(b);                                            // persist booking
        return id;                                                      // return id
    }

    // Change booking by offset minutes; returns updated interval; may throw not found or conflict
    public Types.Interval change(long bookingId, int offsetMinutes) throws NotFoundException, ConflictException {
        Types.Booking b = store.getBooking(bookingId);                  // lookup booking
        if (b == null) throw new NotFoundException("booking");         // not found
        long duration = b.endEpochMs - b.startEpochMs;                  // compute duration
        long delta = offsetMinutes * 60_000L;                           // convert minutes to ms
        long newStart = b.startEpochMs + delta;                         // new start
        long newEnd = newStart + duration;                              // preserve duration
        List<Types.Booking> existing = store.getFacilityBookings(b.facility); // get peers
        if (hasOverlap(existing, newStart, newEnd, b.id)) {             // check conflicts
            throw new ConflictException("overlap");                    // conflict
        }
        b.startEpochMs = newStart;                                      // apply update
        b.endEpochMs = newEnd;                                          // apply update
        return new Types.Interval(newStart, newEnd);                    // return new interval
    }

    // Helper: get facility name for a booking id; returns null if not found
    public String getBookingFacility(long bookingId) {
        Types.Booking b = store.getBooking(bookingId);                  // lookup booking
        return b == null ? null : b.facility;                           // return facility or null
    }

    // Query available non-booked intervals for a given day [dayStart, dayEnd)
    public List<Types.Interval> queryDay(String facility, long dayStart, long dayEnd) {
        List<Types.Booking> existing = store.getFacilityBookings(facility); // fetch bookings
        existing.sort(Comparator.comparingLong(x -> x.startEpochMs));   // sort by start
        List<Types.Interval> result = new ArrayList<>();                // result intervals
        long cursor = dayStart;                                         // start scanning
        for (Types.Booking b : existing) {                              // iterate bookings
            if (b.endEpochMs <= dayStart || b.startEpochMs >= dayEnd) continue; // outside day window
            long start = Math.max(cursor, b.startEpochMs);              // overlap start within window
            if (start > cursor) {                                       // gap before booking
                result.add(new Types.Interval(cursor, Math.min(start, dayEnd))); // add free gap
            }
            cursor = Math.max(cursor, Math.min(b.endEpochMs, dayEnd));  // move cursor beyond booking
            if (cursor >= dayEnd) break;                                // end of day
        }
        if (cursor < dayEnd) {                                         // tail free gap
            result.add(new Types.Interval(cursor, dayEnd));             // add remaining free window
        }
        return result;                                                  // return free intervals
    }

    // Reset facility schedule for a specific day (idempotent operation)
    // Removes all bookings within [dayStart, dayEnd) range
    // Returns count of removed bookings (0 if already empty or repeated call)
    public int resetDaySchedule(String facility, long dayStart, long dayEnd) {
        return store.removeBookingsInRange(facility, dayStart, dayEnd); // delegate to store
    }

    // Exception types to map to protocol errors
    public static final class ConflictException extends Exception { public ConflictException(String m){super(m);} }
    public static final class NotFoundException extends Exception { public NotFoundException(String m){super(m);} }
}
