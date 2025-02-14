import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.gms.google-services")
    id("kotlinx-serialization")
    alias(libs.plugins.compose)
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
    namespace = "fr.acinq.phoenix.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "fr.acinq.phoenix.testnet"
        minSdk = 26
        targetSdk = 34
        versionCode = 98
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

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=androidx.constraintlayout.compose.ExperimentalMotionApi"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    androidResources {
        localeFilters.addAll(listOf("en", "fr", "de", "es", "b+es+419", "cs", "pt-rBR", "sk", "vi", "sw"))
    }
}

kotlin {
    target {
        compilerOptions {
            optIn.addAll("kotlin.RequiresOptIn")
            freeCompilerArgs.addAll(listOf("-Xskip-metadata-version-check", "-Xinline-classes"))
        }
    }
}

//noinspection UseTomlInstead
dependencies {
    implementation(project(":phoenix-shared"))

    // -- AndroidX
    implementation("androidx.core:core-ktx:${libs.versions.androidx.corektx.get()}")
    implementation("androidx.appcompat:appcompat:${libs.versions.androidx.appcompat.get()}")
    // -- AndroidX: livedata
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${libs.versions.androidx.lifecycle.get()}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${libs.versions.androidx.lifecycle.get()}")
    // -- AndroidX: preferences datastore
    implementation("androidx.datastore:datastore-preferences:${libs.versions.androidx.datastore.get()}")
    // -- AndroidX: biometric
    implementation("androidx.biometric:biometric:${libs.versions.androidx.biometrics.get()}")
    // -- AndroidX: work manager
    implementation("androidx.work:work-runtime-ktx:${libs.versions.androidx.workmanager.get()}")

    // -- jetpack compose
    implementation("androidx.compose.ui:ui:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.foundation:foundation:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.foundation:foundation-layout:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.ui:ui-tooling:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.ui:ui-util:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.ui:ui-viewbinding:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.runtime:runtime-livedata:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.material:material:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.animation:animation:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.animation:animation-graphics:${libs.versions.androidx.compose.common.get()}")
    implementation("androidx.compose.material3:material3:${libs.versions.androidx.compose.material3.get()}")
    // -- jetpack compose: navigation
    implementation("androidx.navigation:navigation-compose:${libs.versions.androidx.compose.navigation.get()}")
    // -- jetpack compose: accompanist (utility library for compose)
    implementation("com.google.accompanist:accompanist-systemuicontroller:${libs.versions.accompanist.get()}")
    implementation("com.google.accompanist:accompanist-permissions:${libs.versions.accompanist.get()}")
    // -- constraint layout for compose
    implementation("androidx.constraintlayout:constraintlayout-compose:${libs.versions.androidx.compose.constraintlayout.get()}")

    // -- scanner zxing
    implementation("com.journeyapps:zxing-android-embedded:${libs.versions.zxing.get()}")

    // logging
    implementation("org.slf4j:slf4j-api:${libs.versions.slf4j.get()}")
    implementation("com.github.tony19:logback-android:${libs.versions.logback.get()}")

    // firebase cloud messaging
    implementation("com.google.firebase:firebase-messaging:${libs.versions.fcm.get()}")
    implementation("com.google.android.gms:play-services-base:${libs.versions.playservices.get()}")

    testImplementation("app.cash.sqldelight:sqlite-driver:${libs.versions.sqldelight.get()}")
    androidTestImplementation("androidx.test.ext:junit:${libs.versions.androidx.junit.get()}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${libs.versions.espresso.get()}")
}
