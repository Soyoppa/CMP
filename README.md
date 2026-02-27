# Finance Tracker - Kotlin Compose Multiplatform

A cross-platform finance tracking application built with Kotlin Compose Multiplatform that integrates with Google Sheets for data storage.

## Features

- ✅ Cross-platform support (Android, iOS, Web, Desktop)
- ✅ Google Sheets integration via Google Apps Script
- ✅ Transaction input with categories and payment modes
- ✅ Real-time data synchronization
- ✅ MVVM architecture with proper state management
- ✅ Secure API key management for production deployments

## Architecture

This app follows MVVM (Model-View-ViewModel) architecture:

- **Model**: Transaction data classes and business logic
- **View**: Compose UI screens with reactive state
- **ViewModel**: State management and business operations
- **Repository**: Data access layer with Google Sheets integration

## Development Setup

1. Clone the repository
2. Open in Android Studio or IntelliJ IDEA
3. Update API configuration in `ApiConfig.kt` with your Google Sheets details
4. Run the app on your preferred platform

## Production Deployment

### Web Deployment (GitLab Pages) - RECOMMENDED

1. **Set up GitLab CI/CD Variables**:
   - Go to your GitLab project → Settings → CI/CD → Variables
   - Add these variables:
     - `SPREADSHEET_ID`: Your Google Sheets ID (`1P7FnOo2Cv-HwfyY3RbWlrr6W1kLURNoEDjxmMj-3NCY`)
     - `API_KEY`: Your Google Sheets API key (`AIzaSyAKJBhNENUHgz4HYJv24epFef0tsIv-Nc0`)
     - `SCRIPT_URL`: Your Google Apps Script web app URL

2. **Enable GitLab Pages**:
   - Push to `main` or `master` branch
   - GitLab CI will automatically build and deploy
   - Access your app at `https://yourusername.gitlab.io/yourproject`

### Web Deployment (GitHub Pages)

1. **Set up GitHub Secrets**:
   - Go to your GitHub repo → Settings → Secrets and variables → Actions
   - Add these secrets with the same values as above

2. **Enable GitHub Pages**:
   - Go to Settings → Pages
   - Select "GitHub Actions" as source
   - Push to `main` branch to trigger deployment

## Security Features

- ✅ API keys are injected at build time, not stored in source code
- ✅ Environment-based configuration for different deployment targets
- ✅ Secure credential management through CI/CD variables
- ✅ Production vs development configuration detection

## Google Sheets Setup

Your current configuration:
- **Spreadsheet ID**: `1P7FnOo2Cv-HwfyY3RbWlrr6W1kLURNoEDjxmMj-3NCY`
- **Sheet Name**: "Data Dump"
- **API Key**: `AIzaSyAKJBhNENUHgz4HYJv24epFef0tsIv-Nc0`
- **Script URL**: `https://script.google.com/macros/s/AKfycbwIwiZaZezvby_ntecR1NfHFkno1fJauCluIk5IWVC0_sEWsLb5AvxTnmqvpaGVGfQA/exec`

## Building for Different Platforms

```bash
# Android APK
./gradlew :composeApp:assembleDebug

# Web (JS) - for deployment
./gradlew :composeApp:jsBrowserDistribution

# Web (JS) - for development
./gradlew :composeApp:jsBrowserDevelopmentRun

# Desktop (JVM)
./gradlew :composeApp:createDistributable

# iOS (requires macOS)
./gradlew :composeApp:iosSimulatorArm64Test
```

## Project Structure

```
composeApp/src/
├── commonMain/kotlin/org/example/project/
│   ├── config/          # Configuration and environment management
│   ├── data/            # API clients and data sources
│   ├── model/           # Data models
│   ├── repository/      # Data access layer
│   ├── ui/              # Compose UI screens
│   ├── viewmodel/       # ViewModels for state management
│   └── App.kt           # Main application entry point
├── androidMain/         # Android-specific code
├── iosMain/            # iOS-specific code
├── webMain/            # Web-specific code
└── jvmMain/            # Desktop-specific code
```

## Next Steps for Deployment

1. **Test the web build locally**:
   ```bash
   ./gradlew :composeApp:jsBrowserDistribution
   ```

2. **Push to GitLab** and set up the CI/CD variables

3. **Access your web app** from any device including iPhone

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes following MVVM architecture
4. Test on multiple platforms
5. Submit a pull request

## License

This project is licensed under the MIT License.