plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
    kotlin("plugin.serialization") version "1.9.0"
}

dependencies {
    api(project(":kanvas"))
    implementation(kotlin("stdlib"))
    implementation("javax.xml.stream:stax-api:1.0")
    testImplementation(kotlin("test"))
}
