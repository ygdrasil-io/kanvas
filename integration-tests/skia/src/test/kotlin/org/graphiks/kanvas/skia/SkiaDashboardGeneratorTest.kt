package org.graphiks.kanvas.skia

import org.graphiks.kanvas.test.ComparisonUtils
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

    @Test
    fun `writes measured similarity instead of previous score`() {
        val refDir = File(tempDir, "reference").apply { mkdirs() }
        val genDir = File(tempDir, "generated/image").apply { mkdirs() }
        val scoresFile = File(tempDir, "scores.properties").apply {
            writeText("dashboard_score_probe=0.0\n")
        }
        val outputDir = File(tempDir, "dashboard")
        val reference = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(),
            0, 255.toByte(), 0, 255.toByte(),
        )
        val generated = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(),
            0, 0, 255.toByte(), 255.toByte(),
        )
        ComparisonUtils.saveRgbaAsPng(reference, 2, 1, refDir.resolve("dashboard_score_probe.png"))
        ComparisonUtils.saveRgbaAsPng(generated, 2, 1, genDir.resolve("dashboard_score_probe.png"))

        val args = arrayOf(
            "--ref-dir", refDir.absolutePath,
            "--gen-dir", File(tempDir, "generated").absolutePath,
            "--scores", scoresFile.absolutePath,
            "--output-dir", outputDir.absolutePath,
        )
        generateSkiaDashboard(args, gms = listOf(DashboardScoreProbeGm()))

        val json = outputDir.resolve("data/gms.json").readText()
        assertTrue(json.contains("\"similarity\": 50.0,"))
    }
}

private class DashboardScoreProbeGm : SkiaGm {
    override val name = "dashboard_score_probe"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2
    override val height = 1

    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
