rootProject.name = "phoenix"

//pluginManagement {
//    repositories {
//        google()
//        maven ("https://dl.bintray.com/kotlin/kotlin-eap")
//        gradlePluginPortal()
//        jcenter()
//    }
//
//    resolutionStrategy {
//        eachPlugin {
//            if (requested.id.id.startsWith("com.android.")) {
//                val androidVersion = if (System.getProperty("idea.executable") != "idea") "4.2.0-alpha04" else "4.0.0"
//                useModule("com.android.tools.build:gradle:$androidVersion")
//            }
//        }
//    }
//}

include(":phoenix-shared")

val skipAndroid: String? by settings
if (skipAndroid != "true" && System.getProperty("idea.executable") != "idea") {
    include(":phoenix-android")
}
