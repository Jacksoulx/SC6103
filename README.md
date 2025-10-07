# Distributed Facility Booking System (UDP)

This project implements a UDP-based client/server system with manual binary marshalling, supporting:

- Query facility availability
- Book a facility
- Change a booking by offset (± minutes)
- Monitor availability via server→client UDP callbacks
- Two custom operations:
  - **Idempotent**: Reset facility schedule for a specific day (repeated calls yield same result)
  - **Non-idempotent**: Increment facility usage counter (repeated calls change state)
- Demonstrates at-least-once and at-most-once semantics over UDP
- **Heterogeneous implementation**: Java server + C client (demonstrates cross-language RPC)

## Build & Run (Windows)

### Java Server
- Start server:
  ```
  scripts\run_server.bat --host 0.0.0.0 --port 9999 --atMostOnce=true --lossSim=0.2
  ```

### C Client
1. Build C client (requires MinGW gcc):
   ```
   scripts\build_c_client.bat
   ```

2. Run C client commands:
   - Query: `scripts\run_c_client.bat query --facility LabA --date 2025-10-10`
   - Book: `scripts\run_c_client.bat book --facility LabA --user bob --start 1728540000000 --end 1728543600000`
   - Change booking: `scripts\run_c_client.bat change --booking-id 1 --offset 60`
   - Monitor: `scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000`
   - Reset schedule (idempotent): `scripts\run_c_client.bat reset --facility LabA --day-start 1728518400000 --day-end 1728604800000`
   - Usage counter (non-idempotent): `scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1`

**Note**: C client uses manual byte-order conversion with `htons/htonl/ntohs/ntohl` and Winsock2 on Windows.

## Project Structure

```
/project
  /common              # Shared protocol definitions (Java)
    Protocol.java      # Op codes, flags, constants
    Types.java         # DTOs: Facility, Booking, Interval
    WireCodec.java     # Manual marshalling with ByteBuffer (big-endian)
  /server              # Java server
    FacilityStore.java       # In-memory storage
    ReservationLogic.java    # Business rules (booking, overlap detection)
    MonitorRegistry.java     # Callback registration
    RequestRouter.java       # Request routing, at-most-once cache
    ServerMain.java          # UDP server main loop
  /client              # C client
    protocol.h         # Op codes (mirrors Java)
    wire_codec.h/c     # Manual marshalling with htons/htonl
    client_main.c      # UDP client with Winsock2/POSIX
    Makefile           # Build script
  /scripts
    run_server.bat           # Start Java server
    build_c_client.bat       # Build C client
    run_c_client.bat         # Run C client
  README.md
  HETEROGENEOUS.md     # Detailed cross-language implementation notes
```

## Protocol

Header (16 bytes, big-endian):
- 0-1: uint16 version (=1)
- 2-3: uint16 opCode
- 4-7: uint32 requestId
- 8-11: uint32 flags (bit0: atMostOnce; bit1: isCallback)
- 12-15: uint32 payloadLength

Strings: uint16 length + UTF-8 bytes
Timestamps: int64 epochMillis (8 bytes, big-endian)

Op codes: see `common/Protocol.java` or `client/protocol.h`.

## Notes

- No Java serialization/RMI/CORBA used. Only DatagramSocket/DatagramPacket.
- Server loop supports simulated loss via `--lossSim`.
- At-most-once cache TTL ~60s; request IDs are monotonic per client process.
- Inline comments document line-level logic.
- All code comments are in English for international collaboration.
- Dynamic facility creation: facilities are created on first use (no pre-registration needed).

## Demo: At-least-once vs At-most-once

### 1. At-least-once with duplicates (non-idempotent op)
Start server with response loss simulation (drops ~20% responses):
```
scripts\run_server.bat --lossSim=0.2 --atMostOnce=false
```

Run non-idempotent increment with C client:
```
scripts\run_c_client.bat custom-incr --facility LabA --retries 5 --atMostOnce 0
```
Repeat the command multiple times. Observe counter may increase by >1 per invocation due to retries.

### 2. At-most-once prevents duplicates
Stop server and restart with at-most-once cache enabled:
```
scripts\run_server.bat --lossSim=0.2 --atMostOnce=true
```

Run with at-most-once flag:
```
scripts\run_c_client.bat custom-incr --facility LabA --retries 5 --atMostOnce 1
```
Despite retries, counter increments by exactly 1 per logical invocation (server deduplicates by requestId).

### 3. Basic operations demo
Start Java server:
```
scripts\run_server.bat --lossSim=0.1
```

Build and run C client:
```
scripts\build_c_client.bat
scripts\run_c_client.bat query --facility LabA --date 2025-10-10
scripts\run_c_client.bat book --facility LabA --user alice --start 1728540000000 --end 1728543600000
scripts\run_c_client.bat change --booking-id 1 --offset 60
scripts\run_c_client.bat monitor --facility LabA --duration 30 --callback-port 10000
scripts\run_c_client.bat reset --facility LabA --day-start 1728518400000 --day-end 1728604800000
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
```
Observe C client (with manual `htons/htonl` byte-order conversion) successfully communicates with Java server.

## Supported Operations

### Core Operations
1. **QUERY_AVAIL** - Query facility availability for a specific day
   - Returns list of available time intervals
   - Idempotent operation

2. **BOOK** - Book a facility for a time range
   - Parameters: facility name, user, start/end timestamps
   - Returns booking ID
   - Triggers monitor callbacks if registered

3. **CHANGE_BOOKING** - Modify booking time by offset
   - Parameters: booking ID, offset in minutes (can be negative)
   - Returns new start/end timestamps
   - Validates no conflicts with other bookings
   - Triggers monitor callbacks if registered

4. **MONITOR** - Register for facility change notifications
   - Server sends UDP callbacks to client when bookings change
   - Client listens on specified callback port
   - Callbacks contain updated availability information

### Custom Operations

5. **CUSTOM_IDEMPOTENT** - Reset facility schedule (idempotent)
   - Removes all bookings within specified day range
   - Returns count of removed bookings
   - **Idempotent**: Repeated calls yield same result (empty schedule)
   - Use case: Daily/weekly schedule reset
   - Command: `reset --facility LabA --day-start <ms> --day-end <ms>`

6. **CUSTOM_NON_IDEMPOTENT** - Increment usage counter (non-idempotent)
   - Increments facility usage counter by 1
   - Returns new counter value
   - **Non-idempotent**: Each call increments counter (demonstrates at-least-once issues)
   - Use case: Track facility access frequency, audit logging
   - Command: `custom-incr --facility LabA`
