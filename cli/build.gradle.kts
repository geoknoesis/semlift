plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":jena"))
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("io.semlift.cli.SemanticLiftCliKt")
}

