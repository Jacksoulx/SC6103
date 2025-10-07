@echo off
rem Build and run the UDP server
setlocal
set SRC=e:\SC6103_project
pushd %SRC%

rem Compile all Java sources to bin
if not exist bin mkdir bin
javac -d bin common\*.java server\*.java client\*.java
if errorlevel 1 goto :eof

rem Run server with provided args
java -cp bin ServerMain %*

popd
endlocal
