@echo off
echo Building production web app...
call gradlew.bat wasmJsBrowserDistribution

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)

echo Build successful!
echo.
echo Deploying to Firebase...
call firebase deploy --only hosting

echo.
echo Deployment complete!
