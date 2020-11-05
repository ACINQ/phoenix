buildscript {
    repositories {
        google()
        jcenter()
    }

    dependencies {
        val androidVersion = if (System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")) "4.0.1" else "4.2.0-alpha15"
        classpath("com.android.tools.build:gradle:$androidVersion")

        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/acinq/libs")
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kodein-framework/Kodein-DB")
        google()
        jcenter()
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.buildDir)
}
