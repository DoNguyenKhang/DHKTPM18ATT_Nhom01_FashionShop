@echo off
echo ============================================
echo VNPay Tunnel Setup - Error 72 Fix
echo ============================================
echo.

REM Check if ngrok is already running
tasklist /FI "IMAGENAME eq ngrok.exe" 2>NUL | find /I /N "ngrok.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo [INFO] ngrok is already running
) else (
    echo [INFO] Starting ngrok tunnel on port 8080...
    start "ngrok" /MIN D:\Project\fashion\ngrok.exe http 8080
    echo [INFO] Waiting for ngrok to initialize...
    timeout /t 5 /nobreak >nul
)

echo.
echo ============================================
echo IMPORTANT: Getting your ngrok URL
echo ============================================
echo.
echo 1. Open your browser and go to: http://localhost:4040
echo 2. Look for "Forwarding" and copy the HTTPS URL
echo.
echo Example: https://abc-123-456.ngrok-free.app
echo.
echo ============================================
echo.
set /p NGROK_URL="Paste your ngrok HTTPS URL here: "

if "%NGROK_URL%"=="" (
    echo ERROR: No URL provided!
    pause
    exit /b 1
)

echo.
echo ============================================
echo Setting Environment Variable
echo ============================================
echo.
set VNPAY_RETURN_URL=%NGROK_URL%/api/payment/vnpay/callback
echo VNPAY_RETURN_URL=%VNPAY_RETURN_URL%
echo.

echo ============================================
echo NEXT STEPS - READ CAREFULLY!
echo ============================================
echo.
echo 1. Keep this window open (environment variable is set here)
echo 2. In THIS SAME WINDOW, start your Spring Boot app:
echo.
echo    D:\Project\fashion\mvnw.cmd spring-boot:run
echo.
echo 3. Access your app via the ngrok URL (NOT localhost):
echo    %NGROK_URL%
echo.
echo 4. Test VNPay payment
echo.
echo IMPORTANT NOTES:
echo - The environment variable is only set in this CMD window
echo - You MUST start the app from this window
echo - Access the app via %NGROK_URL% (not localhost)
echo - When VNPay redirects you, it will use this public URL
echo.
echo ============================================
echo.
echo Press any key to start the Spring Boot app now...
pause >nul

echo.
echo Starting Spring Boot application with VNPay tunnel configured...
echo.
D:\Project\fashion\mvnw.cmd spring-boot:run

echo.
echo ============================================
echo Spring Boot app has stopped
echo ============================================
pause
