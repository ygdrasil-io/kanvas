package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SkiaDashboardGeneratorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `creates dashboard data directory`() {
        val refDir = File(tempDir, "reference").apply { mkdirs() }
        val genDir = File(tempDir, "generated").apply { mkdirs() }
        val scoresFile = File(tempDir, "scores.properties")
        val outputDir = File(tempDir, "dashboard")

        val args = arrayOf(
            "--ref-dir", refDir.absolutePath,
            "--gen-dir", genDir.absolutePath,
            "--scores", scoresFile.absolutePath,
            "--output-dir", outputDir.absolutePath,
        )
        generateSkiaDashboard(args, gms = emptyList())

        assertTrue(outputDir.resolve("data/gms.json").isFile)
    }
}
