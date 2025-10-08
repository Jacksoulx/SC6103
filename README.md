# Distributed Facility Booking System (UDP) - Weekly Schedule

[![Language](https://img.shields.io/badge/Languages-Java%20%2B%20C-blue.svg)]()
[![Protocol](https://img.shields.io/badge/Protocol-UDP-green.svg)]()
[![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen.svg)]()

This project implements a **heterogeneous UDP-based distributed system** for facility booking with manual binary marshalling, featuring:

- 🏢 **Facility Management**: Query availability and book facilities using weekly recurring schedules
- 📅 **Weekly Schedule Format**: Human-readable Day/Hour/Minute format instead of timestamps
- 🔄 **Real-time Updates**: UDP callback system for monitoring facility changes
- 🌐 **Cross-Language RPC**: Java server communicating with C client via custom binary protocol
- ⚡ **Network Resilience**: At-least-once and at-most-once semantics with retry mechanisms
- 🎯 **Custom Operations**: Demonstrates idempotent vs non-idempotent operations

## 📑 Table of Contents

- [Quick Start](#-quick-start)
- [Project Structure](#-project-structure)
- [Protocol Specification](#protocol-specification)
- [Weekly Schedule Features](#weekly-schedule-features)
- [Testing At-least-once vs At-most-once](#testing-at-least-once-vs-at-most-once)
- [Technical Features](#-technical-features)
- [Documentation](#-documentation)

## Quick Start

### 1. Start Java Server
```bash
scripts\run_server.bat
```
**Expected output:** `Server listening on 0.0.0.0:9999 atMostOnce=true lossSim=0.0`

### 2. Build C Client (requires MinGW gcc)
```bash
scripts\build_c_client.bat
```

### 3. Run Client Commands
```bash
# Query facility availability for a specific day
scripts\run_c_client.bat query --facility LabA --day Monday

# Book facility using weekly schedule
scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30

# Change existing booking by offset (minutes)
scripts\run_c_client.bat change --booking-id 1 --offset 60

# Monitor facility changes (callbacks)
scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000

# Reset day schedule (idempotent)
scripts\run_c_client.bat reset --facility LabA --day Monday

# Increment usage counter (non-idempotent)
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
```

## 📁 Project Structure

```
SC6103/
├── 📂 common/                    # Shared protocol definitions (Java)
│   ├── Protocol.java             # Op codes, flags, constants
│   ├── Types.java                # Data types: Day enum, WeeklyTime, Booking, Interval  
│   └── WireCodec.java            # Manual marshalling (ByteBuffer, big-endian)
├── 📂 server/                    # Java UDP server
│   ├── ServerMain.java           # Main server loop with DatagramSocket
│   ├── RequestRouter.java        # Request routing + at-most-once cache
│   ├── ReservationLogic.java     # Business logic (booking, conflict detection)
│   ├── FacilityStore.java        # In-memory storage with weekly schedules
│   └── MonitorRegistry.java      # UDP callback registration
├── 📂 client/                    # C UDP client  
│   ├── client_main.c             # Command-line interface with Winsock2
│   ├── protocol.h                # Op codes + data structures (mirrors Java)
│   ├── wire_codec.h/.c           # Manual marshalling (htons/htonl)
│   └── client_udp.exe            # Compiled executable
├── 📂 scripts/                   # Build and run utilities
│   ├── help.bat                  # Interactive help system
│   ├── build_c_client.bat        # Build C client (MinGW auto-detect)
│   ├── run_server.bat            # Compile and run Java server
│   ├── run_c_client.bat          # Execute client commands
│   ├── debug_server.bat          # Server with debug output
│   ├── test_weekly_schedule.bat  # Comprehensive system tests
│   ├── clean.bat                 # Clean build files
│   └── README.md                 # Scripts documentation
├── 📂 bin/                       # Java compiled classes
├── 📄 README.md                  # Main documentation (this file)
├── 📄 HOW_TO_RUN.md              # Step-by-step usage guide
├── 📄 HETEROGENEOUS.md           # Cross-language implementation details
├── 📄 CURRENT_STATUS.md          # System status and features
└── 📄 PROJECT_STRUCTURE.txt      # Technical specifications
```

## Protocol Specification

### Message Header (16 bytes, big-endian)
```
Offset  Size  Field         Type/Encoding
------  ----  -----         -------------
0-1     2     version       uint16 (=1)
2-3     2     opCode        uint16
4-7     4     requestId     uint32
8-11    4     flags         uint32 (bit0: atMostOnce; bit1: isCallback)
12-15   4     payloadLen    uint32
```

### Data Types
- **Strings**: uint16 length + UTF-8 bytes (no null terminator)
- **WeeklyTime**: uint8 day (0-6) + uint8 hour (0-23) + uint8 minute (0-59) = **3 bytes total**
- **Day Enum**: Monday=0, Tuesday=1, ..., Sunday=6

### Operation Codes
- `0x0001` - QUERY_AVAIL (query day availability)
- `0x0002` - BOOK (book facility)
- `0x0003` - CHANGE_BOOKING (modify booking)
- `0x0004` - MONITOR (register callbacks)
- `0x1001` - CUSTOM_IDEMPOTENT (reset day schedule)
- `0x1002` - CUSTOM_NON_IDEMPOTENT (increment counter)
- `0x8000` - Error flag mask

## 🔧 Technical Features

- **Pure UDP Implementation**: No Java serialization, RMI, or CORBA - only DatagramSocket/DatagramPacket
- **Manual Binary Marshalling**: Custom protocol with network byte order (big-endian)
- **Cross-Platform Networking**: Winsock2 (Windows) + POSIX sockets compatibility
- **Packet Loss Simulation**: Configurable loss rate for testing network resilience
- **Request Deduplication**: At-most-once cache with 60s TTL using monotonic request IDs
- **Dynamic Facility Creation**: Facilities are auto-created on first use
- **Comprehensive Documentation**: Inline comments in English for international collaboration
- **Production Ready**: Clean codebase with optimized imports and no unused code

## 📚 Documentation

- **[HOW_TO_RUN.md](HOW_TO_RUN.md)** - Complete step-by-step usage guide
- **[HETEROGENEOUS.md](HETEROGENEOUS.md)** - Cross-language implementation details
- **[CURRENT_STATUS.md](CURRENT_STATUS.md)** - System status and feature summary
- **[PROJECT_STRUCTURE.txt](PROJECT_STRUCTURE.txt)** - Technical specifications

## Weekly Schedule Features

### Time Representation
- **Recurring Schedule**: Bookings repeat weekly (Monday-Sunday)
- **Human Readable**: "Monday 09:00" instead of epoch timestamps
- **Compact Protocol**: 3 bytes per time vs 8 bytes (62% reduction)
- **Week Boundaries**: 0-10079 minutes (7 days × 24 hours × 60 minutes)

### Supported Operations

#### Core Operations
1. **QUERY_AVAIL** - Query day availability
   ```bash
   scripts\run_c_client.bat query --facility LabA --day Monday
   ```
   Returns available time intervals for the specified day

2. **BOOK** - Book facility using weekly schedule
   ```bash
   scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
   ```
   Books facility and returns booking ID

3. **CHANGE_BOOKING** - Modify booking by offset
   ```bash
   scripts\run_c_client.bat change --booking-id 1 --offset 60
   ```
   Shifts booking time by specified minutes (can be negative)

4. **MONITOR** - Register for change notifications
   ```bash
   scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000
   ```
   Receives UDP callbacks when facility availability changes

#### Custom Operations

5. **CUSTOM_IDEMPOTENT** - Reset day schedule
   ```bash
   scripts\run_c_client.bat reset --facility LabA --day Monday
   ```
   - **Idempotent**: Same result regardless of repetition
   - Removes all bookings for specified day
   - Returns count of removed bookings

6. **CUSTOM_NON_IDEMPOTENT** - Usage counter
   ```bash
   scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
   ```
   - **Non-idempotent**: State changes with each call
   - Increments facility usage counter
   - Demonstrates at-least-once vs at-most-once semantics

## Testing At-least-once vs At-most-once

### Test Non-idempotent Operation (Usage Counter)
```bash
# Run multiple times - counter increments each time
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 0
# Output: Usage counter for facility=LabA => 1, 2, 3...
```

### Test With At-most-once Semantics
```bash
# Even with retries, increments only once per unique request
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1 --retries 5
# Output: Usage counter for facility=LabA => 1 (server deduplicates)
```
