plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:core"))
    api(project(":math"))

    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
