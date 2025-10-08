@echo off
rem Build C client using MinGW gcc on Windows
rem Requires MinGW or similar toolchain in PATH
setlocal

rem Get the script directory and navigate to client directory
set SCRIPT_DIR=%~dp0
set CLIENT_DIR=%SCRIPT_DIR%..\client
pushd "%CLIENT_DIR%"

rem Try to find gcc in common locations
set GCC_PATH=
if exist "C:\TDM-GCC-64\bin\gcc.exe" set GCC_PATH=C:\TDM-GCC-64\bin\
if exist "C:\MinGW\bin\gcc.exe" set GCC_PATH=C:\MinGW\bin\
if exist "C:\msys64\mingw64\bin\gcc.exe" set GCC_PATH=C:\msys64\mingw64\bin\

rem Compile C sources
if defined GCC_PATH (
    "%GCC_PATH%gcc" -Wall -Wno-unknown-pragmas -std=c99 -O2 -o client_udp.exe client_main.c wire_codec.c -lws2_32
) else (
    gcc -Wall -Wno-unknown-pragmas -std=c99 -O2 -o client_udp.exe client_main.c wire_codec.c -lws2_32
)

if errorlevel 1 (
    echo Build failed - Make sure MinGW or GCC is installed and in PATH
    popd
    exit /b 1
)

echo Build successful: client_udp.exe
popd
endlocal
