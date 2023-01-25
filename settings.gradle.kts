pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "phoenix"

// Android apps (legacy and kmm) may be skipped to make life easier on iOS developers.
// Use `skip.android` in `local.properties` to define whether android app are built or not.
// By default, Android apps are NOT skipped.
val skipAndroid = File("$rootDir/local.properties").takeIf { it.exists() }
    ?.inputStream()?.use { java.util.Properties().apply { load(it) } }
    ?.run { getProperty("skip.android", "false")?.toBoolean() }
    ?: false

// Inject the skip value in System properties so that it can be used in other gradle build files.
System.setProperty("includeAndroid", (!skipAndroid).toString())

// The shared app is always included.
// include(":phoenix-shared")

// Android apps are optional.
if (!skipAndroid) {
    // include(":phoenix-android")
    include(":phoenix-legacy")
}
