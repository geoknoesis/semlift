plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(project(":jsonld"))
    api("org.apache.jena:apache-jena-libs:5.6.0")
    api("org.apache.jena:jena-shacl:5.6.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

