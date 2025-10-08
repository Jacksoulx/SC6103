/*
 * ServerMain.java
 * Purpose: UDP server main loop. Receives requests, routes them, sends responses, and
 *          sends monitor callbacks on booking/changes. Supports at-most-once cache sweep
 *          and monitor sweep. Also supports simulated packet loss of responses.
 */

import java.net.*;
import java.nio.*;
import java.time.*;
import java.util.Random;
import java.util.Arrays;
import java.util.List;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        // Default config values
        String host = "0.0.0.0";                 // listen host
        int port = 9999;                          // listen port
        boolean atMostOnce = true;                // enable at-most-once cache
        double lossSim = 0.0;                     // probability to drop outbound responses

        // Parse simple CLI arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host": host = args[++i]; break;              // read host value
                case "--port": port = Integer.parseInt(args[++i]); break; // read port value
                case "--atMostOnce": atMostOnce = Boolean.parseBoolean(args[++i]); break; // read flag
                case "--lossSim": lossSim = Double.parseDouble(args[++i]); break; // loss simulation probability
            }
        }

        // Initialize components
        FacilityStore store = new FacilityStore();                         // in-memory storage
        ReservationLogic logic = new ReservationLogic(store);              // business logic
        MonitorRegistry monitors = new MonitorRegistry();                  // monitor registry
        RequestRouter router = new RequestRouter(logic, monitors, 60_000); // cache TTL 60s
        Random rnd = new Random();                                         // RNG for loss sim

        DatagramSocket sock = new DatagramSocket(new InetSocketAddress(host, port)); // bind UDP socket
        sock.setSoTimeout(500);                                            // timeout for periodic sweeps

        System.out.println("Server listening on " + host + ":" + port + " atMostOnce=" + atMostOnce + " lossSim=" + lossSim);

        byte[] buf = new byte[64 * 1024];                                 // receive buffer (max UDP payload)
        long lastSweep = System.currentTimeMillis();                       // last sweep time

        // Main loop
        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length); // prepare packet holder
                sock.receive(pkt);                                        // blocking receive with timeout

                // Copy the exact datagram bytes (pkt.getLength()) to a new array
                byte[] reqBytes = Arrays.copyOfRange(pkt.getData(), 0, pkt.getLength()); // request bytes slice

                // Parse header just for logging and flags; router will parse again (kept simple)
                WireCodec.Header hdr = WireCodec.readHeader(WireCodec.wrap(reqBytes)); // read header for log
                long t0 = System.currentTimeMillis();                      // start timing

                // Handle request and construct response
                byte[] resp = router.handle(pkt.getAddress(), pkt.getPort(), reqBytes, (hdr.flags & Protocol.FLAG_AT_MOST_ONCE) != 0); // route
                long elapsed = System.currentTimeMillis() - t0;            // elapsed time

                // Log request
                System.out.println("req id=" + hdr.requestId + " op=0x" + Integer.toHexString(hdr.opCode) + " elapsedMs=" + elapsed);

                // Simulate loss if configured
                if (rnd.nextDouble() < lossSim) {
                    System.out.println("[LOSS] Dropping response for req=" + hdr.requestId); // drop response
                } else {
                    // Send response
                    DatagramPacket rp = new DatagramPacket(resp, resp.length, pkt.getAddress(), pkt.getPort()); // build packet
                    sock.send(rp);                                        // sendto
                }

                // On booking or change, notify monitors via callback with QUERY_AVAIL result
                if (hdr.opCode == Protocol.OP_BOOK || hdr.opCode == Protocol.OP_CHANGE_BOOKING) {
                    try {
                        // Extract facility depending on opcode
                        String facility = null;                                                      // facility holder
                        ByteBuffer in = WireCodec.wrap(Arrays.copyOfRange(reqBytes, Protocol.HEADER_LEN, reqBytes.length)); // payload only
                        if (hdr.opCode == Protocol.OP_BOOK) {
                            facility = WireCodec.readString(in);                                    // BOOK carries facility string first
                        } else if (hdr.opCode == Protocol.OP_CHANGE_BOOKING) {
                            long bookingId = WireCodec.readI64(in);                                  // CHANGE carries bookingId first
                            facility = logic.getBookingFacility(bookingId);                          // lookup facility from store
                        }
                        if (facility != null) {
                            // For weekly schedule, send callback for all days that have bookings
                            // Find all days with bookings for this facility
                            java.util.Set<Types.Day> affectedDays = new java.util.HashSet<>();
                            List<Types.Booking> facilityBookings = logic.getFacilityBookings(facility);
                            for (Types.Booking booking : facilityBookings) {
                                affectedDays.add(booking.start.day);
                            }
                            
                            // Send callback for each affected day
                            for (Types.Day day : affectedDays) {
                                List<Types.Interval> ivals = logic.queryDay(facility, day);     // compute intervals for this day
                                int count = ivals.size();
                                int payloadLen = 2 + 1 + count * 6;                             // u16 + day + N*(WeeklyTime,WeeklyTime)
                                ByteBuffer out = WireCodec.newMessageBuffer(payloadLen);
                                WireCodec.Header ch = new WireCodec.Header();
                                ch.version = Protocol.VERSION;
                                ch.opCode = Protocol.OP_QUERY_AVAIL;                             // same op for callback body
                                ch.requestId = 0;                                                // callbacks need no dedupe by id
                                ch.flags = Protocol.FLAG_IS_CALLBACK;                            // mark as callback
                                ch.payloadLen = payloadLen;
                                WireCodec.writeHeader(out, ch);
                                WireCodec.writeU16(out, count);
                                out.put((byte) day.value);                                       // write day
                                for (Types.Interval iv : ivals) {
                                    WireCodec.writeWeeklyTime(out, iv.start);
                                    WireCodec.writeWeeklyTime(out, iv.end);
                                }
                                byte[] cb = out.array();
                                for (MonitorRegistry.Entry m : monitors.getActiveFor(facility)) {
                                    DatagramPacket mp = new DatagramPacket(cb, cb.length, m.addr, m.port);
                                    if (rnd.nextDouble() >= lossSim) sock.send(mp);                  // send callback unless dropped
                                }
                            }
                        }
                    } catch (Exception ignore) { /* ignore callback errors to not impact main flow */ }
                }

            } catch (SocketTimeoutException ste) {
                // Periodic maintenance on timeout
                long now = System.currentTimeMillis();                    // current time
                if (now - lastSweep > 1000) {                             // every second
                    router.sweepCache();                                  // sweep cache
                    monitors.sweepExpired();                              // sweep monitors
                    lastSweep = now;                                      // update sweep ts
                }
            }
        }
    }
}
