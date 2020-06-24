plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android-extensions")
}

val composeVersion = "0.1.0-dev13"

dependencies {
    val coreKtxVersion: String by project

    implementation(project(":phoenix-shared"))

    implementation(kotlin("stdlib-jdk7"))

    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.appcompat:appcompat:1.1.0")

    implementation("com.google.android.material:material:1.1.0")
    implementation("androidx.ui:ui-layout:$composeVersion")
    implementation("androidx.ui:ui-material:$composeVersion")
    implementation("androidx.ui:ui-tooling:$composeVersion")
}

android {
    val androidCompileSdkVersion: Int by project
    val androidMinSdkVersion: Int by project
    val androidTargetSdkVersion: Int by project

    compileSdkVersion(androidCompileSdkVersion)
    defaultConfig {
        applicationId = "fr.acinq.phoenix.phoenix-android"
        minSdkVersion(androidMinSdkVersion)
        targetSdkVersion(androidTargetSdkVersion)

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = composeVersion
        kotlinCompilerVersion = "1.3.70-dev-withExperimentalGoogleExtensions-20200424"
    }

}
