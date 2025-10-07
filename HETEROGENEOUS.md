# Heterogeneous Implementation: Java Server + C Client

## Overview
This project demonstrates **cross-language UDP RPC** with:
- **Server**: Java (ServerMain.java)
- **Client**: C (client_main.c)

The C client communicates with the Java server using a carefully designed binary wire protocol.

## Key Challenges & Solutions

### 1. Byte Order (Endianness)
**Challenge**: Different architectures may use different byte orders (little-endian vs big-endian).

**Solution**: 
- **Wire protocol**: All multi-byte integers transmitted in **network byte order (big-endian)**.
- **Java**: `ByteBuffer.order(ByteOrder.BIG_ENDIAN)` ensures big-endian encoding.
- **C**: Use `htons()`, `htonl()`, `ntohs()`, `ntohl()` for 16/32-bit conversions.
  - For 64-bit values, manually split into two 32-bit halves and convert each.

Example from `wire_codec.c`:
```c
/* Write int64 in network byte order (split into two uint32) */
int write_i64(uint8_t *buf, int64_t val) {
    uint32_t high = (uint32_t)((val >> 32) & 0xFFFFFFFFUL); /* upper 32 bits */
    uint32_t low  = (uint32_t)(val & 0xFFFFFFFFUL);         /* lower 32 bits */
    int offset = 0;
    offset += write_u32(buf + offset, high);                /* write high word first (BE) */
    offset += write_u32(buf + offset, low);                 /* write low word */
    return offset;                                          /* 8 bytes */
}
```

### 2. Struct Packing & Alignment
**Challenge**: C structs may have padding bytes inserted by compiler for alignment, causing size mismatches.

**Solution**: 
- **Never send C structs directly on wire**.
- Manually serialize each field byte-by-byte using helper functions.
- Header struct in C is only used as in-memory representation; `write_header()` serializes it field-by-field.

### 3. String Encoding
**Challenge**: C uses null-terminated strings; Java uses length-prefixed UTF-8.

**Solution**:
- Wire format: `uint16 length + UTF-8 bytes` (no null terminator on wire).
- **Java**: `WireCodec.writeString()` writes length prefix + `String.getBytes(UTF_8)`.
- **C**: `write_string()` writes length prefix + `strlen()` bytes; `read_string()` null-terminates after reading.

### 4. Socket API Differences
**Challenge**: Windows uses Winsock2; POSIX systems use BSD sockets.

**Solution**: Conditional compilation in C client:
```c
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "ws2_32.lib")
    typedef int socklen_t;
    #define close closesocket
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
#endif
```

Initialize Winsock on Windows:
```c
#ifdef _WIN32
    WSADATA wsa_data;
    WSAStartup(MAKEWORD(2, 2), &wsa_data);
#endif
```

### 5. Type Sizes
**Challenge**: `int`, `long` sizes vary across platforms.

**Solution**:
- Use fixed-width types: `uint8_t`, `uint16_t`, `uint32_t`, `int64_t` from `<stdint.h>`.
- Wire protocol explicitly specifies sizes (e.g., uint16 = 2 bytes, int64 = 8 bytes).

## Wire Protocol Compatibility

### Header (16 bytes)
```
Offset  Size  Field         Java Type       C Type
------  ----  -----         ---------       ------
0-1     2     version       short (BE)      uint16_t (htons)
2-3     2     opCode        short (BE)      uint16_t (htons)
4-7     4     requestId     int (BE)        uint32_t (htonl)
8-11    4     flags         int (BE)        uint32_t (htonl)
12-15   4     payloadLen    int (BE)        uint32_t (htonl)
```

### Example: BOOK Request Payload
```
Offset  Size    Field         Encoding
------  ----    -----         --------
0-1     2       facility_len  uint16 (BE)
2-N     N       facility      UTF-8 bytes
N+1-N+2 2       user_len      uint16 (BE)
N+3-M   M-N-2   user          UTF-8 bytes
M+1-M+8 8       start_ms      int64 (BE, high 32 bits first)
M+9-M+16 8      end_ms        int64 (BE, high 32 bits first)
```

The C client produces **identical byte sequences** as would a Java client for the same logical request.

## Testing Heterogeneous Communication

### Step 1: Start Java Server
```bash
scripts\run_server.bat --host 0.0.0.0 --port 9999 --atMostOnce=true
```

### Step 2: Build C Client
```bash
scripts\build_c_client.bat
```

### Step 3: Test C Client Operations
```bash
# Query availability
scripts\run_c_client.bat query --facility LabA --date 2025-10-10

# Book a facility
scripts\run_c_client.bat book --facility LabA --user alice --start 1728540000000 --end 1728543600000

# Change booking time
scripts\run_c_client.bat change --booking-id 1 --offset 60

# Monitor facility changes (in separate terminal)
scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000

# Reset schedule (idempotent - can run multiple times)
scripts\run_c_client.bat reset --facility LabA --day-start 1728518400000 --day-end 1728604800000
# Output: Schedule reset for facility=LabA: 1 booking(s) removed

# Run reset again (idempotent - same result)
scripts\run_c_client.bat reset --facility LabA --day-start 1728518400000 --day-end 1728604800000
# Output: Schedule reset for facility=LabA: 0 booking(s) removed

# Increment counter (non-idempotent)
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
# Output: Usage counter for facility=LabA => 1

# Run again to verify increment (non-idempotent - state changes)
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
# Output: Usage counter for facility=LabA => 2
```

**Result**: C client successfully communicates with Java server, proving cross-language interoperability.

### Step 4: Verify Byte-Level Compatibility
Enable server logging to see request bytes. The C client produces identical wire format as Java would.

## Implementation Files

### Java Components
- `common/Protocol.java` - Op codes, constants
- `common/WireCodec.java` - Marshalling with `ByteBuffer.order(BIG_ENDIAN)`
- `server/ServerMain.java` - UDP server loop
- `server/RequestRouter.java` - Request routing and at-most-once cache
- `server/ReservationLogic.java` - Business logic

### C Components
- `client/protocol.h` - Op codes, constants (mirrors Java)
- `client/wire_codec.h/c` - Marshalling with `htons/htonl`
- `client/client_main.c` - C client with Winsock2/POSIX support

## Idempotent vs Non-Idempotent Operations

### Idempotent Operation: Reset Schedule
**Implementation**: `CUSTOM_IDEMPOTENT` (OP_CUSTOM_IDEMPOTENT = 0x1001)

**Server-side** (`RequestRouter.java`):
```java
private byte[] onCustomIdem(...) {
    String facility = WireCodec.readString(in);
    long dayStart = WireCodec.readI64(in);
    long dayEnd = WireCodec.readI64(in);
    int removedCount = logic.resetDaySchedule(facility, dayStart, dayEnd);
    // Returns count of removed bookings
}
```

**Client-side** (`client_main.c`):
```c
void cmd_reset(...) {
    // Send: facility name + day start + day end
    // Receive: uint32 removed count
}
```

**Idempotent property**: 
- First call: Removes N bookings, returns N
- Second call: No bookings left, returns 0
- Result: Schedule is empty (same state)

### Non-Idempotent Operation: Usage Counter
**Implementation**: `CUSTOM_NON_IDEMPOTENT` (OP_CUSTOM_NON_IDEMPOTENT = 0x1002)

**Server-side** (`RequestRouter.java`):
```java
private byte[] onCustomNonIdem(...) {
    String facility = WireCodec.readString(in);
    long cur = facilityUsageCounters.getOrDefault(facility, 0L);
    cur += 1;  // Non-idempotent: state changes on each call
    facilityUsageCounters.put(facility, cur);
    // Returns new counter value
}
```

**Client-side** (`client_main.c`):
```c
void cmd_custom_incr(...) {
    // Send: facility name
    // Receive: int64 counter value
}
```

**Non-idempotent property**:
- First call: counter = 1
- Second call: counter = 2
- Third call: counter = 3
- Result: State changes with each invocation

**At-least-once risk**: If client retries due to packet loss, counter may increment multiple times for single logical request. Use `--atMostOnce 1` flag to enable server-side deduplication.

## Key Takeaways

1. **Manual marshalling is essential** for heterogeneous systems - no language-specific serialization.
2. **Network byte order** (big-endian) is the universal standard for multi-byte integers.
3. **Fixed-width types** prevent platform-specific size issues.
4. **Length-prefixed strings** avoid null-terminator ambiguity.
5. **Never send structs directly** - always serialize field-by-field.
6. **Test cross-language communication** to verify protocol compatibility.
7. **Idempotent operations** are safe to retry; **non-idempotent operations** require at-most-once semantics.
8. **All code comments in English** ensure international collaboration and maintainability.

This implementation follows the tutorial's requirement: "Use UDP socket primitives... no RMI or object serialization."
