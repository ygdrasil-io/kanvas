// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // J7 — Test workers fork their own JVM; Gradle's default cap is 512 MB,
    // independent of `org.gradle.jvmargs` (which only sizes the build
    // daemon). Several GMs (ManyCirclesGM, HitTestPathGM, SimpleRectGM, …)
    // allocate enough bitmap surfaces during a full :test run to trip
    // OutOfMemoryError under that default. 4 GB clears every GM we
    // exercise today with comfortable headroom and stays well under the
    // 6 GB daemon ceiling defined in `gradle.properties`, so a single
    // worker JVM cannot starve the rest of the build.
    maxHeapSize = "4g"

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
