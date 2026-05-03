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
    sourceSets {
        val main by getting {
            kotlin.srcDir("src/tmp")
            kotlin.srcDir("src/fix")
            kotlin.srcDir("src/generated/foundation")
            kotlin.srcDir("src/generated/maths")
            kotlin.srcDir("src/generated/undefined")
            //kotlin.srcDir("src/generated/utils")
            //kotlin.srcDir("src/generated/gpu")
            //kotlin.srcDir("src/generated/core")
            //kotlin.srcDir("src/generated/tests")
            //kotlin.srcDir("src/generated/modules")
            kotlin.srcDir("src/ganesh")
        }
    }
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
