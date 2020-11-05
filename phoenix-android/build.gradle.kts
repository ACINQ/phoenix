import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    kotlin("android")
}

val composeVersion = "1.0.0-alpha05"

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
        val release by getting {
            isMinifyEnabled = false
//            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerVersion = "1.4.10"
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

    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.0-beta01")

    implementation("androidx.appcompat:appcompat:1.2.0")

    implementation("com.google.android.material:material:1.2.1")

    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")

    implementation("androidx.ui:ui-tooling:$composeVersion")

    testImplementation("junit:junit:4.13.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}
