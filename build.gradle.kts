buildscript {
    repositories {
        google()
        jcenter()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")

        val isIntelliJ = System.getProperty("isIntelliJ")!!.toBoolean()
        val androidVersion = if (isIntelliJ) "4.0.1" else "7.0.0-alpha02"
        classpath("com.android.tools.build:gradle:$androidVersion")

        val sqldelightVersion = "1.4.4"
        classpath("com.squareup.sqldelight:gradle-plugin:$sqldelightVersion")
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/acinq/libs")
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/kodein-framework/Kodein-DB")
        maven("https://dl.bintray.com/kodein-framework/Kodein-Memory")
        google()
        jcenter()
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.buildDir)
}
