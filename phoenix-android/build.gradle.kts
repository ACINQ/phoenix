plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.gms.google-services")
}

val chain: String by project

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "fr.acinq.phoenix.android"
        minSdk = 24
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            resValue("string", "CHAIN", chain)
            buildConfigField("String", "CHAIN", chain)
            isDebuggable = true
        }
        getByName("release") {
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
        kotlinCompilerVersion = Versions.kotlin
        kotlinCompilerExtensionVersion = Versions.Android.compose
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
    implementation("com.google.android.material:material:1.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
        version { strictly(Versions.coroutines) }
    }

    // -- AndroidX
    implementation("androidx.core:core-ktx:${Versions.Android.ktx}")
    // -- AndroidX: preferences
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.Android.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.Android.lifecycle}")
    // -- AndroidX: preferences datastore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // -- jetpack compose
    implementation("androidx.compose.ui:ui:${Versions.Android.compose}")
    implementation("androidx.compose.foundation:foundation:${Versions.Android.compose}")
    implementation("androidx.compose.foundation:foundation-layout:${Versions.Android.compose}")
    implementation("androidx.compose.ui:ui-tooling:${Versions.Android.compose}")
    implementation("androidx.compose.ui:ui-viewbinding:${Versions.Android.compose}")
    implementation("androidx.compose.runtime:runtime-livedata:${Versions.Android.compose}")
    implementation("androidx.compose.material:material:${Versions.Android.compose}")
    implementation("androidx.constraintlayout:constraintlayout-compose:${Versions.Android.constraintLayoutCompose}")
    // -- jetpack compose: navigation
    implementation("androidx.navigation:navigation-compose:${Versions.Android.navCompose}")

    // -- scanner zxing
    implementation("com.journeyapps:zxing-android-embedded:${Versions.Android.zxing}")

    // logging
    implementation("org.slf4j:slf4j-api:${Versions.slf4j}")
    implementation("com.github.tony19:logback-android:${Versions.Android.logback}")

    // firebase cloud messaging
    implementation("com.google.firebase:firebase-messaging:${Versions.Android.fcm}")

    testImplementation("junit:junit:${Versions.junit}")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Versions.Android.espresso}")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}
