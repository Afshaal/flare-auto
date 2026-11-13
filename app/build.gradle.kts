import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Release signing comes from an untracked keystore.properties next to settings.gradle.kts, or from the
 * environment. Without either, release APKs are built unsigned (sign them afterwards) and debug builds are
 * unaffected. Keys: storeFile, storePassword, keyAlias, keyPassword, storeType (optional, e.g. pkcs12).
 * Environment: FLARE_KEYSTORE, FLARE_KEYSTORE_PASSWORD, FLARE_KEY_ALIAS, FLARE_KEY_PASSWORD, FLARE_KEYSTORE_TYPE.
 */
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun secret(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)
val releaseStore = secret("storeFile", "FLARE_KEYSTORE")

android {
    namespace = "app.flare.auto"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.flare.auto"
        minSdk = 26
        targetSdk = 28
        versionCode = 4
        versionName = "1.2.0"
        // MapLibre ships native code: arm64-v8a for the head unit (and phones), x86_64 for emulators/BlueStacks.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../AndroidManifest.xml")
            java.setSrcDirs(listOf("../src"))
            res.setSrcDirs(listOf("../res"))
            assets.setSrcDirs(listOf("../assets"))
        }
        getByName("test") {
            java.setSrcDirs(listOf("../test"))
        }
    }

    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = rootProject.file(releaseStore)
            storePassword = secret("storePassword", "FLARE_KEYSTORE_PASSWORD")
            keyAlias = secret("keyAlias", "FLARE_KEY_ALIAS")
            keyPassword = secret("keyPassword", "FLARE_KEY_PASSWORD")
            secret("storeType", "FLARE_KEYSTORE_TYPE")?.let { storeType = it }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 + resource shrinking. Everything looked up by name at runtime is kept: drawables in
            // res/raw/keep.xml, library code in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            // Verification aid: `./gradlew assembleDebug -Pflare.minifyDebug=true` builds the debug harnesses with the
            // release R8 rules, so the shrunk app can be exercised on a device (SmokeActivity, check activities).
            if (project.findProperty("flare.minifyDebug") == "true") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-debug-checks.pro")
            }
            // Verification aid: `-Pflare.debugIdSuffix=.integration` installs the debug build next to the real app
            // (own package, own data and pack folder) on a test emulator. Unset by default: no change to any build.
            (project.findProperty("flare.debugIdSuffix") as String?)?.let { applicationIdSuffix = it }
        }
    }

    // This sideloaded build retains target 28 for existing head-unit behavior.
    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.graphhopper:graphhopper-core:1.0")
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-themes:0.25.0")
    // Vector display map (OpenGL ES 3 variant; the default 13.x artifact needs Vulkan). 13.0.2 is the newest
    // release built with Kotlin 2.0, matching this project's Kotlin plugin.
    implementation("org.maplibre.gl:android-sdk-opengl:13.0.2")
    testImplementation("junit:junit:4.13.2")
    // Test-only: real org.json on the JVM (android.jar ships stubs). Not packaged in the app.
    testImplementation("org.json:json:20231013")
}
