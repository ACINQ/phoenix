import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android")
}

val chain: String by project

val xCoreKtxVersion = "1.3.2"
val xLifecycleVersion = "2.3.0"
val xPrefsVersion = "1.1.1"
val composeVersion = "1.0.0-beta02"
val navComposeVersion = "1.0.0-alpha09"
val zxingVersion = "4.1.0"

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "fr.acinq.phoenix.android"
        minSdkVersion(24)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            resValue("string", "CHAIN", chain)
            buildConfigField("String", "CHAIN", chain)
            isDebuggable = true
        }
        release {
            resValue("string", "CHAIN", chain)
            buildConfigField("String", "CHAIN", chain)
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerVersion = "1.4.31"
        kotlinCompilerExtensionVersion = composeVersion
    }

}

kotlin {
    target {
        compilations.all {
            kotlinOptions.freeCompilerArgs += listOf("-Xskip-metadata-version-check", "-Xinline-classes")
        }
    }
}

dependencies {
    implementation(project(":phoenix-shared"))
    implementation("com.google.android.material:material:1.3.0")

    // -- AndroidX
    implementation("androidx.core:core-ktx:$xCoreKtxVersion")
//    implementation("androidx.appcompat:appcompat:1.2.0")
    // -- AndroidX: preferences
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$xLifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$xLifecycleVersion")
    // -- AndroidX: preferences
    implementation("androidx.preference:preference-ktx:$xPrefsVersion")

    // -- jetpack compose
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.foundation:foundation-layout:$composeVersion")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.0-alpha03")
    implementation("androidx.compose.ui:ui-tooling:$composeVersion")
    implementation("androidx.compose.ui:ui-viewbinding:$composeVersion")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")
    // -- jetpack compose: navigation
    implementation("androidx.navigation:navigation-compose:$navComposeVersion")

    // -- scanner zxing
    implementation("com.journeyapps:zxing-android-embedded:$zxingVersion")

    // logging
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("com.github.tony19:logback-android:2.0.0")

    testImplementation("junit:junit:4.13.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}
