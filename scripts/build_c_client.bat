@echo off
rem Build C client using MinGW gcc on Windows
rem Requires MinGW or similar toolchain in PATH
setlocal
set SRC=e:\SC6103_project\client
pushd %SRC%

rem Compile C sources
gcc -Wall -Wextra -std=c11 -O2 -o client_udp.exe client_main.c wire_codec.c -lws2_32

if errorlevel 1 (
    echo Build failed
    popd
    exit /b 1
)

echo Build successful: client_udp.exe
popd
endlocal
