@echo off
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ================================
echo Java:
java -version
echo ================================

java -cp "target\classes;target\dependency\*" com.project.game.GameApplication

pause