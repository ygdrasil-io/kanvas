import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas"))
    implementation(project(":codec:core"))

    testImplementation(project(":codec:test-fixtures"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

val jpeg2000OracleOpenJpeg = providers.gradleProperty("jpeg2000OracleOpenJpeg").orNull

tasks.withType<Test>().configureEach {
    if (jpeg2000OracleOpenJpeg != null) {
        systemProperty("kanvas.jpeg2000.oracle.openjpeg", jpeg2000OracleOpenJpeg)
    }
}
