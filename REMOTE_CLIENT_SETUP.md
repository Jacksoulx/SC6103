# Running Client on Different PC

## ✅ Yes, you can run the client on a different PC!

The system is designed to work across different machines in a network. Here's how to set it up:

## 🖥️ Server Setup (Host PC)

### 1. Configure Server for Remote Access
```powershell
# Start server to listen on all interfaces (default)
scripts\run_server.bat --host 0.0.0.0 --port 9999

# Alternative: Bind to specific IP 
# Remember to change this 192.168.1.100 to actual ip
scripts\run_server.bat --host 192.168.1.100 --port 9999
```

### 2. Check Server IP Address
```powershell
# Find your server's IP address change 192.168.1.100 to this 
ipconfig | findstr IPv4
```

### 3. Configure Firewall (Windows)
```powershell
# Allow UDP port 9999 through Windows Firewall
netsh advfirewall firewall add rule name="UDP Port 9999" dir=in action=allow protocol=UDP localport=9999
```

## 💻 Client Setup (Remote PC)

### 1. Copy Client Files
Transfer these files to the remote PC:
```
client/
├── client_main.c
├── wire_codec.c
├── wire_codec.h
├── protocol.h
└── Makefile
```

### 2. Build Client on Remote PC
```powershell
# Requires MinGW or GCC
gcc -Wall -Wno-unknown-pragmas -std=c99 -O2 -o client_udp.exe client_main.c wire_codec.c -lws2_32
```

### 3. Run Client with Server IP
```powershell
# Connect to remote server
client_udp.exe query --host 192.168.1.100 --port 9999 --facility LabA --day Monday

# All commands support --host and --port parameters
client_udp.exe book --host 192.168.1.100 --port 9999 --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
```

## 🌐 Network Configuration Examples

### Same Local Network
```powershell
# Server PC: 192.168.1.100
scripts\run_server.bat --host 0.0.0.0 --port 9999

# Client PC: Any IP on 192.168.1.x network
client_udp.exe query --host 192.168.1.100 --facility LabA --day Monday
```

### Different Subnets/Internet
```powershell
# Server PC: Public IP or port forwarding
scripts\run_server.bat --host 0.0.0.0 --port 9999

# Client PC: Connect via public IP
client_udp.exe query --host 203.0.113.10 --port 9999 --facility LabA --day Monday
```

## 🔧 Command Examples for Remote Client

### Basic Operations
```powershell
# Query availability (replace SERVER_IP with actual server IP)
client_udp.exe query --host SERVER_IP --port 9999 --facility LabA --day Monday

# Book facility
client_udp.exe book --host SERVER_IP --port 9999 --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30

# Change booking
client_udp.exe change --host SERVER_IP --port 9999 --booking-id 1 --offset 60

# Reset schedule
client_udp.exe reset --host SERVER_IP --port 9999 --facility LabA --day Monday

# Usage counter
client_udp.exe custom-incr --host SERVER_IP --port 9999 --facility LabA --atMostOnce 1
```

### Monitor with Callbacks
```powershell
# Monitor requires client to listen on callback port
# Make sure callback port is accessible from server
client_udp.exe monitor --host SERVER_IP --port 9999 --facility LabA --duration 60 --callback-port 10000
```

## 🛠️ Troubleshooting

### Connection Issues
```powershell
# Test connectivity
ping SERVER_IP
telnet SERVER_IP 9999
```

### Common Problems
1. **Firewall blocking**: Open UDP port 9999 on server
2. **Wrong IP**: Use `ipconfig` on server to get correct IP
3. **Network timeout**: Increase timeout with `--timeout 2000` (2 seconds)
4. **Retries**: Increase retries with `--retries 5`

### Extended Client Command
```powershell
# Full command with network options
client_udp.exe query --host 192.168.1.100 --port 9999 --timeout 2000 --retries 5 --facility LabA --day Monday
```

## 🔒 Security Considerations

- **No encryption**: Data is sent in plain text over UDP
- **No authentication**: Anyone who can reach the server can use it
- **Open port**: Server port 9999 needs to be accessible
- **Firewall**: Configure firewall rules appropriately

## ✅ Cross-Platform Compatibility

### Windows Client
- Uses Winsock2 for networking
- Requires MinGW or Visual Studio for compilation

### Linux/macOS Client (if ported)
- Uses POSIX sockets
- Compile with: `gcc -std=c99 -O2 -o client_udp client_main.c wire_codec.c`

The system is fully network-ready and designed for distributed deployment! 🌐