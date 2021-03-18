enableFeaturePreview("GRADLE_METADATA")
rootProject.name = "phoenix-kmm"

include(":phoenix-shared")


val isIntelliJ = System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")

// We cannot use buildSrc here for now.
// https://github.com/gradle/gradle/issues/11090#issuecomment-734795353

val skipAndroid: String? by settings

val withAndroid = if (skipAndroid == "true") {
    false
} else {
    val localFile = File("$rootDir/local.properties").takeIf { it.exists() }
        ?: error("Please create a $rootDir/local.properties file with either 'sdk.dir' or 'skip.android' properties")

    val localProperties = java.util.Properties()
    localFile.inputStream().use { localProperties.load(it) }

    if (localProperties["sdk.dir"] == null && localProperties["skip.android"] != "true") {
        error("local.properties: sdk.dir == null && skip.android != true : $localProperties")
    }

    localProperties["skip.android"] != "true"
}

System.setProperty("fr.acinq.phoenix.with-android", if (withAndroid) "true" else "false")

if (withAndroid && !isIntelliJ) {
    include(":phoenix-android")
}

System.setProperty("withAndroid", withAndroid.toString())
System.setProperty("isIntelliJ", isIntelliJ.toString())
