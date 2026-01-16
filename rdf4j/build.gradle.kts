plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(project(":jsonld"))
    implementation("org.eclipse.rdf4j:rdf4j-runtime:5.2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
}


