plugins {
    kotlin("jvm")
    id("application")
    id("io.kotest") version "6.0.3"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

    // Kotest - Modern Kotlin testing framework
    testImplementation("io.kotest:kotest-framework-engine:6.0.3")
    testImplementation("io.kotest:kotest-assertions-core:6.0.3")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.3")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("testing.TestRunnerExampleKt")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
