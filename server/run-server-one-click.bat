@echo off
setlocal

cd /d "%~dp0"

title Vu Tru Rong Than - Server

REM =========================================================
REM Java 21
REM =========================================================
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ========================================================
echo Vu Tru Rong Than - Build and Run Server
echo ========================================================
echo.
echo Server directory:
echo %CD%
echo.

REM =========================================================
REM 1. Check Java
REM =========================================================
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java not found:
    echo %JAVA_HOME%\bin\java.exe
    echo.
    echo Update JAVA_HOME in this BAT file if your JDK path is different.
    echo.
    pause
    exit /b 1
)

echo [1/4] Java:
java -version
if errorlevel 1 (
    echo.
    echo [ERROR] Java failed to start.
    echo.
    pause
    exit /b 1
)

echo.

REM =========================================================
REM 2. Check Maven
REM =========================================================
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven command "mvn" was not found in PATH.
    echo.
    echo Install Maven or add Maven\bin to PATH.
    echo.
    pause
    exit /b 1
)

echo [2/4] Maven:
call mvn -version
if errorlevel 1 (
    echo.
    echo [ERROR] Maven failed to start.
    echo.
    pause
    exit /b 1
)

echo.

REM =========================================================
REM 3. Show active source resource config
REM =========================================================
echo [3/4] Resource configuration:
if exist "src\main\resources\application.properties" (
    findstr /B /C:"game.resource.icon-dir=" "src\main\resources\application.properties"
    findstr /B /C:"game.resource.image-version=" "src\main\resources\application.properties"
    findstr /B /C:"game.resource.json-dir=" "src\main\resources\application.properties"
) else (
    echo [WARN] src\main\resources\application.properties not found.
)

echo.
echo ========================================================
echo Building latest server code...
echo ========================================================
echo.

REM clean:
REM   removes old classes/resources from target.
REM compile:
REM   compiles latest Java code and copies src/main/resources.
REM dependency:copy-dependencies:
REM   refreshes target\dependency for the runtime classpath.
call mvn -q -DskipTests clean compile dependency:copy-dependencies

if errorlevel 1 (
    echo.
    echo ========================================================
    echo [BUILD FAILED]
    echo Server was NOT started.
    echo Fix the errors above, then double-click this BAT again.
    echo ========================================================
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================================
echo [BUILD SUCCESS]
echo ========================================================
echo.

REM =========================================================
REM 4. Start server
REM =========================================================
echo [4/4] Starting server...
echo.

if exist "%CD%\logging.properties" (
    echo Logging config: %CD%\logging.properties
    echo ========================================================
    echo.
    java "-Djava.util.logging.config.file=%CD%\logging.properties" -cp "target\classes;target\dependency\*" com.project.game.GameApplication
) else (
    echo [WARN] logging.properties not found.
    echo Using default Java logging configuration.
    echo ========================================================
    echo.
    java -cp "target\classes;target\dependency\*" com.project.game.GameApplication
)

set "SERVER_EXIT=%ERRORLEVEL%"

echo.
echo ========================================================
echo Server stopped. Exit code: %SERVER_EXIT%
echo ========================================================
echo.
pause

exit /b %SERVER_EXIT%
