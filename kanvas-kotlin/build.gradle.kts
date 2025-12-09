plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(25)
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
