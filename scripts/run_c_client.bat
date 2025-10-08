@echo off
rem Run C UDP client
setlocal

rem Get the script directory and navigate to client directory
set SCRIPT_DIR=%~dp0
set CLIENT_DIR=%SCRIPT_DIR%..\client
pushd "%CLIENT_DIR%"

rem Check if executable exists
if not exist client_udp.exe (
    echo client_udp.exe not found. Run build_c_client.bat first.
    popd
    exit /b 1
)

rem Run with provided arguments
client_udp.exe %*

popd
endlocal
