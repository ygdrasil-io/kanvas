package org.graphiks.kanvas.skia

import java.io.File
import java.util.Properties

object SimilarityTracker {
    private const val SCORES_FILE: String = "test-similarity-scores.properties"
    private val scores = SimilarityScoreStore(File(SCORES_FILE))

    public fun getPreviousScore(testName: String): Double? {
        return scores.get(testName)
    }

    public fun updateScore(testName: String, newScore: Double): Boolean {
        scores.record(testName, newScore)
        println("Measured similarity for $testName: %.2f%%".format(newScore))
        return true
    }
}

internal class SimilarityScoreStore(private val file: File) {
    private val properties = Properties()

    init {
        if (file.exists()) {
            file.inputStream().use { properties.load(it) }
        }
    }

    fun get(name: String): Double? = properties.getProperty(name)?.toDoubleOrNull()

    fun record(name: String, similarity: Double) {
        require(similarity.isFinite())
        properties.setProperty(name, similarity.toString())
        file.outputStream().use {
            properties.store(it, "integration-tests/skia GM similarity scores")
        }
    }
}
