plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":math"))

    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
