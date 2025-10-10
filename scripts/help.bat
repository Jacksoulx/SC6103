@echo off
rem Script to display help for all available scripts
echo ================================
echo  SC6103 Project Scripts Help
echo ================================
echo.

echo Available Scripts:
echo.

echo 1. build_c_client.bat
echo    Purpose: Build the C UDP client using MinGW gcc
echo    Usage:   scripts\build_c_client.bat
echo    Output:  Creates client_udp.exe in client\ directory
echo.

echo 2. run_server.bat [args]
echo    Purpose: Compile and run the Java UDP server
echo    Usage:   scripts\run_server.bat [--port 9999] [--lossSim 0.1]
echo    Example: scripts\run_server.bat --port 9999 --atMostOnce true
echo.

echo 3. run_c_client.bat [command] [options]
echo    Purpose: Run the C UDP client with specified command
echo    Usage:   scripts\run_c_client.bat [command] [options]
echo    Examples:
echo             scripts\run_c_client.bat query --facility LabA --day Monday
echo             scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
echo             scripts\run_c_client.bat change --booking-id 1 --offset 60
echo             scripts\run_c_client.bat reset --facility LabA --day Monday
echo             scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
echo             scripts\run_c_client.bat monitor --facility LabA --duration 60 --callback-port 10000
echo.
echo    Remote Server Examples:
echo             scripts\run_c_client.bat query --host 192.168.1.100 --port 9999 --facility LabA --day Monday
echo             scripts\run_c_client.bat book --host 192.168.1.100 --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
echo.

echo 4. debug_server.bat
echo    Purpose: Run server with debug output and error catching
echo    Usage:   scripts\debug_server.bat
echo    Note:    Automatically compiles before running
echo.

echo 5. test_weekly_schedule.bat
echo    Purpose: Run comprehensive system tests
echo    Usage:   scripts\test_weekly_schedule.bat
echo    Note:    Builds system and runs automated tests
echo.

echo ================================
echo  Quick Start:
echo ================================
echo 1. scripts\build_c_client.bat
echo 2. scripts\run_server.bat
echo 3. scripts\run_c_client.bat query --facility LabA --day Monday
echo.

echo For detailed documentation, see:
echo - README.md
echo - HOW_TO_RUN.md
echo ================================
pause