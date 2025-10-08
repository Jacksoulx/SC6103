@echo off
rem Run C UDP client
setlocal
set SRC=c:\Users\wangx\OneDrive\Desktop\ds\SC6103\client
pushd %SRC%

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
