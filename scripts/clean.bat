@echo off
rem Clean up temporary and build files
setlocal

rem Get the script directory and navigate to project root
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
pushd "%PROJECT_ROOT%"

echo ================================
echo  Cleaning Build Files
echo ================================
echo.

echo Removing Java compiled classes...
if exist bin\*.class (
    del /q bin\*.class
    echo - Removed .class files
) else (
    echo - No .class files found
)

echo.
echo Removing C executable...
if exist client\client_udp.exe (
    del /q client\client_udp.exe
    echo - Removed client_udp.exe
) else (
    echo - No client executable found
)

echo.
echo Removing temporary files...
for /r %%i in (*.tmp *.bak *~) do (
    if exist "%%i" (
        del /q "%%i"
        echo - Removed %%i
    )
)

echo.
echo ================================
echo  Cleanup Complete!
echo ================================
echo.
echo To rebuild the system:
echo 1. scripts\run_server.bat     (builds Java automatically)
echo 2. scripts\build_c_client.bat (builds C client)

popd
endlocal
pause