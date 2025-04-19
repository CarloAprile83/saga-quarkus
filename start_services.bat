@echo off
echo Starting Quarkus services in Dev mode...

REM Avvia Order Service (porta debug 5005)
echo Starting Order Service (Debug Port 5005)...
start "Order Service" cmd /k "cd /d order-service && ..\gradlew quarkusDev -Ddebug=5005"

REM Avvia Payment Service (porta debug 5006)
echo Starting Payment Service (Debug Port 5006)...
start "Payment Service" cmd /k "cd /d payment-service && ..\gradlew quarkusDev -Ddebug=5006"

REM Avvia Stock Service (porta debug 5007)
echo Starting Stock Service (Debug Port 5007)...
start "Stock Service" cmd /k "cd /d stock-service && ..\gradlew quarkusDev -Ddebug=5007"

echo "All services starting in new windows. Waiting 60 seconds for them to initialize..."
timeout /t 60 /nobreak

echo "Initialization period (60s) over. VS Code will now attempt to attach debuggers to ports 5005, 5006, and 5007."
echo "******************************************************"
echo "* IMPORTANT: To stop services, CLOSE THEIR TERMINAL WINDOWS! *"
echo "* All three services should now be debuggable simultaneously. *"
echo "******************************************************"

pause
