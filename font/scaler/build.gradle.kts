plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:core"))

    implementation(project(":font:colr"))
    implementation(project(":font:sfnt"))
    implementation(kotlin("stdlib"))
}
