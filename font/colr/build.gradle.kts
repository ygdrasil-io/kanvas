plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":font:core"))
    implementation(project(":font:sfnt"))
}
