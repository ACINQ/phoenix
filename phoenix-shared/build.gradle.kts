plugins {
    val withAndroid = System.getProperty("withAndroid")!!.toBoolean()
    if (withAndroid) id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.4.10"
    id("com.squareup.sqldelight")
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()

val withAndroid = System.getProperty("withAndroid")!!.toBoolean()

if (withAndroid) {
    // The `android` extension function is not in classpath if android plugin is not applied
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        compileSdkVersion(30)
        defaultConfig {
            minSdkVersion(24)
            targetSdkVersion(30)
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
}

kotlin {
    if (withAndroid) {
        android {
            compilations.all {
                kotlinOptions.jvmTarget = "1.8"
            }
        }
    }

    ios {
        binaries {
            framework {
                baseName = "PhoenixShared"
            }
        }
    }

    sourceSets {

        val coroutinesVersion = "1.3.9-native-mt-2"
        val serializationVersion = "1.0.0"
        val secp256k1Version = "0.4.1"
        val ktorVersion = "1.4.1"
        val kodeinDBVersion = "0.2.0-beta"
        val sqldelightVersion = "1.4.4"

        val commonMain by getting {
            dependencies {
                api("fr.acinq.eclair:eclair-kmp:snapshot")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                api("org.kodein.db:kodein-db:$kodeinDBVersion")
                api("org.kodein.db:kodein-db-serializer-kotlinx:$kodeinDBVersion")
                api("org.kodein.memory:kodein-memory-files:0.4.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.1.0")
                api("io.ktor:ktor-client-core:$ktorVersion")
                api("io.ktor:ktor-client-json:$ktorVersion")
                api("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("com.squareup.sqldelight:runtime:$sqldelightVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (withAndroid) {
            val androidMain by getting {
                dependencies {
                    api("androidx.core:core-ktx:1.3.2")
                    api("fr.acinq.secp256k1:secp256k1-kmp-jni-android:$secp256k1Version")
                    api("io.ktor:ktor-network:$ktorVersion")
                    api("io.ktor:ktor-network-tls:$ktorVersion")
                    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
                    implementation("com.squareup.sqldelight:android-driver:$sqldelightVersion")
                }
            }
            val androidTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation("androidx.test.ext:junit:1.1.2")
                    implementation("androidx.test.espresso:espresso-core:3.3.0")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                    val target = when {
                        currentOs.isLinux -> "linux"
                        currentOs.isMacOsX -> "darwin"
                        currentOs.isWindows -> "mingw"
                        else -> error("UnsupportedmOS $currentOs")
                    }
                    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-$target:$secp256k1Version")
                    implementation("com.squareup.sqldelight:sqlite-driver:$sqldelightVersion")
                }
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-ios:$ktorVersion")
                implementation("com.squareup.sqldelight:native-driver:$sqldelightVersion")
            }
        }

        val iosTest by getting {
            dependencies {
                implementation("com.squareup.sqldelight:native-driver:$sqldelightVersion")
            }
        }

        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

sqldelight {
    database("ChannelsDatabase") {
        packageName = "fr.acinq.phoenix.db"
    }
}

val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = (project.findProperty("XCODE_CONFIGURATION") as? String) ?: "Debug"
    val platformName = (project.findProperty("XCODE_PLATFORM_NAME") as? String) ?: "iphonesimulator"
    val targetName = when (platformName) {
        "iphonesimulator" -> "iosX64"
        "iphoneos" -> "iosArm64"
        else -> error("Unknown XCode platform $platformName")
    }
    val framework = kotlin.targets.getByName<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    from({ framework.outputDirectory })
    into(buildDir.resolve("xcode-frameworks"))
}
tasks.getByName("build").dependsOn(packForXcode)

afterEvaluate {
    tasks.withType<AbstractTestTask>() {
        testLogging {
            events("passed", "skipped", "failed", "standard_out", "standard_error")
            showExceptions = true
            showStackTraces = true
        }
    }
}
