# Scripts Directory

This directory contains utility scripts for building and running the distributed facility booking system.

## 📋 Available Scripts

| Script | Purpose | Usage |
|--------|---------|-------|
| **`help.bat`** | Show help for all scripts | `scripts\help.bat` |
| **`build_c_client.bat`** | Build C UDP client | `scripts\build_c_client.bat` |
| **`run_server.bat`** | Compile and run Java server | `scripts\run_server.bat [args]` |
| **`run_c_client.bat`** | Run C client commands | `scripts\run_c_client.bat [command] [options]` |
| **`debug_server.bat`** | Run server with debug output | `scripts\debug_server.bat` |
| **`test_weekly_schedule.bat`** | Run comprehensive tests | `scripts\test_weekly_schedule.bat` |
| **`clean.bat`** | Clean build files | `scripts\clean.bat` |

## 🚀 Quick Start

```powershell
# 1. Build the system
scripts\build_c_client.bat

# 2. Start server (in separate terminal)
scripts\run_server.bat

# 3. Test client operations
scripts\run_c_client.bat query --facility LabA --day Monday
scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
```

## 🔧 Features

- **Portable**: All scripts use relative paths (no hardcoded absolute paths)
- **Error Handling**: Proper error checking and cleanup
- **Cross-Compatible**: Works with different MinGW installations
- **Documented**: Clear help and usage information
- **Automated**: Comprehensive testing and cleanup utilities

## 📁 Script Details

### Core Scripts
- **`build_c_client.bat`**: Detects MinGW installation automatically, builds with optimization flags
- **`run_server.bat`**: Compiles Java sources and runs server with arguments
- **`run_c_client.bat`**: Validates executable exists before running client commands

### Utility Scripts  
- **`debug_server.bat`**: Enhanced server startup with compilation and error output
- **`test_weekly_schedule.bat`**: Automated system testing with build verification
- **`clean.bat`**: Removes all temporary and build files safely
- **`help.bat`**: Interactive help system with examples

All scripts are designed to be run from the project root directory and use relative paths for portability.