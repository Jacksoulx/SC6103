@echo off
rem Comprehensive test script for the weekly schedule system
setlocal

rem Get the script directory and navigate to project root
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
pushd "%PROJECT_ROOT%"

echo ================================
echo  Weekly Schedule System Test
echo ================================
echo.

echo Step 0: Building system...
echo - Compiling Java server...
call scripts\run_server.bat --help > nul 2>&1
if errorlevel 1 (
    echo Java compilation failed!
    goto :cleanup
)

echo - Building C client...
call scripts\build_c_client.bat
if errorlevel 1 (
    echo C client build failed!
    goto :cleanup
)

echo.
echo Step 1: Starting server...
start "Weekly_Schedule_Server" cmd /k "scripts\run_server.bat --port 9999"
timeout /t 5 /nobreak > nul
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
echo  Test Complete!
echo ================================
echo.
echo NOTE: Server is still running in separate window.
echo       Close the server window when done testing.

:cleanup
popd
endlocal
pause