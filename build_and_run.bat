@echo off
echo ========================================
echo Compilazione e installazione Pastiera
echo ========================================
echo.

REM Compila e installa l'app
call gradlew.bat installDebug

REM Verifica se l'installazione è riuscita
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Installazione completata con successo!
    echo ========================================
    echo.
    echo Avvio dell'app sul dispositivo...
    
    REM Lancia l'app sul dispositivo Android
    adb shell am start -n it.palsoftware.pastiera/.MainActivity
    
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo App avviata con successo!
        exit /b 0
    ) else (
        echo.
        echo ERRORE: Impossibile avviare l'app.
        echo Verifica che il dispositivo sia connesso e che ADB sia configurato correttamente.
        exit /b %ERRORLEVEL%
    )
) else (
    echo.
    echo ERRORE: La compilazione/installazione non è riuscita.
    echo Verifica gli errori sopra.
    exit /b %ERRORLEVEL%
)

