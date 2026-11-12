@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "SRC=%~dp0src\main\KeepTheMouseMovingApp.java"
set "SETTINGSICON=%~dp0resources\settings.png"
set "APPICON=%~dp0resources\KeepTheMouseMoving.png"
set "OUT=%~dp0build\classes"

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

if not exist "%SRC%" (
    echo CHYBA: Nenalezen zdrojovy soubor:
    echo   "%SRC%"
    goto :fail
)

if not exist "%SETTINGSICON%" (
    echo CHYBA: Nenalezena ikona nastavení:
    echo   "%SETTINGSICON%"
    goto :fail
)

if not exist "%APPICON%" (
    echo CHYBA: Nenalezena ikona aplikace:
    echo   "%APPICON%"
    goto :fail
)

echo Compiling KeepTheMouseMovingApp.java...
javac --release 17 -encoding UTF-8 -d "%OUT%" "%SRC%"
if errorlevel 1 goto :fail

echo Copying resources...
copy /Y "%SETTINGSICON%" "%OUT%\settings.png" >nul
if errorlevel 1 goto :fail

copy /Y "%APPICON%" "%OUT%\KeepTheMouseMoving.png" >nul
if errorlevel 1 goto :fail

echo Starting KeepTheMouseMoving...
java -cp "%OUT%" KeepTheMouseMovingApp
if errorlevel 1 goto :fail

exit /b 0

:fail
set "ERR=%ERRORLEVEL%"
if "%ERR%"=="0" set "ERR=1"
echo.
echo CHYBA - exit code %ERR%
pause
exit /b %ERR%
