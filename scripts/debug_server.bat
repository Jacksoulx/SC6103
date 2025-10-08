@echo off
rem Debug version of server startup with error catching
echo Starting server with debug output...
java -cp bin ServerMain --port 9999 2>&1
echo.
echo Server process ended. Press any key to continue...
pause