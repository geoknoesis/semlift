plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(project(":jsonld"))
    implementation(project(":jena"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(project(":jena"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

tasks.register<JavaExec>("runOgcBblockValidator") {
    group = "application"
    description = "Validate OGC building block examples and output per-step artifacts."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.semlift.ogc.OgcBblocksValidatorKt")
}

