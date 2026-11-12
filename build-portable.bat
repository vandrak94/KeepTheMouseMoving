@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title KeepTheMouseMoving - portable build

set "APP=KeepTheMouseMoving"
set "LOG=%~dp0build-portable.log"
set "SRC=%~dp0src\main\KeepTheMouseMovingApp.java"
set "SETTINGSICON=%~dp0resources\settings.png"
set "APPICON=%~dp0resources\KeepTheMouseMoving.png"
set "EXEICON=%~dp0resources\KeepTheMouseMoving.ico"
set "BUILD=%~dp0build"
set "DIST=%~dp0dist"

>"%LOG%" echo ==== KeepTheMouseMoving build log ====
>>"%LOG%" echo Started: %DATE% %TIME%
>>"%LOG%" echo Working directory: %CD%

echo ========================================
echo   KeepTheMouseMoving - portable ZIP build
echo ========================================
echo.

echo [1/7] Checking required tools...
call :checkTool java.exe || goto :fail
call :checkTool javac.exe || goto :fail
call :checkTool jar.exe || goto :fail
call :checkTool jpackage.exe || goto :fail
call :checkTool powershell.exe || goto :fail

if not exist "%SRC%" (
    >>"%LOG%" echo ERROR: Source file not found: %SRC%
    echo CHYBA: Nenalezen zdrojovy soubor "%SRC%"
    goto :fail
)
if not exist "%SETTINGSICON%" (
    >>"%LOG%" echo ERROR: Resource not found: %SETTINGSICON%
    echo CHYBA: Nenalezena ikona "%SETTINGSICON%"
    goto :fail
)
if not exist "%APPICON%" (
    >>"%LOG%" echo ERROR: Resource not found: %APPICON%
    echo CHYBA: Nenalezena ikona "%APPICON%"
    goto :fail
)
if not exist "%EXEICON%" (
    >>"%LOG%" echo ERROR: Resource not found: %EXEICON%
    echo CHYBA: Nenalezena ikona "%EXEICON%"
    goto :fail
)

echo [2/7] Cleaning output directories...
if exist "%BUILD%" rmdir /s /q "%BUILD%"
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%BUILD%\classes" >>"%LOG%" 2>&1 || goto :fail
mkdir "%BUILD%\input" >>"%LOG%" 2>&1 || goto :fail
mkdir "%DIST%" >>"%LOG%" 2>&1 || goto :fail

echo [3/7] Compiling Java 17 source...
javac --release 17 -encoding UTF-8 -d "%BUILD%\classes" "%SRC%" >>"%LOG%" 2>&1
if errorlevel 1 goto :fail

echo [4/7/1] Adding settings icon to application resources...
copy /Y "%SETTINGSICON%" "%BUILD%\classes\settings.png" >>"%LOG%" 2>&1
if errorlevel 1 goto :fail
if not exist "%BUILD%\classes\settings.png" goto :fail

echo [4/7/2] Adding app icon to application resources...
copy /Y "%~dp0resources\KeepTheMouseMoving.png" "%BUILD%\classes\KeepTheMouseMoving.png" >>"%LOG%" 2>&1
if errorlevel 1 goto :fail
if not exist "%BUILD%\classes\KeepTheMouseMoving.png" goto :fail

echo [4/7/2] Adding exe icon to application resources...
copy /Y "%~dp0resources\KeepTheMouseMoving.ico" "%BUILD%\classes\KeepTheMouseMoving.ico" >>"%LOG%" 2>&1
if errorlevel 1 goto :fail
if not exist "%BUILD%\classes\KeepTheMouseMoving.ico" goto :fail

echo [5/7] Creating application JAR...
jar --create --file "%BUILD%\input\%APP%.jar" --main-class KeepTheMouseMovingApp -C "%BUILD%\classes" . >>"%LOG%" 2>&1
if errorlevel 1 goto :fail

rem Verify that settings.png is really inside the JAR.
jar --list --file "%BUILD%\input\%APP%.jar" | findstr /x /c:"settings.png" >nul
if errorlevel 1 (
    >>"%LOG%" echo ERROR: settings.png is missing from %APP%.jar
    echo CHYBA: settings.png se nepodarilo vlozit do JARu.
    goto :fail
)

echo [6/7] Creating portable application image...

jpackage ^
    --type app-image ^
    --name "%APP%" ^
    --app-version 1.0.0 ^
    --input "%BUILD%\input" ^
    --main-jar "%APP%.jar" ^
    --main-class KeepTheMouseMovingApp ^
    --icon "%~dp0resources\KeepTheMouseMoving.ico" ^
    --java-options "-Dapp.dir=$APPDIR" ^
    --dest "%DIST%" >>"%LOG%" 2>&1

if errorlevel 1 goto :fail

if not exist "%DIST%\%APP%\%APP%.exe" (
    >>"%LOG%" echo ERROR: Expected EXE was not created.
    goto :fail
)

if exist "%DIST%\%APP%\%APP%.ico" (
    del /Q "%DIST%\%APP%\%APP%.ico"
)

>"%DIST%\%APP%\delay.properties" echo delay.minutes=3

echo [7/7] Creating portable ZIP...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -LiteralPath '%DIST%\%APP%' -DestinationPath '%DIST%\%APP%-portable.zip' -Force" >>"%LOG%" 2>&1
if errorlevel 1 goto :fail

>>"%LOG%" echo Finished successfully: %DATE% %TIME%
echo.
echo BUILD OK
echo "%DIST%\%APP%-portable.zip"
echo.
pause
exit /b 0

:checkTool
where %1 >>"%LOG%" 2>&1
if errorlevel 1 (
    >>"%LOG%" echo ERROR: Required tool %1 was not found in PATH.
    echo CHYBA: %1 nebyl nalezen v PATH.
    exit /b 1
)
exit /b 0

:fail
set "ERR=%ERRORLEVEL%"
if "%ERR%"=="0" set "ERR=1"
>>"%LOG%" echo FAILED: %DATE% %TIME%, exit code %ERR%
echo.
echo BUILD SELHAL - exit code %ERR%
echo Log: "%LOG%"
echo.
if exist "%LOG%" powershell.exe -NoProfile -Command "Get-Content -LiteralPath '%LOG%' -Tail 30" 2>nul
pause
exit /b %ERR%
