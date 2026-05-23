package org.skia.testing

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Tracks the best-ever similarity score per GM test. A new run is accepted if
 * it improves the previous score, or stays within a 1% drop tolerance.
 *
 * The properties file lives at the module root: `kanvas-skia/test-similarity-scores.properties`.
 * Tests run from that directory by default under Gradle.
 */
public object SimilarityTracker {

    private const val SCORES_FILE: String = "test-similarity-scores.properties"
    private const val MAX_DROP_PERCENT: Double = 1.0

    /**
     * When `true`, [updateScore] still validates against the existing
     * ratchet but the [SCORES_FILE] file is **not** mutated. Set via
     * `-Pkanvas.ratchet.writes.disabled=true` in `gradle.properties` (the
     * sprint default, see commit message of #671) or
     * `-Dkanvas.ratchet.writes.disabled=true` on the JVM. Lets 10+
     * parallel GM port agents coexist without conflicting on the shared
     * properties file. Re-enable by flipping the gradle.properties line
     * back to `false` (or removing it) before re-baselining.
     *
     * Loaded **once** at object init to avoid per-call sysprop reads
     * (and to make the gate state visible in test logs at the first
     * `updateScore` call).
     */
    private val ratchetWriteDisabled: Boolean =
        System.getProperty("kanvas.ratchet.writes.disabled", "false").toBoolean() ||
            System.getenv("KANVAS_RATCHET_WRITES_DISABLED") == "true"

    private val scores: Properties = Properties()
    private var loaded: Boolean = false

    public fun getPreviousScore(testName: String): Double? {
        ensureLoaded()
        return scores.getProperty(testName)?.toDoubleOrNull()
    }

    /**
     * Record a new score for the test. Returns true if the test passes
     * (improved score, or drop within tolerance), false on a significant drop.
     *
     * Also pushes the score into [TestReport] so the run-level markdown
     * summary stays in sync — even for tests that don't opt into the
     * detailed comparison pipeline.
     */
    public fun updateScore(testName: String, newScore: Double): Boolean {
        ensureLoaded()
        val previous = getPreviousScore(testName)

        TestReport.recordScore(testName, newScore, previous)

        if (previous == null || newScore > previous) {
            scores.setProperty(testName, newScore.toString())
            saveScores()
            if (previous == null) {
                println("New similarity score recorded for $testName: %.2f%%".format(newScore))
            } else {
                println(
                    "Improved similarity for $testName: %.2f%% -> %.2f%% (+%.2f%%)"
                        .format(previous, newScore, newScore - previous)
                )
            }
            return true
        }

        val drop = previous - newScore
        return if (drop > MAX_DROP_PERCENT) {
            println("Significant similarity drop for $testName: -%.2f%% (was %.2f%%, now %.2f%%)"
                .format(drop, previous, newScore))
            false
        } else {
            println("Minor fluctuation for $testName: -%.2f%% (within tolerance)".format(drop))
            true
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        val file = File(SCORES_FILE)
        if (file.exists()) {
            FileInputStream(file).use { scores.load(it) }
        }
        loaded = true
    }

    private fun saveScores() {
        if (ratchetWriteDisabled) {
            // Sprint mode : validate-only. The new score has been pushed
            // into the in-memory map + TestReport, but the on-disk
            // ratchet file is left untouched so parallel CI / agent PRs
            // don't conflict on it. See `kanvas.ratchet.writes.disabled`
            // in `gradle.properties`.
            return
        }
        FileOutputStream(SCORES_FILE).use {
            scores.store(it, "kanvas-skia GM test similarity scores")
        }
    }
}
