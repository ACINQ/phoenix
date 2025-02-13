buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.8.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.kotlin}")
        classpath("app.cash.sqldelight:gradle-plugin:${Versions.sqlDelight}")

        // Firebase cloud messaging plugin
        classpath("com.google.gms:google-services:${Versions.fcmPlugin}")
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
