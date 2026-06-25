plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":gpu-renderer"))
    api(project(":font:gpu-api"))
    implementation(kotlin("stdlib"))
    implementation(project(":codec-api"))
    implementation(project(":font"))
    testImplementation(kotlin("test"))
}
