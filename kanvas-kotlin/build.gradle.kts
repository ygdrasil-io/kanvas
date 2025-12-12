plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("application")
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
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
    testImplementation(libs.bundles.kotest)
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

application {
    mainClass.set("testing.TestRunnerExampleKt")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
