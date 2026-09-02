plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":math:geometry"))
    api(project(":math:matrix"))
    api(project(":math:color"))
    api(project(":color-management"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
