plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
}
