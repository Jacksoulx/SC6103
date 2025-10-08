@echo off
rem Build and run the UDP server
setlocal

rem Get the script directory and navigate to project root
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
pushd "%PROJECT_ROOT%"

rem Compile all Java sources to bin
if not exist bin mkdir bin
javac -d bin common\*.java server\*.java
if errorlevel 1 goto :eof

rem Run server with provided args
java -cp bin ServerMain %*

popd
endlocal
