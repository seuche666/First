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

// Signierung: Der Schlüssel kommt ausschließlich aus Umgebungsvariablen (in GitHub Actions
// aus den Repository-Secrets, siehe README). Er liegt NICHT im Repository.
// Ohne diese Variablen entsteht eine unsignierte Release-APK (nur zum Testen).
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val signingKeystore = env("SIGNING_KEYSTORE_PATH")
val signingPassword = env("SIGNING_PASSWORD")
val canSign = signingKeystore != null && signingPassword != null

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
        if (canSign) {
            create("release") {
                storeFile = file(signingKeystore!!)
                storePassword = signingPassword
                keyAlias = env("SIGNING_KEY_ALIAS") ?: "fahrtenbuch"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSign) signingConfig = signingConfigs.getByName("release")
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
    testOptions {
        // Für Robolectric-Tests (Datenbank-Migration, Repository)
        unitTests.isIncludeAndroidResources = true
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
