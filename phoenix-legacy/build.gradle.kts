import java.io.ByteArrayOutputStream

plugins {
  id("com.android.library")
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
  compileSdk = 32
  ndkVersion = "23.1.7779620"
  defaultConfig {
    minSdk = 24
    targetSdk = 32
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildTypes {
    val libCode = 36
    getByName("debug") {
      resValue("string", "CHAIN", chain)
      buildConfigField("String", "CHAIN", chain)
      buildConfigField("int", "LIB_CODE", libCode.toString()) // lib version code
      buildConfigField("String", "LIB_COMMIT", "\"${gitCommitHash()}\"") // lib version name
    }
    getByName("release") {
      resValue("string", "CHAIN", chain)
      buildConfigField("String", "CHAIN", chain)
      buildConfigField("int", "LIB_CODE", libCode.toString()) // lib version code
      buildConfigField("String", "LIB_COMMIT", "\"${gitCommitHash()}\"") // lib version name
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

  implementation("com.google.android.material:material:${Versions.AndroidLegacy.material}")

  // ANDROIDX
  implementation("androidx.core:core-ktx:${Versions.Android.ktx}")
  implementation("androidx.appcompat:appcompat:${Versions.AndroidLegacy.appCompat}")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
  // ANDROIDX - navigation
  implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidLegacy.navigation}")
  implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidLegacy.navigation}")
  // ANDROIDX - constraint layout
  implementation("androidx.constraintlayout:constraintlayout:${Versions.AndroidLegacy.constraint}")
  // ANDROIDX - viewmodel + livedata
  implementation("androidx.lifecycle:lifecycle-extensions:${Versions.AndroidLegacy.lifecycleExtensions}")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.AndroidLegacy.lifecycle}")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.AndroidLegacy.lifecycle}")
  implementation("androidx.lifecycle:lifecycle-common-java8:${Versions.AndroidLegacy.lifecycle}")
  // ANDROIDX - biometric
  implementation("androidx.biometric:biometric:${Versions.Android.biometrics}")
  // ANDROIDX - preferences
  implementation("androidx.preference:preference-ktx:${Versions.Android.prefs}")
  // ANDROIDX - work manager
  implementation("androidx.work:work-runtime-ktx:${Versions.AndroidLegacy.work}") {
    exclude(group = "com.google.guava", module = "listenablefuture")
  }
  // ANDROIDX - view pager 2
  implementation("androidx.viewpager2:viewpager2:${Versions.AndroidLegacy.viewpager}")

  // -- AndroidX: preferences datastore
  implementation("androidx.datastore:datastore-preferences:1.0.0")

  // SQLDelight
  implementation("com.squareup.sqldelight:android-driver:${Versions.sqlDelight}")

  // logging
  implementation("org.slf4j:slf4j-api:${Versions.slf4j}")
  implementation("com.github.tony19:logback-android:${Versions.Android.logback}")

  // eclair core
  implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:${Versions.secp256k1}")
  api("fr.acinq.eclair:eclair-core_2.11:${Versions.AndroidLegacy.eclair}")

  // eventbus
  implementation("org.greenrobot:eventbus:${Versions.AndroidLegacy.eventbus}")

  // zxing
  implementation("com.journeyapps:zxing-android-embedded:${Versions.Android.zxing}")

  // tests
  implementation("androidx.legacy:legacy-support-v4:1.0.0")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test:runner:${Versions.Android.testRunner}")
  androidTestImplementation("androidx.test.espresso:espresso-core:${Versions.Android.espresso}")

  // tor
  implementation("info.guardianproject:jtorctl:${Versions.AndroidLegacy.torCtl}") // controlling tor instance via its control port
  implementation("com.msopentech.thali:universal:${Versions.AndroidLegacy.torWrapper}") // core library for the tor wrapper
  implementation("com.msopentech.thali.toronionproxy.android:android:${Versions.AndroidLegacy.torWrapper}@aar")

  // firebase cloud messaging
  implementation("com.google.firebase:firebase-messaging:${Versions.Android.fcm}")
}
