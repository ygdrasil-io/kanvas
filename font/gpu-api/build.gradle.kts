plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:core"))

    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
