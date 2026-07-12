package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SimilarityTrackerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `record replaces a prior comparable similarity with the latest measurement`() {
        val store = SimilarityScoreStore(File(tempDir, "scores.properties"))

        store.record("blur2rects", 96.44)
        store.record("blur2rects", 96.14)

        assertEquals(96.14, store.get("blur2rects"))
    }

    @Test
    fun `record rejects non finite similarities without creating a score`() {
        val scoresFile = File(tempDir, "scores.properties")
        val store = SimilarityScoreStore(scoresFile)

        assertThrows(IllegalArgumentException::class.java) {
            store.record("blur2rects", Double.NaN)
        }

        assertFalse(scoresFile.exists())
        assertEquals(null, store.get("blur2rects"))
    }
}
