import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.gms.google-services")
}

fun gitCommitHash(): String {
    val stream = ByteArrayOutputStream()
    project.exec {
        commandLine = "git rev-parse --verify --short HEAD".split(" ")
        standardOutput = stream
    }
    return String(stream.toByteArray()).split("\n").first()
}

val chain: String by project

android {
    compileSdk = 33
    defaultConfig {
        applicationId = "fr.acinq.phoenix.testnet"
        minSdk = 24
        targetSdk = 33
        versionCode = 40
        versionName = gitCommitHash()
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
        applicationVariants.all {
            outputs.forEach {
                val apkName = "phoenix-${defaultConfig.versionCode}-${defaultConfig.versionName}-${chain.drop(1).dropLast(1)}-${buildType.name}.apk"
                (it as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.Android.composeCompiler
    }

    packagingOptions {
        resources.merges.add("reference.conf")
    }
}

kotlin {
    target {
        compilations.all {
            kotlinOptions.freeCompilerArgs += listOf("-Xskip-metadata-version-check", "-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}

dependencies {
    implementation(project(":phoenix-shared"))
    api(project(":phoenix-legacy"))

    implementation("com.google.android.material:material:1.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
        version { strictly(Versions.coroutines) }
    }

    // -- AndroidX
    implementation("androidx.core:core-ktx:${Versions.Android.coreKtx}")
    // -- AndroidX: livedata
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.Android.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.Android.lifecycle}")
    // -- AndroidX: preferences datastore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // -- AndroidX: biometric
    implementation("androidx.biometric:biometric:${Versions.Android.biometrics}")
    // -- AndroidX: work manager
    implementation("androidx.work:work-runtime-ktx:${Versions.AndroidLegacy.work}")


    // -- jetpack compose
    implementation("androidx.compose.ui:ui:${Versions.Android.compose}")
    implementation("androidx.compose.foundation:foundation:${Versions.Android.compose}")
    implementation("androidx.compose.foundation:foundation-layout:${Versions.Android.compose}")
    implementation("androidx.compose.ui:ui-tooling:${Versions.Android.compose}")
    implementation("androidx.compose.ui:ui-viewbinding:${Versions.Android.compose}")
    implementation("androidx.compose.runtime:runtime-livedata:${Versions.Android.compose}")
    implementation("androidx.compose.material:material:${Versions.Android.compose}")
    implementation("androidx.compose.animation:animation:${Versions.Android.compose}")
    implementation("androidx.compose.animation:animation-graphics:${Versions.Android.compose}")
    // -- jetpack compose: navigation
    implementation("androidx.navigation:navigation-compose:${Versions.Android.navCompose}")
    // -- jetpack compose: accompanist (utility library for compose)
    implementation("com.google.accompanist:accompanist-systemuicontroller:${Versions.Android.accompanist}")
    implementation("com.google.accompanist:accompanist-permissions:${Versions.Android.accompanist}")
    implementation("com.google.accompanist:accompanist-pager:${Versions.Android.accompanist}")

    // -- scanner zxing
    implementation("com.journeyapps:zxing-android-embedded:${Versions.Android.zxing}")

    // logging
    implementation("org.slf4j:slf4j-api:${Versions.slf4j}")
    implementation("com.github.tony19:logback-android:${Versions.Android.logback}")

    // firebase cloud messaging
    implementation("com.google.firebase:firebase-messaging:${Versions.Android.fcm}")

    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

    testImplementation("junit:junit:${Versions.junit}")
    testImplementation("com.squareup.sqldelight:sqlite-driver:${Versions.sqlDelight}")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Versions.Android.espresso}")
}
