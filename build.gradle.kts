import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24" apply false
}

allprojects {
    group = "io.semlift"
    version = "0.1.0"
}

subprojects {
    buildDir = rootProject.layout.buildDirectory.dir(project.name).get().asFile

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

