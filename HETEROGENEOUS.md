# 🌐 Heterogeneous Implementation: Java Server + C Client

[![Cross-Language](https://img.shields.io/badge/Cross--Language-Java%20↔%20C-orange.svg)]()
[![Protocol](https://img.shields.io/badge/Protocol-Custom%20Binary-red.svg)]()
[![Networking](https://img.shields.io/badge/Network-UDP%20RPC-blue.svg)]()

## 🎯 Overview

This project demonstrates **heterogeneous cross-language UDP RPC** with:

| Component | Language | Technology Stack |
|-----------|----------|------------------|
| **Server** | Java | DatagramSocket, ByteBuffer, Manual Marshalling |
| **Client** | C | Winsock2/POSIX, Manual byte operations |
| **Protocol** | Binary | Custom wire format, Network byte order |

The C client communicates seamlessly with the Java server using a carefully designed binary wire protocol that handles all cross-language compatibility challenges.

## 🔧 Key Challenges & Solutions

### 1️⃣ Byte Order (Endianness)
**🚨 Challenge**: Different architectures use different byte orders (little-endian vs big-endian).

**✅ Solution**: 
- **Wire Protocol Standard**: All multi-byte integers use **network byte order (big-endian)**
- **Java Implementation**: `ByteBuffer.order(ByteOrder.BIG_ENDIAN)` ensures consistent encoding
- **C Implementation**: Platform-neutral conversion using:
  - `htons()`, `htonl()` for 16/32-bit integers  
  - `ntohs()`, `ntohl()` for reading
  - Custom 64-bit handling (split into two 32-bit halves)

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

### 2️⃣ Struct Packing & Alignment
**🚨 Challenge**: C compiler padding can cause struct size mismatches between languages.

**✅ Solution**: 
- **🚫 Never send structs directly** over the wire
- **📦 Field-by-field serialization** using dedicated marshalling functions
- **🏗️ In-memory structs** used only for convenience; `write_header()` handles wire format

### 3️⃣ String Encoding
**🚨 Challenge**: C (null-terminated) vs Java (length-prefixed UTF-8) string differences.

**✅ Solution**:
- **Wire Format**: `uint16 length + UTF-8 bytes` (no null terminator)
- **Java**: `WireCodec.writeString()` → length prefix + `String.getBytes(UTF_8)`
- **C**: `write_string()` → length prefix + `strlen()` bytes, `read_string()` null-terminates

### 4️⃣ Socket API Differences
**🚨 Challenge**: Platform-specific socket APIs (Winsock2 vs BSD sockets).

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

### Example: BOOK Request Payload (Weekly Schedule)
```
Offset  Size    Field         Encoding
------  ----    -----         --------
0-1     2       facility_len  uint16 (BE)
2-N     N       facility      UTF-8 bytes
N+1-N+2 2       user_len      uint16 (BE)
N+3-M   M-N-2   user          UTF-8 bytes
M+1     1       start_day     uint8 (0-6: Monday-Sunday)
M+2     1       start_hour    uint8 (0-23)
M+3     1       start_minute  uint8 (0-59)
M+4     1       end_day       uint8 (0-6: Monday-Sunday)
M+5     1       end_hour      uint8 (0-23)
M+6     1       end_minute    uint8 (0-59)
```

**Total payload reduction**: Old format ~20+ bytes → New format ~12+ bytes

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

### Step 3: Test C Client Operations (Weekly Schedule)
```bash
# Query availability for specific day
scripts\run_c_client.bat query --facility LabA --day Monday
# Output: Available intervals for Monday: 1
#         Monday 00:00 - Monday 23:59

# Book a facility using weekly time format  
scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
# Output: Booking created: id=1 for LabA from Monday 09:00 to Monday 10:30

# Change booking time by offset
scripts\run_c_client.bat change --booking-id 1 --offset 60
# Output: Booking changed: new time Monday 10:00 to Monday 11:30

# Monitor facility changes (in separate terminal)
scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000
# Waits for callbacks when bookings change

# Reset day schedule (idempotent - can run multiple times)
scripts\run_c_client.bat reset --facility LabA --day Monday
# Output: Schedule reset for facility=LabA on Monday: 1 booking(s) removed

# Run reset again (idempotent - same result)
scripts\run_c_client.bat reset --facility LabA --day Monday
# Output: Schedule reset for facility=LabA on Monday: 0 booking(s) removed

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

## 🔄 Idempotent vs Non-Idempotent Operations

### ✅ Idempotent Operation: Reset Day Schedule
**Implementation**: `CUSTOM_IDEMPOTENT` (OP_CUSTOM_IDEMPOTENT = 0x1001)

**Server-side** (`RequestRouter.java`):
```java
private byte[] onCustomIdem(...) {
    String facility = WireCodec.readString(in);
    Day day = Day.values()[WireCodec.readU8(in)];
    int removedCount = logic.resetDaySchedule(facility, day);
    // Returns count of removed bookings for the specified day
}
```

**Client-side** (`client_main.c`):
```c
void cmd_reset(...) {
    // Send: facility name + day (1 byte)
    // Receive: uint32 removed count
}
```

**Idempotent property**: 
- First call: Removes N bookings for specified day, returns N
- Second call: No bookings left for that day, returns 0
- Result: Day schedule is empty (same state regardless of repetition)

### ⚠️ Non-Idempotent Operation: Usage Counter
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

## 🎓 Key Takeaways

### 🔧 Technical Principles
1. **Manual marshalling is essential** for heterogeneous systems - no language-specific serialization
2. **Network byte order** (big-endian) is the universal standard for multi-byte integers
3. **Fixed-width types** prevent platform-specific size issues
4. **Length-prefixed strings** avoid null-terminator ambiguity
5. **Never send structs directly** - always serialize field-by-field

### 🌐 Cross-Language Best Practices
6. **Test cross-language communication** extensively to verify protocol compatibility
7. **Use consistent data representation** across all implementations
8. **Document wire format thoroughly** for future maintenance

### 🔄 Distributed Systems Concepts
9. **Idempotent operations** are safe to retry; **non-idempotent operations** require at-most-once semantics
10. **All code comments in English** ensure international collaboration and maintainability

---

**✅ This implementation successfully demonstrates pure UDP socket programming without RMI or object serialization, meeting all heterogeneous distributed system requirements.**
