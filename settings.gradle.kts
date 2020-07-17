rootProject.name = "phoenix"

include(":phoenix-shared")

val skipAndroid: String? by settings
if (skipAndroid != "true" && !System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")) {
    include(":phoenix-android")
}
