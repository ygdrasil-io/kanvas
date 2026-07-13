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

val jpegPerformanceEvidenceReport = layout.buildDirectory.file("reports/jpeg-performance.json")

tasks.withType<Test>().configureEach {
    systemProperty(
        "kanvas.jpeg.oracle.djpeg",
        providers.gradleProperty("jpegOracleDjpeg").orNull.orEmpty(),
    )
    systemProperty(
        "kanvas.jpeg.oracle.hierarchy",
        providers.gradleProperty("jpegOracleHierarchy").orNull.orEmpty(),
    )
    systemProperty(
        "kanvas.jpeg.oracle.dir",
        providers.gradleProperty("jpegOracleDir").orNull.orEmpty(),
    )
    systemProperty(
        "kanvas.jpeg.performance.report",
        jpegPerformanceEvidenceReport.get().asFile.absolutePath,
    )
}

tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("org.graphiks.kanvas.codec.jpeg.JpegPerformanceEvidenceTest")
    }
}

/**
 * Produces release-facing JPEG measurements independently of the cacheable
 * correctness suite. The report is deleted before every execution, so a
 * successful task proves that this invocation produced fresh evidence.
 */
tasks.register<Test>("jpegPerformanceEvidence") {
    group = "verification"
    description = "Runs the non-threshold JPEG performance evidence suite and writes a fresh JSON report."
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("org.graphiks.kanvas.codec.jpeg.JpegPerformanceEvidenceTest")
    }
    outputs.file(jpegPerformanceEvidenceReport)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    doFirst {
        jpegPerformanceEvidenceReport.get().asFile.delete()
    }
}
