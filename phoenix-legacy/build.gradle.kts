import java.io.ByteArrayOutputStream

plugins {
  id("com.android.application")
  kotlin("android")
  id("kotlin-kapt")
  id("kotlin-android-extensions")
  id("androidx.navigation.safeargs.kotlin")
  id("com.google.gms.google-services")
  id("com.squareup.sqldelight")
  id("kotlinx-serialization")
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
  compileSdk = 30
  ndkVersion = "21.3.6528147"
  defaultConfig {
    applicationId = "fr.acinq.phoenix.testnet"
    minSdk = 24
    targetSdk = 30
    versionCode = 33
    versionName = "${gitCommitHash()}"
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
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    applicationVariants.all {
      outputs.forEach {
        val chain = buildType.resValues.get("CHAIN")?.value ?: throw RuntimeException("a valid chain name is required")
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
  packagingOptions {
    resources.merges.add("reference.conf")
  }
  buildFeatures {
    dataBinding = true
  }
  externalNativeBuild {
    cmake {
      path = file("CMakeLists.txt")
    }
  }
}

sqldelight {
  database("Database") {
    packageName = "fr.acinq.phoenix.legacy.db"
    sourceFolders = listOf("sqldelight")
    schemaOutputDirectory = file("src/main/sqldelight/databases")
  }
}

dependencies {
  implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

  // base dependencies
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32")
  implementation("androidx.core:core-ktx:1.3.2")
  implementation("androidx.appcompat:appcompat:1.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")

  // ANDROIDX - material
  implementation("com.google.android.material:material:1.4.0-alpha02")

  // ANDROIDX - navigation
  val nav_version = "2.3.5"
  implementation("androidx.navigation:navigation-fragment-ktx:$nav_version")
  implementation("androidx.navigation:navigation-ui-ktx:$nav_version")

  // ANDROIDX - constraint layout
  val constraint_version = "2.0.4"
  implementation("androidx.constraintlayout:constraintlayout:$constraint_version")

  // ANDROIDX - viewmodel + livedata
  val lifecycle_version = "2.3.1"
  implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version")
  implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycle_version")

  // ANDROIDX - biometric
  val biometric_version = "1.1.0"
  implementation("androidx.biometric:biometric:$biometric_version")

  // ANDROIDX - preferences
  val preference_version = "1.1.1"
  implementation("androidx.preference:preference-ktx:$preference_version")

  // ANDROIDX - work manager
  val work_version = "2.5.0"
  implementation("androidx.work:work-runtime-ktx:$work_version") {
    exclude(group = "com.google.guava", module = "listenablefuture")
  }

  // ANDROIDX - view pager 2
  val view_pager_version = "1.0.0"
  implementation("androidx.viewpager2:viewpager2:$view_pager_version")

  // SQLDelight
  implementation("com.squareup.sqldelight:android-driver:1.5.0")

  // logging
  implementation("org.slf4j:slf4j-api:1.7.30")
  implementation("com.github.tony19:logback-android:2.0.0")

  // eclair core
  val eclair_version = "0.4.15-android-phoenix-SNAPSHOT"
  implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.5.1")
  implementation("fr.acinq.eclair:eclair-core_2.11:$eclair_version")

  // eventbus
  val eventbus_version = "3.1.1"
  implementation("org.greenrobot:eventbus:$eventbus_version")

  // zxing
  val zxing_version = "4.1.0"
  implementation("com.journeyapps:zxing-android-embedded:$zxing_version")

  // tests
  implementation("androidx.legacy:legacy-support-v4:1.0.0")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test:runner:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")

  // tor
  val tor_ctl_version = "0.4"
  val tor_wrapper_version = "0.0.5"
  implementation("info.guardianproject:jtorctl:$tor_ctl_version") // controlling tor instance via its control port
  implementation("com.msopentech.thali:universal:$tor_wrapper_version") // core library for the tor wrapper
  implementation("com.msopentech.thali.toronionproxy.android:android:$tor_wrapper_version@aar")

  // firebase cloud messaging
  val fcm_version = "21.1.0"
  implementation("com.google.firebase:firebase-messaging:$fcm_version")
}
