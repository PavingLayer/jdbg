@echo off
:: Maven Wrapper script for Windows

setlocal

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

set WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    mkdir .mvn\wrapper 2>nul
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%WRAPPER_JAR%'"
)

java -jar "%WRAPPER_JAR%" %*

