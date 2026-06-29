plugins {
    id("buildsrc.convention.kotlin-jvm")
}

import org.gradle.api.tasks.testing.Test

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":codec"))
    implementation(project(":codec:core"))
    implementation(project(":kanvas-skia"))
    implementation(project(":math"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "kanvas.codec.realImageTestRuntimeClasspath",
        sourceSets.test.get().runtimeClasspath.asPath,
    )
}
