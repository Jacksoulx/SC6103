/*
 * WireCodec.java
 * Purpose: Manual marshalling/unmarshalling helpers for the UDP protocol.
 * Design notes:
 * - All integers are encoded big-endian (network byte order).
 * - Header is fixed 16 bytes; helper methods write/read header and primitives.
 * - Strings are length-prefixed with uint16 length and UTF-8 bytes.
 * - Timestamps are 64-bit epochMillis (Java long) encoded big-endian.
 */

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WireCodec {

    /*
     * Header structure used when building or parsing a UDP message.
     */
    public static final class Header {
        public int version;     // uint16 on wire
        public int opCode;       // uint16 on wire
        public long requestId;   // uint32 on wire (stored as long to avoid sign)
        public long flags;       // uint32 on wire (stored as long)
        public int payloadLen;   // uint32 on wire, but fits in Java int
    }

    // Allocate a ByteBuffer with big-endian order for writing messages
    public static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity).order(Protocol.BYTE_ORDER); // ensure BE
    }

    // Wrap an existing byte array for reading with big-endian order
    public static ByteBuffer wrap(byte[] data) {
        return ByteBuffer.wrap(data).order(Protocol.BYTE_ORDER); // ensure BE
    }

    // Write header at position 0..15
    public static void writeHeader(ByteBuffer buf, Header h) {
        buf.putShort((short) (h.version & 0xFFFF));                // version uint16
        buf.putShort((short) (h.opCode & 0xFFFF));                 // opCode uint16
        buf.putInt((int) (h.requestId & 0xFFFFFFFFL));            // requestId uint32
        buf.putInt((int) (h.flags & 0xFFFFFFFFL));                // flags uint32
        buf.putInt(h.payloadLen);                                  // payload length uint32
    }

    // Read header from buffer starting at current position
    public static Header readHeader(ByteBuffer buf) {
        Header h = new Header();                                   // allocate header
        h.version = Short.toUnsignedInt(buf.getShort());           // read uint16 version
        h.opCode = Short.toUnsignedInt(buf.getShort());            // read uint16 opCode
        h.requestId = Integer.toUnsignedLong(buf.getInt());        // read uint32 requestId
        h.flags = Integer.toUnsignedLong(buf.getInt());            // read uint32 flags
        h.payloadLen = buf.getInt();                               // read uint32 payload length
        return h;                                                  // return parsed header
    }

    // Write a uint16 value
    public static void writeU16(ByteBuffer buf, int v) {
        buf.putShort((short) (v & 0xFFFF));                        // store as 16-bit
    }

    // Write a uint32 value
    public static void writeU32(ByteBuffer buf, long v) {
        buf.putInt((int) (v & 0xFFFFFFFFL));                       // store low 32 bits
    }

    // Write a uint64/signed long (used for timestamps and booking id)
    public static void writeI64(ByteBuffer buf, long v) {
        buf.putLong(v);                                            // store 64-bit big-endian
    }

    // Read helpers
    public static int readU16(ByteBuffer buf) { return Short.toUnsignedInt(buf.getShort()); }
    public static long readU32(ByteBuffer buf) { return Integer.toUnsignedLong(buf.getInt()); }
    public static long readI64(ByteBuffer buf) { return buf.getLong(); }

    // Write a length-prefixed UTF-8 string
    public static void writeString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);         // encode UTF-8 bytes
        if (bytes.length > 0xFFFF) throw new IllegalArgumentException("string too long"); // bound check
        writeU16(buf, bytes.length);                               // prefix with uint16 length
        buf.put(bytes);                                            // write bytes
    }

    // Read a length-prefixed UTF-8 string
    public static String readString(ByteBuffer buf) {
        int len = readU16(buf);                                    // read uint16 length
        byte[] bytes = new byte[len];                              // allocate
        buf.get(bytes);                                            // read bytes
        return new String(bytes, StandardCharsets.UTF_8);          // decode to String
    }

    // Compute total message buffer: header + payload size
    public static ByteBuffer newMessageBuffer(int payloadLength) {
        return allocate(Protocol.HEADER_LEN + payloadLength);      // allocate total buffer
    }

    private WireCodec() { /* utility */ }
}
