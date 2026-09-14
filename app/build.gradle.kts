plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.taskmanagementapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.taskmanagementapplication"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Authoritative online Supabase backend
        buildConfigField("String", "SUPABASE_URL", "\"https://ajgkqjemiqsyqirainok.supabase.co/\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_TZmXiU28oRtMd8R93WKisA_RE4tPkyb\"")
        buildConfigField("String", "BASE_URL", "\"https://ajgkqjemiqsyqirainok.supabase.co/\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"https://ajgkqjemiqsyqirainok.supabase.co/\"")
            buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_TZmXiU28oRtMd8R93WKisA_RE4tPkyb\"")
            buildConfigField("String", "BASE_URL", "\"https://ajgkqjemiqsyqirainok.supabase.co/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SUPABASE_URL", "\"https://ajgkqjemiqsyqirainok.supabase.co/\"")
            buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_TZmXiU28oRtMd8R93WKisA_RE4tPkyb\"")
            buildConfigField("String", "BASE_URL", "\"https://ajgkqjemiqsyqirainok.supabase.co/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)

    // Activity + Navigation
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Splash screen
    implementation(libs.core.splashscreen)

    // Keep existing
    implementation(libs.appcompat)
    implementation(libs.material)

    // Network — Retrofit + OkHttp
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // JSON — Moshi
    implementation(libs.moshi.kotlin)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore for secure token storage
    implementation(libs.datastore.preferences)

    // Coil for image loading
    implementation(libs.coil.compose)

    // Location
    implementation(libs.play.services.location)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}