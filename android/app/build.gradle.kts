plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Versionsnummer: In GitHub Actions wird die Laufnummer übergeben (-PversionCode=…),
// damit jede neue APK als Update über die alte installiert werden kann.
val appVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// Signierung: Bevorzugt die Werte aus GitHub-Secrets (Umgebungsvariablen),
// sonst der mitgelieferte Schlüssel in app/signing/ (siehe README).
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "de.fernfahrer.fahrtenbuch"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.fernfahrer.fahrtenbuch"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"
    }

    signingConfigs {
        create("release") {
            storeFile = file(env("SIGNING_KEYSTORE_PATH") ?: "signing/fahrtenbuch-release.jks")
            storePassword = env("SIGNING_STORE_PASSWORD") ?: "fahrtenbuch"
            keyAlias = env("SIGNING_KEY_ALIAS") ?: "fahrtenbuch"
            keyPassword = env("SIGNING_KEY_PASSWORD") ?: "fahrtenbuch"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
