plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math:color"))
    implementation(project(":kanvas"))
    implementation(project(":codec:core"))
}
