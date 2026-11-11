plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.flare.auto"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.flare.auto"
        minSdk = 26
        targetSdk = 28
        versionCode = 3
        versionName = "1.1.0"
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
    testImplementation("junit:junit:4.13.2")
}
