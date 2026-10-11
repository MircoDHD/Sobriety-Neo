// Build file of the Android app module.
// The shared (platform independent) code lives in the :shared module.
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33

    defaultConfig {
        // FORK: the original app uses "com.katiearose.sobriety". A different applicationId lets the
        // fork be installed NEXT TO the original (no data is overwritten). Change it only before
        // the first install: Android treats another id as another app, and its data does not move.
        // The code package / namespace (below) deliberately stays "com.katiearose.sobriety".
        applicationId = "io.github.mircodhd.sobrietyneo"
        minSdk = 21
        targetSdk = 33
        // Increase versionCode with every release, otherwise Android refuses to update the app.
        versionCode = 23
        versionName = "v10.1.0-neo.1"
        setProperty("archivesBaseName", "Sobriety-Neo-$versionName")
    }

    signingConfigs {
        // FORK: one fixed key for debug builds. Builds on GitHub get a fresh random debug key on
        // every run, and Android refuses to install an update that is signed with another key
        // ("App not installed"). The key is public (password "android"), which is fine for personal
        // test builds but it must NOT be used to publish the app in a store. Release builds will
        // need their own private key kept as a GitHub secret.
        getByName("debug") {
            storeFile = file("fork-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles += getDefaultProguardFile("proguard-android.txt")
            proguardFiles += file("proguard-rules.pro")
        }

        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles += getDefaultProguardFile("proguard-android.txt")
            proguardFiles += file("proguard-rules.pro")
            versionNameSuffix = "-debug"
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            //signingConfig = signingConfigs.debug
        }
    }
    compileOptions {
        // Core library desugaring lets java.time (used all over the app) work on Android 5 to 7.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
    }
    kotlinOptions {
        jvmTarget = "9"
    }
    buildFeatures {
        // View binding generates a class per layout (ActivityMainBinding ...), no findViewById.
        viewBinding = true
    }
    lint {
        // New texts are only written in English; translations are added by contributors.
        disable += listOf("MissingTranslation")
    }
    // Package of the code and of the generated R / BuildConfig classes (not the app id!).
    namespace = "com.katiearose.sobriety"
}

dependencies {

    // Platform independent code: data model, time calculation, saving/loading (see shared/).
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.0")
    //required for AndroidX Preference library
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs") {
        version {
            strictly ("1.2.0") //2.0.0 requires gradle 7.4.0-alpha10
        }
    }
    implementation("com.github.vipulasri:timelineview:1.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}
