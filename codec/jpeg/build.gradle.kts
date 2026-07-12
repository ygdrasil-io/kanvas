import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas"))
    implementation(project(":codec:core"))
    implementation(project(":codec:common"))

    testImplementation(project(":codec:test-fixtures"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    test {
        resources.srcDir("../real-image-tests/src/test/resources")
    }
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "kanvas.jpeg.oracle.djpeg",
        providers.gradleProperty("jpegOracleDjpeg").orNull.orEmpty(),
    )
}
