import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()

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
                freeCompilerArgs = freeCompilerArgs + "-Xobjc-generics"
            }
        }
    }

    sourceSets {
        val coreKtxVersion: String by project
//        val kodeinDIVersion: String by project
        val kotlinXCoroutinesVersion: String by project

        val secp256k1Version = "0.3.0-1.4-M3"

        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("fr.acinq.eklair:eklair:0.2.0-1.4-M3")
//                implementation("org.kodein.di:kodein-di:$kodeinDIVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinXCoroutinesVersion")
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
                implementation(kotlin("stdlib-jdk7"))
                implementation("androidx.core:core-ktx:$coreKtxVersion")
                implementation("fr.acinq.secp256k1:secp256k1-jni-android:$secp256k1Version")
                implementation("io.ktor:ktor-network:1.3.2-1.4-M3")
                implementation("io.ktor:ktor-network-tls:1.3.2-1.4-M3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinXCoroutinesVersion")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("androidx.test.ext:junit:1.1.1")
                implementation("androidx.test.espresso:espresso-core:3.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinXCoroutinesVersion")
                val target = when {
                    currentOs.isLinux -> "linux"
                    currentOs.isMacOsX -> "darwin"
                    currentOs.isWindows -> "mingw"
                    else -> error("UnsupportedmOS $currentOs")
                }
                implementation("fr.acinq.secp256k1:secp256k1-jni-jvm-$target:$secp256k1Version")
            }
        }
        val iosMain by getting
        val iosTest by getting

        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

android {
    val androidCompileSdkVersion: Int by project
    val androidMinSdkVersion: Int by project
    val androidTargetSdkVersion: Int by project

    compileSdkVersion(androidCompileSdkVersion)
    defaultConfig {
        minSdkVersion(androidMinSdkVersion)
        targetSdkVersion(androidTargetSdkVersion)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
    val framework = kotlin.targets.getByName<KotlinNativeTarget>("ios").binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    val targetDir = File(buildDir, "xcode-frameworks")
    from({ framework.outputDirectory })
    into(targetDir)
}
tasks.getByName("build").dependsOn(packForXcode)
