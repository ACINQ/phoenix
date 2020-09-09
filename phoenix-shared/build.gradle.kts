plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.4.0"
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()

android {
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

kotlin {
    android {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    iosX64("ios") {
        binaries {
            framework {
                baseName = "PhoenixShared"
            }
        }
    }

    sourceSets {

        val coroutinesVersion = "1.3.9-native-mt"
        val serializationVersion = "1.0.0-RC"
        val secp256k1Version = "0.4.0"
        val ktorVersion = "1.4.0"
        val kodeinDIVersion = "7.1.0-master-87"
//        val kodeinDBVersion = "0.2.0"
        val kodeinDBVersion = "0.2.0-master-34"

        val commonMain by getting {
            dependencies {
                api("fr.acinq.eclair:eclair-kmp:snapshot")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
                api("org.kodein.di:kodein-di:$kodeinDIVersion")
                api("org.kodein.db:kodein-db:$kodeinDBVersion")
                api("org.kodein.db:kodein-db-serializer-kotlinx:$kodeinDBVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val androidMain by getting {
            dependencies {
                api("androidx.core:core-ktx:1.3.1")
                api("fr.acinq.secp256k1:secp256k1-kmp-jni-android:$secp256k1Version")
                api("io.ktor:ktor-network:$ktorVersion")
                api("io.ktor:ktor-network-tls:$ktorVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
                api("org.kodein.di:kodein-di-framework-android-x:$kodeinDIVersion")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("androidx.test.ext:junit:1.1.1")
                implementation("androidx.test.espresso:espresso-core:3.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                val target = when {
                    currentOs.isLinux -> "linux"
                    currentOs.isMacOsX -> "darwin"
                    currentOs.isWindows -> "mingw"
                    else -> error("UnsupportedmOS $currentOs")
                }
                implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-$target:$secp256k1Version")
            }
        }

        val iosMain by getting {}
        val iosTest by getting {}

        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = System.getenv("XCODE_CONFIGURATION") ?: "Debug"
    val framework = kotlin.targets.getByName<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>("ios").binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    val targetDir = File(buildDir, "xcode-frameworks")
    from({ framework.outputDirectory })
    into(targetDir)
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
