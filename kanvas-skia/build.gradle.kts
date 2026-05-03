plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("application")
    id("org.jetbrains.kotlinx.atomicfu") version "0.32.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    
    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

kotlin {
    jvmToolchain(25)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
