plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation("org.apache.spark:spark-sql_2.12:3.5.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(project(":jsonld"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

