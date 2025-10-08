# 🚀 How to Run the Weekly Schedule System
## 📋 Prerequisites

- **Java**: JDK 8+ (tested with OpenJDK 25)
- **C Compiler**: MinGW-w64 or Visual Studio (for C client)
- **OS**: Windows (scripts provided for Windows)

## ⚡ Quick Start Guide

### 🖥️ Step 1: Start the Server
```powershell
scripts\run_server.bat
```
**✅ Expected output:** `Server listening on 0.0.0.0:9999 atMostOnce=true lossSim=0.0`

### 🔨 Step 2: Build the Client
```powershell
scripts\build_c_client.bat
```
**✅ Expected output:** `Build successful: client_udp.exe`

### 🧪 Step 3: Test Core Operations

#### 🔍 Test 1: Query Empty Schedule
```powershell
scripts\run_c_client.bat query --facility LabA --day Monday
```
**✅ Expected output:**
```
Available intervals for Monday: 1
  Monday 00:00 - Monday 23:59
```

#### 📝 Test 2: Book a Facility  
```powershell
scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
```
**✅ Expected output:**
```
Booking created: id=1 for LabA from Monday 09:00 to Monday 10:30
```

#### 🔍 Test 3: Query After Booking
```powershell
scripts\run_c_client.bat query --facility LabA --day Monday
```
**✅ Expected output:**
```
Available intervals for Monday: 2
  Monday 00:00 - Monday 09:00
  Monday 10:30 - Monday 23:59
```

#### ⏰ Test 4: Change Booking Time
```powershell
scripts\run_c_client.bat change --booking-id 1 --offset 30
```
**✅ Expected output:**
```
Booking changed: new time Monday 09:30 to Monday 11:00
```

#### 🔄 Test 5: Reset Schedule (Idempotent)
```powershell
scripts\run_c_client.bat reset --facility LabA --day Monday
```
**✅ Expected output:**
```
Schedule reset for facility=LabA on Monday: 1 booking(s) removed
```

**🔁 Run again to verify idempotency:**
```powershell
scripts\run_c_client.bat reset --facility LabA --day Monday
```
**✅ Expected output:**
```
Schedule reset for facility=LabA on Monday: 0 booking(s) removed
```

#### 📊 Test 6: Usage Counter (Non-idempotent)
```powershell
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
```
**✅ Expected output:**
```
Usage counter for facility=LabA => 1
```

**🔢 Run again to see increment:**
```powershell
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
```
**✅ Expected output:**
```
Usage counter for facility=LabA => 2
```

#### 📡 Test 7: Monitor Changes (Advanced)

**Terminal 1** (Monitor):
```powershell
scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000
```

**Terminal 2** (Trigger change):
```powershell
scripts\run_c_client.bat book --facility LabA --user bob --day Tuesday --start-hour 14 --start-minute 0 --end-hour 15 --end-minute 0
```

**🔔 Terminal 1 will receive callback notification when the booking is made.**

## 🎯 Advanced Testing Scenarios

### Test At-least-once vs At-most-once Semantics

#### Without At-most-once (Potential Duplicates)
```powershell
scripts\run_c_client.bat custom-incr --facility TestLab --atMostOnce 0 --retries 3
```

#### With At-most-once (Deduplication)
```powershell
scripts\run_c_client.bat custom-incr --facility TestLab --atMostOnce 1 --retries 3
```

### Test Different Days of the Week
```powershell
# Test different days
scripts\run_c_client.bat query --facility LabA --day Tuesday
scripts\run_c_client.bat query --facility LabA --day Wednesday
scripts\run_c_client.bat book --facility LabA --user charlie --day Friday --start-hour 14 --start-minute 30 --end-hour 16 --end-minute 0
```

## System Status: ✅ FULLY WORKING!

### All Features Operational:
- ✅ Server starts and handles requests
- ✅ Client connects and communicates properly
- ✅ Weekly schedule format (Day/Hour/Minute) working correctly
- ✅ All booking operations functional
- ✅ Display formatting shows correct time ranges
- ✅ Cross-language communication (Java ↔ C) verified
- ✅ At-least-once and at-most-once semantics working
- ✅ Idempotent vs non-idempotent operations demonstrated

### Technical Achievements:
1. **Protocol Efficiency**: 3-byte WeeklyTime vs 8-byte timestamps (62% reduction)
2. **Human Readable**: "Monday 09:00" instead of epoch milliseconds
3. **Cross-Platform**: Java server + C client with proper byte order handling
4. **Network Resilience**: UDP with retry mechanism and deduplication
5. **Clean Architecture**: Manual marshalling without Java serialization
