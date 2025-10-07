/*
 * Protocol.java
 * Purpose: Defines the UDP wire protocol constants, op codes, header structure, flags,
 *          error codes, and small helpers used across client and server.
 * Design notes:
 * - All RPC messages begin with a fixed 16-byte header in network byte order (big endian):
 *     0-1   uint16  version (=1)
 *     2-3   uint16  opCode
 *     4-7   uint32  requestId
 *     8-11  uint32  flags   (bit0: atMostOnce; bit1: isCallback)
 *     12-15 uint32  payloadLength (length of payload bytes following the header)
 * - Strings are encoded as: uint16 length (BE) + UTF-8 bytes.
 * - Timestamps use 64-bit epochMillis (Java long) encoded big-endian.
 * - No Java serialization is used. Manual marshalling/unmarshalling is implemented in WireCodec.
 */

import java.nio.ByteOrder;

public final class Protocol {
    // Protocol version
    public static final int VERSION = 1; // uint16 on wire

    // Op codes (uint16)
    public static final int OP_QUERY_AVAIL          = 0x0001;
    public static final int OP_BOOK                 = 0x0002;
    public static final int OP_CHANGE_BOOKING       = 0x0003;
    public static final int OP_MONITOR              = 0x0004;
    public static final int OP_CUSTOM_IDEMPOTENT    = 0x1001; // e.g., set display color
    public static final int OP_CUSTOM_NON_IDEMPOTENT= 0x1002; // e.g., increment counter / append audit

    // Error opcode marker: op | 0x8000
    public static final int OP_ERROR_MASK           = 0x8000;

    // Error codes (uint16), returned as payload: uint16 errCode + string message
    public static final int ERR_CONFLICT            = 1;  // booking conflict/overlap
    public static final int ERR_NOT_FOUND           = 2;  // booking not found
    public static final int ERR_BAD_REQUEST         = 3;  // malformed payload
    public static final int ERR_INTERNAL            = 4;  // server error

    // Flags (uint32)
    public static final int FLAG_AT_MOST_ONCE = 1 << 0; // client requests at-most-once semantics
    public static final int FLAG_IS_CALLBACK  = 1 << 1; // server→client monitor callback

    // Header length in bytes
    public static final int HEADER_LEN = 16;

    // Network byte order for all integers
    public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private Protocol() { /* no instantiation */ }
}
