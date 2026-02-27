@echo off
echo Building web distribution...
call gradlew :composeApp:jsBrowserDistribution

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Build successful! 
echo.
echo Web files are located in: composeApp\build\dist\js\productionExecutable\
echo.
echo To test locally, you can:
echo 1. Use a local web server like Live Server in VS Code
echo 2. Use Python: python -m http.server 8000 (in the productionExecutable folder)
echo 3. Use Node.js: npx serve . (in the productionExecutable folder)
echo.
echo For deployment to GitLab Pages:
echo 1. Push this code to your GitLab repository
echo 2. Set up CI/CD variables in GitLab (SPREADSHEET_ID, API_KEY, SCRIPT_URL)
echo 3. The .gitlab-ci.yml will automatically build and deploy
echo.
pause