import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("com.github.gmazzo.buildconfig") version "5.5.0"
}

// Load local.properties
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// Release signing: keystore.properties (gitignored) next to local.properties, with
// storeFile / storePassword / keyAlias / keyPassword. See RELEASE.md.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

val appVersionCode = providers.gradleProperty("app.versionCode").get().toInt()
val appVersionName = providers.gradleProperty("app.versionName").get()

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            
            // Android-specific HTTP client engine
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
            
            // Firebase dependencies (Android only)
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:34.5.0"))
            implementation("com.google.firebase:firebase-auth")
            implementation("com.google.firebase:firebase-config")
            implementation("com.google.firebase:firebase-ai")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
        }

        iosMain.dependencies {
            // iOS HTTP client engine (Ktor has no default engine on Apple targets)
            implementation("io.ktor:ktor-client-darwin:3.0.3")
            // Keychain-backed storage for the Firebase Auth credential
            implementation("com.russhwolf:multiplatform-settings:1.3.0")
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Multiplatform BackHandler (Android back button/gesture; no-op elsewhere)
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.9.1")
            
            // Date/Time handling
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            
            // HTTP Client for API calls
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("io.ktor:ktor-client-mock:3.0.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            
            // JVM-specific HTTP client engine
            implementation("io.ktor:ktor-client-cio:3.0.3")
        }
        
        jsMain.dependencies {
            // JS-specific HTTP client engine
            implementation("io.ktor:ktor-client-js:3.0.3")
        }
        
        wasmJsMain.dependencies {
            // WASM-specific HTTP client engine  
            implementation("io.ktor:ktor-client-js:3.0.3")
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// Store uploads must be signed: fail loudly instead of producing an unsigned bundle/APK.
val hasReleaseKeystore = keystorePropertiesFile.exists()
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (!hasReleaseKeystore) {
            throw GradleException("keystore.properties not found — release builds must be signed. See RELEASE.md.")
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.example.project"
            packageVersion = appVersionName
        }
    }
}

buildConfig {
    packageName("org.example.project.config")

    // Household sheet schema used by accounts granted Sheets access on the web. local.properties
    // stores schema-specific values under a `<schema>.<KEY>` prefix so both schemas' configs can
    // coexist and you flip with a single line.
    val activeSchema = localProperties.getProperty("SHEET_SCHEMA")
        ?: error("SHEET_SCHEMA missing from local.properties — see SETUP.md")

    fun schemaProp(key: String): String? =
        localProperties.getProperty("$activeSchema.$key") ?: localProperties.getProperty(key)

    fun requiredProp(key: String): String =
        localProperties.getProperty(key) ?: error("$key missing from local.properties — see SETUP.md")

    fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun field(name: String, value: String) {
        buildConfigField("String", name, "\"${escape(value)}\"")
    }

    field("SHEET_SCHEMA", activeSchema)
    // Optional: only the web build for granted accounts talks to the sheet (apps-script/sheets-gateway.gs).
    field("SHEETS_GATEWAY_URL", schemaProp("SHEETS_GATEWAY_URL").orEmpty())

    // Firebase web-app config (from the Firebase console `firebaseConfig`). None are secret —
    // access is enforced by Firebase Auth + firestore.rules.
    field("FIREBASE_API_KEY", requiredProp("FIREBASE_API_KEY"))
    field("FIREBASE_AUTH_DOMAIN", requiredProp("FIREBASE_AUTH_DOMAIN"))
    field("FIREBASE_PROJECT_ID", requiredProp("FIREBASE_PROJECT_ID"))
    field("FIREBASE_STORAGE_BUCKET", requiredProp("FIREBASE_STORAGE_BUCKET"))
    field("FIREBASE_MESSAGING_SENDER_ID", requiredProp("FIREBASE_MESSAGING_SENDER_ID"))
    field("FIREBASE_APP_ID", requiredProp("FIREBASE_APP_ID"))
    field("GEMINI_MODEL", requiredProp("GEMINI_MODEL"))
}
