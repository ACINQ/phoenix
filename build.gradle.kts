buildscript {
    repositories {
        google()
        maven ("https://dl.bintray.com/kotlin/kotlin-eap")
        jcenter()
    }

    dependencies {
        val androidVersion = if (System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")) "4.0.0" else "4.2.0-alpha04"
        classpath("com.android.tools.build:gradle:$androidVersion")

        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.0-rc")
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/acinq/libs")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kodein-framework/kodein-dev")
        google()
        jcenter()
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.buildDir)
}
