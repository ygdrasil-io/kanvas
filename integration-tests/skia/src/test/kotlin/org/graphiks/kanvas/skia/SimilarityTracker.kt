package org.graphiks.kanvas.skia

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object SimilarityTracker {
    private const val SCORES_FILE: String = "test-similarity-scores.properties"
    private const val MAX_DROP_PERCENT: Double = 1.0

    private val scores: Properties = Properties()
    private var loaded: Boolean = false

    public fun getPreviousScore(testName: String): Double? {
        ensureLoaded()
        return scores.getProperty(testName)?.toDoubleOrNull()
    }

    public fun updateScore(testName: String, newScore: Double): Boolean {
        ensureLoaded()
        val previous = getPreviousScore(testName)

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
        FileOutputStream(SCORES_FILE).use {
            scores.store(it, "integration-tests/skia GM similarity scores")
        }
    }
}
