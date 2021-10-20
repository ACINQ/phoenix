import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("com.squareup.sqldelight")
    if (System.getProperty("includeAndroid")?.toBoolean() == true) {
        id("com.android.library")
    }
}

val includeAndroid = System.getProperty("includeAndroid")?.toBoolean() ?: false
if (includeAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        compileSdk = 31
        defaultConfig {
            minSdk = 24
            targetSdk = 31
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

        // workaround for https://youtrack.jetbrains.com/issue/KT-43944
        configurations {
            create("androidTestApi")
            create("androidTestDebugApi")
            create("androidTestReleaseApi")
            create("testApi")
            create("testDebugApi")
            create("testReleaseApi")
        }
    }
}

kotlin {
    if (includeAndroid) {
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
            compilations.all {
                kotlinOptions.freeCompilerArgs +=
                    "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi"
                kotlinOptions.freeCompilerArgs +=
                    "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi"
                kotlinOptions.freeCompilerArgs +=
                    "-Xoverride-konan-properties=osVersionMin.ios_x64=14.0;osVersionMin.ios_arm64=14.0"
            }
        }
    }

    sourceSets {
        sourceSets["commonMain"].dependencies {
            api("fr.acinq.lightning:lightning-kmp:${Versions.lightningKmp}")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:${Versions.serialization}")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
            api("org.kodein.memory:kodein-memory-files:${Versions.kodeinMemory}")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
                version { strictly(Versions.coroutines) }
            }
            implementation("io.ktor:ktor-client-core:${Versions.ktor}")
            implementation("io.ktor:ktor-client-json:${Versions.ktor}")
            implementation("io.ktor:ktor-client-serialization:${Versions.ktor}")
            implementation("com.squareup.sqldelight:runtime:${Versions.sqlDelight}")
            implementation("com.squareup.sqldelight:coroutines-extensions:${Versions.sqlDelight}")
        }

        sourceSets["commonTest"].dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation("io.ktor:ktor-client-mock:${Versions.ktor}")
        }

        if (includeAndroid) {
            sourceSets["androidMain"].dependencies {
                implementation("androidx.core:core-ktx:${Versions.Android.ktx}")
                implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:${Versions.secp256k1}")
                implementation("io.ktor:ktor-network:${Versions.ktor}")
                implementation("io.ktor:ktor-network-tls:${Versions.ktor}")
                implementation("io.ktor:ktor-client-android:${Versions.ktor}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
                implementation("com.squareup.sqldelight:android-driver:${Versions.sqlDelight}")
            }
            sourceSets["androidTest"].dependencies {
                implementation(kotlin("test-junit"))
                implementation("androidx.test.ext:junit:1.1.2")
                implementation("androidx.test.espresso:espresso-core:3.3.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
                val currentOs = org.gradle.internal.os.OperatingSystem.current()
                val target = when {
                    currentOs.isLinux -> "linux"
                    currentOs.isMacOsX -> "darwin"
                    currentOs.isWindows -> "mingw"
                    else -> error("Unsupported OS $currentOs")
                }
                implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-$target:${Versions.secp256k1}")
                implementation("com.squareup.sqldelight:sqlite-driver:${Versions.sqlDelight}")
            }
        }

        sourceSets["iosMain"].dependencies {
            implementation("io.ktor:ktor-client-ios:${Versions.ktor}")
            implementation("com.squareup.sqldelight:native-driver:${Versions.sqlDelight}")
        }

        sourceSets["iosTest"].dependencies {
            implementation("com.squareup.sqldelight:native-driver:${Versions.sqlDelight}")
        }

        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

sqldelight {
    database("ChannelsDatabase") {
        packageName = "fr.acinq.phoenix.db"
        sourceFolders = listOf("channelsdb")
    }
    database("PaymentsDatabase") {
        packageName = "fr.acinq.phoenix.db"
        sourceFolders = listOf("paymentsdb")
    }
    database("AppDatabase") {
        packageName = "fr.acinq.phoenix.db"
        sourceFolders = listOf("appdb")
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
    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    from({ framework.outputDirectory })
    into(buildDir.resolve("xcode-frameworks"))
}
tasks.getByName("build").dependsOn(packForXcode)

afterEvaluate {
    tasks.withType<AbstractTestTask> {
        testLogging {
            events("passed", "skipped", "failed", "standard_out", "standard_error")
            showExceptions = true
            showStackTraces = true
        }
    }
}
