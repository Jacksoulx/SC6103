/*
 * Types.java
 * Purpose: Defines DTOs used by client and server: Facility, Booking, Interval.
 * Design notes:
 * - These are simple in-memory structures; no Java serialization is used.
 * - The wire format for these objects is defined and encoded/decoded in WireCodec.
 * - Updated to use weekly schedule format instead of timestamps.
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public final class Types {
    /*
     * Day enumeration for weekly schedule
     */
    public enum Day {
        MONDAY(0), TUESDAY(1), WEDNESDAY(2), THURSDAY(3), 
        FRIDAY(4), SATURDAY(5), SUNDAY(6);
        
        public final int value;
        
        Day(int value) {
            this.value = value;
        }
        
        public static Day fromValue(int value) {
            for (Day day : values()) {
                if (day.value == value) return day;
            }
            throw new IllegalArgumentException("Invalid day value: " + value);
        }
    }
    
    /*
     * WeeklyTime - represents time within a week as Day/Hour/Minute
     */
    public static final class WeeklyTime {
        public final Day day;        // day of week (Monday=0 to Sunday=6)
        public final int hour;       // hour (0-23)
        public final int minute;     // minute (0-59)
        
        public WeeklyTime(Day day, int hour, int minute) {
            if (hour < 0 || hour > 23) throw new IllegalArgumentException("Invalid hour: " + hour);
            if (minute < 0 || minute > 59) throw new IllegalArgumentException("Invalid minute: " + minute);
            this.day = day;
            this.hour = hour;
            this.minute = minute;
        }
        
        // Convert to minutes since Monday 00:00 for comparison
        public int toWeekMinutes() {
            return day.value * 24 * 60 + hour * 60 + minute;
        }
        
        // Create from minutes since Monday 00:00
        public static WeeklyTime fromWeekMinutes(int weekMinutes) {
            int dayValue = weekMinutes / (24 * 60);
            int remainingMinutes = weekMinutes % (24 * 60);
            int hour = remainingMinutes / 60;
            int minute = remainingMinutes % 60;
            return new WeeklyTime(Day.fromValue(dayValue), hour, minute);
        }
        
        @Override
        public String toString() {
            return String.format("%s %02d:%02d", day, hour, minute);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WeeklyTime)) return false;
            WeeklyTime other = (WeeklyTime) obj;
            return day == other.day && hour == other.hour && minute == other.minute;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(day, hour, minute);
        }
    }

    /*
     * Interval - represents a time interval within a week
     */
    public static final class Interval {
        public final WeeklyTime start; // start time within week
        public final WeeklyTime end;   // end time within week

        public Interval(WeeklyTime start, WeeklyTime end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "Interval{" + start + " - " + end + "}";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Interval)) return false;
            Interval other = (Interval) obj;
            return start.equals(other.start) && end.equals(other.end);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(start, end);
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
        public final long id;           // booking id (uint64 on the wire)
        public final String facility;   // facility name this booking is for
        public final String user;       // user who made the booking
        public WeeklyTime start;        // start time within week; mutable for change
        public WeeklyTime end;          // end time within week; mutable for change

        public Booking(long id, String facility, String user, WeeklyTime start, WeeklyTime end) {
            this.id = id;               // assign booking id
            this.facility = facility;   // facility this booking belongs to
            this.user = user;           // booking user
            this.start = start;         // start time
            this.end = end;             // end time
        }

        @Override
        public String toString() {
            return "Booking{" + id + "," + facility + "," + user + "," + start + "," + end + "}";
        }
    }

    private Types() { /* utility holder */ }
}
