package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ClippedCubicGmTest {
    @Test
    fun `clipped cubic GM renders through the bounded path route`() {
        GpuAvailability.requireWebGpu()

        val gm = ClippedCubicGm()
        val result = SkiaGmRenderer.render(gm)
        val reference = ReferenceManager.loadReference("/reference/${gm.referenceName}.png")
        val comparison = ComparisonUtils.compareRgba(
            actual = result.rgba,
            reference = reference,
            width = result.width,
            height = result.height,
            tolerance = gm.tolerance,
            minSimilarity = gm.minSimilarity,
        )

        assertEquals(0, result.refusedCount, result.diagnostics.joinToString())
        assertTrue(result.dispatchedCount > 0)
        assertTrue(comparison.similarity >= gm.minSimilarity)
        if (System.getProperty("kanvas.gm.writeEvidence") == "true") {
            writeEvidenceArtifacts(gm, result, reference, comparison)
        }
        println(
            "task116.gm-evidence gm=${gm.name} route=bounded-cubic-path " +
                "dispatch=${result.dispatchedCount} refuse=${result.refusedCount} " +
                "similarity=${comparison.similarity} " +
                "meanError=${comparison.meanChannelError}",
        )
    }

    private fun writeEvidenceArtifacts(
        gm: ClippedCubicGm,
        result: org.graphiks.kanvas.skia.SkiaRenderResult,
        reference: ByteArray,
        comparison: ComparisonUtils.ComparisonResult,
    ) {
        val directory = evidenceDirectory()
        ComparisonUtils.saveRgbaAsPng(result.rgba, result.width, result.height, File(directory, "gpu.png"))
        ComparisonUtils.saveRgbaAsPng(reference, result.width, result.height, File(directory, "reference.png"))
        ComparisonUtils.saveRgbaAsPng(
            comparison.diffRgba ?: ByteArray(result.rgba.size),
            result.width,
            result.height,
            File(directory, "diff.png"),
        )
        File(directory, "stats.json").writeText(
            """
            {
              "gm": "${gm.name}",
              "route": "bounded-cubic-path",
              "width": ${result.width},
              "height": ${result.height},
              "dispatchCount": ${result.dispatchedCount},
              "refusedCount": ${result.refusedCount},
              "tolerance": ${gm.tolerance},
              "minimumSimilarityPercent": ${gm.minSimilarity},
              "similarityPercent": ${comparison.similarity},
              "pixelMatchPercent": ${comparison.pixelMatch},
              "ssim": ${comparison.ssim},
              "matchingPixels": ${comparison.matchingPixels},
              "totalPixels": ${comparison.totalPixels},
              "meanChannelError": ${comparison.meanChannelError},
              "maxDiff": ${comparison.maxDiff.contentToString()},
              "meanDiff": ${comparison.meanDiff.contentToString()},
              "pass": ${comparison.isPassing}
            }
            """.trimIndent() + "\n",
        )
    }

    private fun evidenceDirectory(): File {
        var repositoryRoot = File(System.getProperty("user.dir")).absoluteFile
        while (!File(repositoryRoot, "settings.gradle.kts").isFile && repositoryRoot.parentFile != null) {
            repositoryRoot = repositoryRoot.parentFile
        }
        return File(repositoryRoot, "reports/gpu-renderer/evidence/clipped-cubic-gm-2026-08-29")
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
