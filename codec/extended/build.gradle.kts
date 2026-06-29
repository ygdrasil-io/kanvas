plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas-skia"))
    implementation(project(":codec:core"))
    implementation(project(":codec:common"))
}
