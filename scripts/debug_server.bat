@echo off
rem Debug version of server startup with error catching and compilation
setlocal

rem Get the script directory and navigate to project root
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
pushd "%PROJECT_ROOT%"

echo ================================
echo  Debug Server Startup
echo ================================
echo.

echo Compiling Java sources...
if not exist bin mkdir bin
javac -d bin common\*.java server\*.java
if errorlevel 1 (
    echo Compilation failed!
    echo.
    echo Server process ended. Press any key to continue...
    pause
    goto :cleanup
)

echo Starting server with debug output...
echo Command: java -cp bin ServerMain --port 9999
echo ================================
echo.
java -cp bin ServerMain --port 9999 2>&1

:cleanup
echo.
echo ================================
echo Server process ended. Press any key to continue...
pause
popd
endlocal