plugins {
    id("com.android.application")
    kotlin("android")
}

val composeVersion = "0.1.0-dev14"
val composeCompilerVersion = "1.3.70-dev-withExperimentalGoogleExtensions-20200424"

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
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerVersion = composeCompilerVersion
        kotlinCompilerExtensionVersion = composeVersion
    }

}

kotlin {
    target {
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xskip-metadata-version-check"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":phoenix-shared"))
//    implementation("androidx.core:core-ktx:1.3.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.material:material:1.1.0")
    implementation("androidx.ui:ui-layout:$composeVersion")
    implementation("androidx.ui:ui-material:$composeVersion")
    implementation("androidx.ui:ui-tooling:$composeVersion")
    testImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
}

