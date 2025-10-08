/*
 * MonitorRegistry.java
 * Purpose: Tracks active monitor registrations for callback notifications.
 * Design notes:
 * - Each monitor entry stores client address/port, facility, and expiry timestamp.
 * - A sweeper removes expired entries.
 */

import java.net.*;
import java.util.List;
import java.util.ArrayList;

public class MonitorRegistry {
    public static final class Entry {
        public final InetAddress addr;    // client IP address
        public final int port;            // client UDP port
        public final String facility;     // facility being monitored
        public final long expiryEpochMs;  // expiry timestamp
        public Entry(InetAddress addr, int port, String facility, long expiryEpochMs) {
            this.addr = addr; this.port = port; this.facility = facility; this.expiryEpochMs = expiryEpochMs;
        }
    }

    private final List<Entry> entries = new ArrayList<>(); // in-memory list of monitors

    // Register a new monitor entry
    public synchronized void register(InetAddress addr, int port, String facility, long durationSeconds) {
        long expiry = System.currentTimeMillis() + durationSeconds * 1000L; // compute expiry time
        entries.add(new Entry(addr, port, facility, expiry));                // append entry
    }

    // Get snapshot of active monitors for a facility
    public synchronized List<Entry> getActiveFor(String facility) {
        long now = System.currentTimeMillis();           // current time
        List<Entry> out = new ArrayList<>();             // output list
        for (Entry e : entries) {                        // iterate entries
            if (e.expiryEpochMs > now && e.facility.equals(facility)) {
                out.add(e);                              // still active for facility
            }
        }
        return out;                                      // return active list
    }

    // Sweep expired entries
    public synchronized void sweepExpired() {
        long now = System.currentTimeMillis();           // current time
        entries.removeIf(e -> e.expiryEpochMs <= now);   // remove if expired
    }
}
