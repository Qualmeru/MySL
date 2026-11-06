import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release signing key lives outside git; keystore.properties (also not in git) points at it.
// Every build signed with the same key can install over the previous one, whichever PC built it.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "se.mybus"
    compileSdk = 35

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val releaseKey = signingConfigs.findByName("release")
        if (releaseKey == null) {
            logger.warn("keystore.properties not found: signing with this PC's debug key. " +
                "Those APKs won't install over ones signed with the release key.")
        }
        // Debug builds use the release key too, so switching between debug and release keeps your data.
        getByName("debug") { if (releaseKey != null) signingConfig = releaseKey }
        getByName("release") {
            signingConfig = releaseKey ?: signingConfigs.getByName("debug")
            // Strips unused code and resources (mostly from Compose and the icon set): a much
            // smaller APK. Rules for what must survive are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    defaultConfig {
        applicationId = "se.mybus"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"
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
        buildConfig = true
    }

    // standard starts with no favourites; preset starts with Frihetsvägen and Helenelund.
    flavorDimensions += "favorites"
    productFlavors {
        create("standard") {
            dimension = "favorites"
            buildConfigField("boolean", "STARTER_STOPS", "false")
        }
        create("preset") {
            dimension = "favorites"
            buildConfigField("boolean", "STARTER_STOPS", "true")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub that throws in JVM unit tests; this is the real thing.
    testImplementation("org.json:json:20240303")
}
