import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt) // Add Hilt plugin
    alias(libs.plugins.kotlin.serialization) // Add Kotlin Serialization plugin
    kotlin("kapt") // Add kapt for Hilt
}

// Load secrets from secrets.properties
val secretsProperties = Properties()
// Adjust the path relative to the root project's build.gradle.kts if needed,
// but assuming this app build.gradle.kts is run from the app module context,
// accessing the root project's file might need adjustment or use project.rootDir
// Let's try relative path from the app module first.
// If this fails, we might need project.rootDir.resolve("secrets.properties")
val secretsFile = project.rootProject.file("secrets.properties")
if (secretsFile.exists()) {
    try {
        secretsProperties.load(FileInputStream(secretsFile))
    } catch (e: Exception) {
        println("Warning: Could not load secrets.properties: ${e.message}")
    }
} else {
    println("Warning: secrets.properties file not found at ${secretsFile.absolutePath}. Build may fail or use default values.")
    // Optionally define default values here if needed for builds without the file
    // secretsProperties.setProperty("SUPABASE_URL", "\"YOUR_DEFAULT_URL\"")
    // secretsProperties.setProperty("SUPABASE_KEY", "\"YOUR_DEFAULT_KEY\"")
}

android {
    namespace = "com.hypest.supermarketreceiptsapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hypest.supermarketreceiptsapp"

        // Retrieve secrets and add as BuildConfig fields
        // Ensure the values are properly quoted for String types in BuildConfig
        val supabaseUrl = secretsProperties.getProperty("SUPABASE_URL", "\"\"") // Default to empty string if not found
        val supabaseKey = secretsProperties.getProperty("SUPABASE_KEY", "\"\"") // Default to empty string if not found
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")

        minSdk = 26 // Keep minSdk at 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.hypest.supermarketreceiptsapp.HiltTestRunner" // Use custom Hilt runner for testing
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Consider enabling for production builds
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Enable core library desugaring
        isCoreLibraryDesugaringEnabled = true // Uncommented
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Ensure this matches your Kotlin/Compose setup if needed
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Allow references to generated code
    kapt {
        correctErrorTypes = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core) // Added Material Icons

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.play.services.code.scanner)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    kaptAndroidTest(libs.hilt.compiler) // Hilt compiler for Android tests
    androidTestImplementation(libs.hilt.android.testing) // Hilt testing library

    // Supabase v3 (Using version catalog and BOM)
    implementation(platform("io.github.jan-tennert.supabase:bom:2.0.1")) // Use direct BOM coordinates
    implementation(libs.supabase.auth)      // Include Supabase Auth
    implementation(libs.supabase.postgrest) // Include Supabase Postgrest
    implementation(libs.supabase.realtime)  // Include Supabase Realtime
    // implementation(libs.supabase.storage) // Add if needed

    implementation(libs.ktor.client.core)    // Ktor client core (required by Supabase v3)
    implementation(libs.ktor.client.okhttp)  // Ktor OkHttp engine (supports WebSockets)
    implementation(libs.okhttp.logging.interceptor) // Added OkHttp Logging Interceptor
    // implementation(libs.supabase.compose.auth) // Add if Compose Auth UI is needed

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit Barcode Scanning (Removed - Using Google Code Scanner instead)
    // implementation(libs.mlkit.barcode.scanning)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test) // Add coroutines test dependency
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Core library desugaring dependency
    coreLibraryDesugaring(libs.android.desugarJdkLibs) // Uncommented
}
