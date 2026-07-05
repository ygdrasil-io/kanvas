plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:core"))
    api(project(":font:text"))
    api(project(":font:gpu-api"))
    api(project(":math"))

    implementation(project(":font:colr"))
    implementation(project(":font:scaler"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
