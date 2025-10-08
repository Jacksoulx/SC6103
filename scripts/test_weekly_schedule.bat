@echo off
rem Simple test script for the weekly schedule system
echo ================================
echo Weekly Schedule System Test
echo ================================
echo.

echo Step 1: Starting server...
start "Server" cmd /k "java -cp bin ServerMain --port 9999"
timeout /t 3 /nobreak > nul
echo.

echo Step 2: Testing query availability...
echo Command: scripts\run_c_client.bat query --facility LabA --day Monday
echo.
scripts\run_c_client.bat query --facility LabA --day Monday
echo.

echo Step 3: Testing booking...
echo Command: scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
echo.
scripts\run_c_client.bat book --facility LabA --user alice --day Monday --start-hour 9 --start-minute 0 --end-hour 10 --end-minute 30
echo.

echo Step 4: Testing query after booking...
echo Command: scripts\run_c_client.bat query --facility LabA --day Monday
echo.
scripts\run_c_client.bat query --facility LabA --day Monday
echo.

echo Step 5: Testing reset (idempotent)...
echo Command: scripts\run_c_client.bat reset --facility LabA --day Monday
echo.
scripts\run_c_client.bat reset --facility LabA --day Monday
echo.

echo Step 6: Testing usage counter (non-idempotent)...
echo Command: scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1  
echo.
scripts\run_c_client.bat custom-incr --facility LabA --atMostOnce 1
echo.

echo ================================
echo Test Complete!
echo ================================
pause