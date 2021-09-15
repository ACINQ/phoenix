pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "phoenix"

// The Android app is optional and skipped by default to make life easier on iOS developers.
// The `skip.android` property in `local.properties` define whether the android application is built or not.
val skipAndroid = File("$rootDir/local.properties").takeIf { it.exists() }
    ?.inputStream()?.use { java.util.Properties().apply { load(it) } }
    ?.run { getProperty("skip.android", "true")?.toBoolean() }
    ?: true

// Inject the skip value in System properties so that it can be used in other gradle build files.
System.setProperty("includeAndroid", (!skipAndroid).toString())

// The shared app is always included.
include(":phoenix-shared")

// Android apps are optional.
if (!skipAndroid) {
    include(":phoenix-android")
    include(":phoenix-legacy")
}
