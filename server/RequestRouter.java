/*
 * RequestRouter.java
 * Purpose: Parses UDP requests, routes to business logic, applies at-most-once cache,
 *          and builds responses. Also emits monitor callbacks.
 * Design notes:
 * - Stateless decode/encode with WireCodec; minimal shared state via dependencies.
 * - At-most-once: cache maps requestId to response bytes for a short TTL.
 */

import java.net.*;
import java.nio.*;
import java.util.*;

public class RequestRouter {
    private final ReservationLogic logic;            // business logic
    private final MonitorRegistry monitors;          // registry for callbacks
    private final Random rnd = new Random();         // used for loss simulation in server

    // Simple at-most-once cache entry
    private static final class CacheEntry {
        final byte[] response;   // full datagram response bytes
        final long expiryMs;     // expiry timestamp for LRU/TTL
        CacheEntry(byte[] response, long expiryMs) { this.response = response; this.expiryMs = expiryMs; }
    }

    private final Map<Long, CacheEntry> amoCache = new HashMap<>(); // requestId -> cached response
    private final long cacheTtlMs;                                   // cache time to live

    public RequestRouter(ReservationLogic logic, MonitorRegistry monitors, long cacheTtlMs) {
        this.logic = logic; this.monitors = monitors; this.cacheTtlMs = cacheTtlMs; // assign dependencies
    }

    // Sweep at-most-once cache
    public synchronized void sweepCache() {
        long now = System.currentTimeMillis();                             // current time
        amoCache.entrySet().removeIf(e -> e.getValue().expiryMs <= now);   // remove expired
    }

    // Handle a single request and return a response datagram
    public synchronized byte[] handle(InetAddress clientAddr, int clientPort, byte[] request, boolean atMostOnceFlag)
    {
        ByteBuffer in = WireCodec.wrap(request);                           // wrap input buffer
        WireCodec.Header hdr = WireCodec.readHeader(in);                   // parse header
        byte[] payload = new byte[hdr.payloadLen];                         // payload bytes array
        in.get(payload);                                                   // copy payload

        // If at-most-once and cached, return cached response
        CacheEntry cached = null;                                          // holder
        if ((hdr.flags & Protocol.FLAG_AT_MOST_ONCE) != 0) {               // check flag bit
            cached = amoCache.get(hdr.requestId);                          // lookup cache
            if (cached != null) return cached.response;                    // return cached response directly
        }

        // Route by opCode
        byte[] response;
        try {
            switch (hdr.opCode) {
                case Protocol.OP_QUERY_AVAIL:
                    response = onQuery(clientAddr, clientPort, hdr, payload); break; // handle query
                case Protocol.OP_BOOK:
                    response = onBook(clientAddr, clientPort, hdr, payload); break;  // handle booking
                case Protocol.OP_CHANGE_BOOKING:
                    response = onChange(clientAddr, clientPort, hdr, payload); break;// handle change
                case Protocol.OP_MONITOR:
                    response = onMonitor(clientAddr, clientPort, hdr, payload); break;// handle monitor
                case Protocol.OP_CUSTOM_IDEMPOTENT:
                    response = onCustomIdem(clientAddr, clientPort, hdr, payload); break; // idempotent
                case Protocol.OP_CUSTOM_NON_IDEMPOTENT:
                    response = onCustomNonIdem(clientAddr, clientPort, hdr, payload); break; // non-idempotent
                default:
                    response = error(hdr, Protocol.ERR_BAD_REQUEST, "unknown opcode"); // error for unknown
            }
        } catch (Exception ex) {
            response = error(hdr, Protocol.ERR_INTERNAL, ex.getMessage() == null ? "error" : ex.getMessage()); // generic error
        }

        // Store in at-most-once cache if requested
        if ((hdr.flags & Protocol.FLAG_AT_MOST_ONCE) != 0) {
            amoCache.put(hdr.requestId, new CacheEntry(response, System.currentTimeMillis() + cacheTtlMs)); // cache response
        }
        return response; // return encoded response
    }

    // Build an error response buffer for given header
    private byte[] error(WireCodec.Header reqHdr, int errCode, String message) {
        byte[] msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8); // encode message
        int payloadLen = 2 + 2 + msgBytes.length;                                    // uint16 err + str(u16+bytes)
        ByteBuffer out = WireCodec.newMessageBuffer(payloadLen);                     // allocate buffer
        WireCodec.Header h = new WireCodec.Header();                                 // new header
        h.version = Protocol.VERSION;                                                // version
        h.opCode = (reqHdr.opCode | Protocol.OP_ERROR_MASK) & 0xFFFF;               // error opcode
        h.requestId = reqHdr.requestId;                                             // same request id
        h.flags = reqHdr.flags;                                                     // same flags
        h.payloadLen = payloadLen;                                                  // payload length
        WireCodec.writeHeader(out, h);                                              // write header
        WireCodec.writeU16(out, errCode);                                           // write error code
        WireCodec.writeString(out, message);                                        // write message
        return out.array();                                                         // return bytes
    }

    // Helpers: parse a date (ms) or truncate to day as needed are kept external to router for simplicity (client will send ms).

    // onQuery: req payload = string facility + i64 dayStart + i64 dayEnd; resp = u16 count + [i64 start,i64 end]*
    private byte[] onQuery(InetAddress addr, int port, WireCodec.Header reqHdr, byte[] payload) {
        ByteBuffer in = WireCodec.wrap(payload);                   // wrap payload
        String facility = WireCodec.readString(in);                // read facility
        long dayStart = WireCodec.readI64(in);                     // read day start
        long dayEnd = WireCodec.readI64(in);                       // read day end
        List<Types.Interval> ivals = logic.queryDay(facility, dayStart, dayEnd); // business call

        int count = ivals.size();                                  // number of intervals
        int payloadLen = 2 + count * 16;                           // u16 count + each has 2x i64
        ByteBuffer out = WireCodec.newMessageBuffer(payloadLen);   // allocate
        WireCodec.Header h = new WireCodec.Header();               // header
        h.version = Protocol.VERSION; h.opCode = reqHdr.opCode; h.requestId = reqHdr.requestId; h.flags = reqHdr.flags; h.payloadLen = payloadLen; // fill
        WireCodec.writeHeader(out, h);                             // write header
        WireCodec.writeU16(out, count);                            // write count
        for (Types.Interval iv : ivals) {                          // for each interval
            WireCodec.writeI64(out, iv.startEpochMs);              // write start
            WireCodec.writeI64(out, iv.endEpochMs);                // write end
        }
        return out.array();                                        // return buffer bytes
    }

    // onBook: req payload = str facility + str user + i64 start + i64 end; resp = i64 bookingId
    private byte[] onBook(InetAddress addr, int port, WireCodec.Header reqHdr, byte[] payload) throws ReservationLogic.ConflictException {
        ByteBuffer in = WireCodec.wrap(payload);                   // wrap
        String facility = WireCodec.readString(in);                // facility
        String user = WireCodec.readString(in);                    // user
        long start = WireCodec.readI64(in);                        // start ms
        long end = WireCodec.readI64(in);                          // end ms
        long id = logic.book(facility, user, start, end);          // attempt booking
        ByteBuffer out = WireCodec.newMessageBuffer(8);            // payload length 8 for i64
        WireCodec.Header h = new WireCodec.Header();               // header
        h.version = Protocol.VERSION; h.opCode = reqHdr.opCode; h.requestId = reqHdr.requestId; h.flags = reqHdr.flags; h.payloadLen = 8; // fill
        WireCodec.writeHeader(out, h);                             // write header
        WireCodec.writeI64(out, id);                               // write id
        // Notify monitors about availability change for this facility via callbacks would be sent by server main loop using monitors registry.
        return out.array();                                        // return response bytes
    }

    // onChange: req payload = i64 bookingId + i32 offsetMinutes; resp = i64 start + i64 end
    private byte[] onChange(InetAddress addr, int port, WireCodec.Header reqHdr, byte[] payload) throws ReservationLogic.NotFoundException, ReservationLogic.ConflictException {
        ByteBuffer in = WireCodec.wrap(payload);                   // wrap
        long bookingId = WireCodec.readI64(in);                    // id
        int offsetMinutes = (int) WireCodec.readU32(in);           // read as uint32 -> int
        Types.Interval updated = logic.change(bookingId, offsetMinutes); // apply change
        ByteBuffer out = WireCodec.newMessageBuffer(16);           // two i64 values
        WireCodec.Header h = new WireCodec.Header();               // header
        h.version = Protocol.VERSION; h.opCode = reqHdr.opCode; h.requestId = reqHdr.requestId; h.flags = reqHdr.flags; h.payloadLen = 16; // fill
        WireCodec.writeHeader(out, h);                             // write
        WireCodec.writeI64(out, updated.startEpochMs);             // start
        WireCodec.writeI64(out, updated.endEpochMs);               // end
        return out.array();                                        // bytes
    }

    // onMonitor: req payload = str facility + u32 windowSeconds + u32 clientCallbackPort; resp = u16 ok(=1)
    private byte[] onMonitor(InetAddress addr, int port, WireCodec.Header reqHdr, byte[] payload) {
        ByteBuffer in = WireCodec.wrap(payload);                   // wrap
        String facility = WireCodec.readString(in);                // facility
        long windowSeconds = WireCodec.readU32(in);                // requested window seconds
        int callbackPort = (int) WireCodec.readU32(in);            // client callback UDP port
        monitors.register(addr, callbackPort, facility, windowSeconds); // register monitor
        ByteBuffer out = WireCodec.newMessageBuffer(2);            // ok flag
        WireCodec.Header h = new WireCodec.Header();               // header
        h.version = Protocol.VERSION; h.opCode = reqHdr.opCode; h.requestId = reqHdr.requestId; h.flags = reqHdr.flags; h.payloadLen = 2; // fill
        WireCodec.writeHeader(out, h);                             // write header
        WireCodec.writeU16(out, 1);                                // write ok=1
        return out.array();                                        // bytes
    }

    // Custom idempotent: reset facility schedule for a specific day. Repeated calls yield same result; idempotent.
    private byte[] onCustomIdem(InetAddress addr, int port, WireCodec.Header reqHdr, byte[] payload) {
        ByteBuffer in = WireCodec.wrap(payload);                   // wrap
        String facility = WireCodec.readString(in);                // facility name
        long dayStart = WireCodec.readI64(in);                     // day start timestamp
        long dayEnd = WireCodec.readI64(in);                       // day end timestamp
        int removedCount = logic.resetDaySchedule(facility, dayStart, dayEnd); // reset schedule (idempotent)
        ByteBuffer out = WireCodec.newMessageBuffer(4);            // u32 for removed count
        WireCodec.Header h = new WireCodec.Header();               // header
        h.version = Protocol.VERSION; h.opCode = reqHdr.opCode; h.requestId = reqHdr.requestId; h.flags = reqHdr.flags; h.payloadLen = 4; // fill
        WireCodec.writeHeader(out, h);                             // write header
        WireCodec.writeU32(out, removedCount);                     // write count of removed bookings
        return out.array();                                        // bytes
    }

    // Custom non-idempotent: increment usage counter; tracks how many times a facility has been accessed (non-idempotent)
    private final Map<String, Long> facilityUsageCounters = new HashMap<>(); // facility -> usage counter
    private byte[] onCustomNonIdem(InetAddress addr, int port, WireCodec.Header reqHdr, byte[] payload) {
        ByteBuffer in = WireCodec.wrap(payload);                   // wrap
        String facility = WireCodec.readString(in);                // facility
        long cur = facilityUsageCounters.getOrDefault(facility, 0L); // read current usage count
        cur += 1;                                                  // increment usage counter (non-idempotent)
        facilityUsageCounters.put(facility, cur);                  // store updated count
        ByteBuffer out = WireCodec.newMessageBuffer(8);            // return new value
        WireCodec.Header h = new WireCodec.Header();               // header
        h.version = Protocol.VERSION; h.opCode = reqHdr.opCode; h.requestId = reqHdr.requestId; h.flags = reqHdr.flags; h.payloadLen = 8; // fill
        WireCodec.writeHeader(out, h);                             // write header
        WireCodec.writeI64(out, cur);                              // write usage counter value
        return out.array();                                        // bytes
    }
}
