@echo off
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ================================
echo Java:
java -version
echo ================================

echo.
echo ================================
echo Compiling server...
echo ================================

call mvn -q -DskipTests compile dependency:copy-dependencies

if errorlevel 1 (
    echo.
    echo ================================
    echo BUILD FAILED
    echo ================================
    pause
    exit /b 1
)

echo.
echo ================================
echo Starting server...
echo ================================

java "-Djava.util.logging.config.file=%CD%\logging.properties" -cp "target\classes;target\dependency\*" com.project.game.GameApplication

pause