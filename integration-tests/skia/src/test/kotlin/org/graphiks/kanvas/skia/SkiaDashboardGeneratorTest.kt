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
    fun `uses readable contrast for metrics below images`() {
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

        val html = outputDir.resolve("index.html").readText()
        assertTrue(html.contains("--secondary:#b8b8d4"))
        assertTrue(html.contains(".card-footer{display:flex;gap:1rem;margin-top:0.65rem;font-size:0.76rem;color:var(--secondary);"))
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

    @Test
    fun `writes secondary quality metrics for scored rows`() {
        val refDir = File(tempDir, "reference").apply { mkdirs() }
        val genDir = File(tempDir, "generated/image").apply { mkdirs() }
        val scoresFile = File(tempDir, "scores.properties")
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
        assertTrue(json.contains("\"pixelMatch\": 50.0,"))
        assertTrue(json.contains("\"ssim\": 1.0000,"))
        assertTrue(json.contains("\"meanChannelError\": 0.2500,"))
    }

    @Test
    fun `marks untrustable references as no score even when images exist`() {
        val refDir = File(tempDir, "reference").apply { mkdirs() }
        val genDir = File(tempDir, "generated/image").apply { mkdirs() }
        val scoresFile = File(tempDir, "scores.properties")
        val outputDir = File(tempDir, "dashboard")
        val reference = byteArrayOf(
            255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
        )
        val generated = byteArrayOf(
            255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            0, 255.toByte(), 0, 255.toByte(),
        )
        ComparisonUtils.saveRgbaAsPng(reference, 2, 1, refDir.resolve("dashboard_score_probe.png"))
        ComparisonUtils.saveRgbaAsPng(generated, 2, 1, genDir.resolve("dashboard_score_probe.png"))
        val args = arrayOf(
            "--ref-dir", refDir.absolutePath,
            "--gen-dir", File(tempDir, "generated").absolutePath,
            "--scores", scoresFile.absolutePath,
            "--output-dir", outputDir.absolutePath,
        )
        generateSkiaDashboard(args, gms = listOf(UntrustableDashboardScoreProbeGm()))

        val json = outputDir.resolve("data/gms.json").readText()
        assertTrue(json.contains("\"similarity\": null,"))
        assertTrue(json.contains("\"isPassing\": null,"))
        assertTrue(json.contains("\"referenceUntrustable\": true,"))
        assertTrue(json.contains("\"noScoreCause\": \"reference-untrustable\""))
    }

    @Test
    fun `omits blocking rows from dashboard data`() {
        val refDir = File(tempDir, "reference").apply { mkdirs() }
        val genDir = File(tempDir, "generated/image").apply { mkdirs() }
        val scoresFile = File(tempDir, "scores.properties")
        val outputDir = File(tempDir, "dashboard")
        val image = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(),
            0, 255.toByte(), 0, 255.toByte(),
        )
        ComparisonUtils.saveRgbaAsPng(image, 2, 1, refDir.resolve("dashboard_score_probe.png"))
        ComparisonUtils.saveRgbaAsPng(image, 2, 1, genDir.resolve("dashboard_score_probe.png"))
        ComparisonUtils.saveRgbaAsPng(image, 2, 1, refDir.resolve("blocking_dashboard_score_probe.png"))
        ComparisonUtils.saveRgbaAsPng(image, 2, 1, genDir.resolve("blocking_dashboard_score_probe.png"))

        val args = arrayOf(
            "--ref-dir", refDir.absolutePath,
            "--gen-dir", File(tempDir, "generated").absolutePath,
            "--scores", scoresFile.absolutePath,
            "--output-dir", outputDir.absolutePath,
        )
        generateSkiaDashboard(
            args,
            gms = listOf(DashboardScoreProbeGm(), BlockingDashboardScoreProbeGm()),
        )

        val json = outputDir.resolve("data/gms.json").readText()
        assertTrue(json.contains("\"total\": 1,"))
        assertTrue(json.contains("\"name\": \"dashboard_score_probe\""))
        assertTrue(!json.contains("blocking_dashboard_score_probe"))
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

private class UntrustableDashboardScoreProbeGm : SkiaGm {
    override val name = "dashboard_score_probe"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val referenceStatus = ReferenceStatusEntry(
        status = "untrustable",
        reason = "fixture is a white placeholder",
    )
    override val width = 2
    override val height = 1

    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}

private class BlockingDashboardScoreProbeGm : SkiaGm {
    override val name = "blocking_dashboard_score_probe"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 2
    override val height = 1

    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
