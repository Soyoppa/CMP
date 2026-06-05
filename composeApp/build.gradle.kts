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
            implementation("com.google.firebase:firebase-analytics")
            implementation("com.google.firebase:firebase-auth")
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
            
            // Date/Time handling
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            
            // HTTP Client for API calls
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
            implementation("io.ktor:ktor-client-auth:3.0.3")
            implementation("io.ktor:ktor-client-logging:3.0.3")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(npm("firebase", "11.0.1"))
            
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
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
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

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.example.project"
            packageVersion = "1.0.0"
        }
    }
}

buildConfig {
    packageName("org.example.project.config")

    // Active schema. local.properties stores schema-specific values under
    // a `<schema>.<KEY>` prefix so both schemas' configs can coexist and
    // you flip with a single line.
    val activeSchema = localProperties.getProperty("SHEET_SCHEMA")
        ?: error("SHEET_SCHEMA missing from local.properties — see local.properties.example")

    // Look up `<activeSchema>.<key>` first, fall back to bare `<key>` for
    // back-compat with the pre-namespaced layout.
    fun schemaProp(key: String, default: String = ""): String {
        return localProperties.getProperty("$activeSchema.$key")
            ?: localProperties.getProperty(key, default)
    }

    fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun field(name: String, value: String) {
        buildConfigField("String", name, "\"${escape(value)}\"")
    }

    field("SHEET_SCHEMA", activeSchema)

    // Schema-specific (resolved via `<schema>.KEY`)
    field("SPREADSHEET_ID", schemaProp("SPREADSHEET_ID"))
    field("SHEET_RANGE", schemaProp("SHEET_RANGE", "'Data Dump'!A:H"))
    field("BUDGET_RANGE", schemaProp("BUDGET_RANGE", "'Bugdet vs Expense'!A:N"))
    field("SUMMARY_TREND", schemaProp("Summary", "'Summary Trend'!A:M"))
    field("SCRIPT_URL", schemaProp("SCRIPT_URL"))
    field("WRITE_SPREADSHEET_ID", schemaProp("WRITE_SPREADSHEET_ID"))
    field("WRITE_SCRIPT_URL", schemaProp("WRITE_SCRIPT_URL"))

    // Shared across schemas
    field("GOOGLE_API_KEY", localProperties.getProperty("GOOGLE_API_KEY", ""))
    field("OLLAMA_URL", localProperties.getProperty("OLLAMA_URL", ""))
    field("OLLAMA_MODEL", localProperties.getProperty("OLLAMA_MODEL", "llama3.1:8b"))

    // Firebase AI Logic (primary AI provider on web; Gemini Developer API / free tier).
    // These come from the Firebase console Web app `firebaseConfig` — none are secret
    // (the Firebase web apiKey is an identifier, not the Gemini key, which Firebase AI
    // Logic keeps server-side). Empty values disable the Gemini path → Ollama fallback.
    field("FIREBASE_API_KEY", localProperties.getProperty("FIREBASE_API_KEY", ""))
    field("FIREBASE_AUTH_DOMAIN", localProperties.getProperty("FIREBASE_AUTH_DOMAIN", ""))
    field("FIREBASE_PROJECT_ID", localProperties.getProperty("FIREBASE_PROJECT_ID", ""))
    field("FIREBASE_STORAGE_BUCKET", localProperties.getProperty("FIREBASE_STORAGE_BUCKET", ""))
    field("FIREBASE_MESSAGING_SENDER_ID", localProperties.getProperty("FIREBASE_MESSAGING_SENDER_ID", ""))
    field("FIREBASE_APP_ID", localProperties.getProperty("FIREBASE_APP_ID", ""))
    field("GEMINI_MODEL", localProperties.getProperty("GEMINI_MODEL", "gemini-2.0-flash"))
}
