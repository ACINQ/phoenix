buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.0.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.kotlin}")
        classpath("com.squareup.sqldelight:gradle-plugin:${Versions.sqlDelight}")

        // Plugins for the legacy android app
        // Argument classes generation plugin for the androidx navigation component
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.AndroidLegacy.safeArgs}")
        // Firebase cloud messaging plugin
        classpath("com.google.gms:google-services:${Versions.fcmPlugin}")
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.buildDir)
}
