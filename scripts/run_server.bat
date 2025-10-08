@echo off
rem Build and run the UDP server
setlocal
set SRC=c:\Users\wangx\OneDrive\Desktop\ds\SC6103
pushd %SRC%

rem Compile all Java sources to bin
if not exist bin mkdir bin
javac -d bin common\*.java server\*.java
if errorlevel 1 goto :eof

rem Run server with provided args
java -cp bin ServerMain %*

popd
endlocal
