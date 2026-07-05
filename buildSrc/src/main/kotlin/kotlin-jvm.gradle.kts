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
    // OutOfMemoryError under that default. 8 GB clears the large backdrop
    // / shadow GM cases exercised today. The value can still be overridden
    // per run with `-Pkanvas.test.maxHeapSize=...` when bisecting memory.
    maxHeapSize = findProperty("kanvas.test.maxHeapSize")?.toString() ?: "8g"

    // Forward the ratchet-write gate from `gradle.properties` to the test
    // worker JVMs. When `true`, scores validate against the existing ratchet
    // but the on-disk `test-similarity-scores.properties` is not mutated.
    // Used during the STUB/PARTIAL porting sprint so 10+ parallel agent
    // PRs don't conflict on the shared properties file. See the property
    // definition in `gradle.properties` for re-enable instructions.
    findProperty("kanvas.ratchet.writes.disabled")?.let {
        systemProperty("kanvas.ratchet.writes.disabled", it.toString())
    }

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
