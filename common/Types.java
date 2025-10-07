/*
 * Types.java
 * Purpose: Defines DTOs used by client and server: Facility, Booking, Interval.
 * Design notes:
 * - These are simple in-memory structures; no Java serialization is used.
 * - The wire format for these objects is defined and encoded/decoded in WireCodec.
 */

import java.util.*;

public final class Types {
    /*
     * Interval
     * Represents a half-open time interval [startEpochMs, endEpochMs).
     */
    public static final class Interval {
        public final long startEpochMs; // start timestamp in ms since epoch
        public final long endEpochMs;   // end timestamp in ms since epoch

        public Interval(long startEpochMs, long endEpochMs) {
            this.startEpochMs = startEpochMs; // assign start time
            this.endEpochMs = endEpochMs;     // assign end time
        }

        @Override
        public String toString() {
            return "Interval{" + startEpochMs + "," + endEpochMs + "}"; // simple debug string
        }
    }

    /*
     * Facility
     * Holds a facility name and in-memory booking calendar.
     * For simplicity we store existing bookings as a list; production systems would use an index/tree.
     */
    public static final class Facility {
        public final String name;              // unique facility name
        public final List<Booking> bookings;   // existing bookings for this facility

        public Facility(String name) {
            this.name = name;                      // set name
            this.bookings = new ArrayList<>();     // initialize empty bookings list
        }
    }

    /*
     * Booking
     * Represents a booking entry stored by the server.
     */
    public static final class Booking {
        public final long id;       // booking id (uint64 on the wire)
        public final String facility; // facility name this booking is for
        public final String user;     // user who made the booking
        public long startEpochMs;     // start time; mutable for change
        public long endEpochMs;       // end time; mutable for change

        public Booking(long id, String facility, String user, long startEpochMs, long endEpochMs) {
            this.id = id;                         // assign booking id
            this.facility = facility;             // facility this booking belongs to
            this.user = user;                     // booking user
            this.startEpochMs = startEpochMs;     // start timestamp
            this.endEpochMs = endEpochMs;         // end timestamp
        }

        @Override
        public String toString() {
            return "Booking{" + id + "," + facility + "," + user + "," + startEpochMs + "," + endEpochMs + "}"; // debug
        }
    }

    private Types() { /* utility holder */ }
}
