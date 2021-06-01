pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "phoenix-kmm"

enableFeaturePreview("GRADLE_METADATA")

// We use a property defined in `local.properties` to know whether we should build the android application or not.
// For example, iOS developers may want to skip that most of the time.
val skipAndroid = File("$rootDir/local.properties").takeIf { it.exists() }
    ?.inputStream()?.use { java.util.Properties().apply { load(it) } }
    ?.run { getProperty("skip.android", "true")?.toBoolean() }
    ?: true

// Use system properties to inject the property in other gradle build files.
System.setProperty("includeAndroid", (!skipAndroid).toString())

include(":phoenix-shared")
if (!skipAndroid) {
    include(":phoenix-android")
}
