buildscript {
    repositories {
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        google()
        gradlePluginPortal()
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4-M3")
        classpath("com.android.tools.build:gradle:4.0.0")
    }
}

group = "fr.acinq.phoenix"
version = "0.1.0"

val coreKtxVersion by extra { "1.3.0" }
//val kodeinDIVersion by extra { "7.0.0" }
val kotlinXCoroutinesVersion by extra { "1.3.7-1.4-M3" }

val androidCompileSdkVersion by extra { 30 }
val androidMinSdkVersion by extra { 24 }
val androidTargetSdkVersion by extra { 30 }

val android by extra { "1.3.0" }

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/acinq/libs")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kodein-framework/kodein-dev")
        google()
        jcenter()
    }
}
