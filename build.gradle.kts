buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.agp)
        classpath(libs.kgp)
        classpath(libs.serialization)
        classpath(libs.sqldelight.plugin)
        classpath(libs.fcm.plugin)
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
