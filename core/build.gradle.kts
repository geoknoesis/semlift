plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    api("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.17.1")
    implementation("org.yaml:snakeyaml:2.2")
    api("com.networknt:json-schema-validator:1.0.88")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation(project(":jena"))
    testImplementation(project(":jsonld"))
}

