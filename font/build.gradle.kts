plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:core"))
    api(project(":font:sfnt"))
    api(project(":font:scaler"))
    api(project(":font:text"))
    api(project(":font:glyph"))
    api(project(":font:gpu-api"))

    testImplementation(kotlin("test"))
}
