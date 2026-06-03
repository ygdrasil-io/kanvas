package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SimpleOffsetImageFilterGM
import java.io.File

/**
 * O6 cross-test : `SimpleOffsetImageFilterGM`
 * (`simple-offsetimagefilter`, 640x200) on the GPU backend.
 * Exercises `SkImageFilters.Offset` with assorted cropRect / clipRect
 * combos. FOR-242 ratchets the bounded WebGPU pre-pass above the old
 * diagnostic score without claiming strict Skia fidelity.
 */
class SimpleOffsetImageFilterWebGpuTest {
    @Test
    fun `SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(SimpleOffsetImageFilterGM(), floor = 98.43)
    }

    @Test
    fun `FOR-247 crop offset prepass scratch pixel matches source local probe`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val oldProbe = System.getProperty(FOR247_PROBE_PROPERTY)
        try {
            System.setProperty(FOR247_PROBE_PROPERTY, "true")
            context!!.use { ctx ->
                val bitmap = WebGpuSink.draw(ctx, SimpleOffsetImageFilterGM())
                val sourceLocal = rgbaAt(bitmap, x = 0, y = 0)
                val scratch = rgbaAt(bitmap, x = 1, y = 0)
                assertArrayEquals(
                    sourceLocal,
                    scratch,
                    "FOR-247: scratchPixel(45,5) must match sourceLocal(5,5) " +
                        "before the final Crop composite",
                )
                if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                    writeFor247ScratchProbeJson(sourceLocal, scratch)
                }
            }
        } finally {
            if (oldProbe == null) {
                System.clearProperty(FOR247_PROBE_PROPERTY)
            } else {
                System.setProperty(FOR247_PROBE_PROPERTY, oldProbe)
            }
        }
    }

    private fun rgbaAt(bitmap: org.skia.foundation.SkBitmap, x: Int, y: Int): IntArray {
        val pixel = bitmap.getPixel(x, y)
        return intArrayOf(
            (pixel ushr 16) and 0xFF,
            (pixel ushr 8) and 0xFF,
            pixel and 0xFF,
            (pixel ushr 24) and 0xFF,
        )
    }

    private fun writeFor247ScratchProbeJson(sourceLocal: IntArray, scratch: IntArray) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass",
        ).apply { mkdirs() }
        File(dir, "scratch-probe-for247.json").writeText(
            """
            {
              "backend": "WebGPU",
              "sceneId": "crop-image-filter-nonnull-prepass",
              "linear": "FOR-247",
              "probe": "crop-offset-pre-final-crop-scratch",
              "targetCell": "crop == clip == dst",
              "case": "Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))",
              "origin": [340, 120],
              "layerExtent": [80, 40],
              "offset": [40, 0],
              "sourceLocal": {
                "xy": [5, 5],
                "rgba": ${jsonArray(sourceLocal)}
              },
              "scratchPixel": {
                "xy": [45, 5],
                "rgba": ${jsonArray(scratch)}
              },
              "matchesSourceLocal": ${sourceLocal.contentEquals(scratch)},
              "observationMethod": "diagnostic WebGPU 1x1 kSrc copies before final Crop: sourceLocal(5,5) -> output(0,0), scratchPixel(45,5) -> output(1,0)",
              "selectedRoute": "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
              "fallbackReason": "none",
              "supportDecision": "KEEP_DIAGNOSTIC"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun jsonArray(values: IntArray): String = values.joinToString(
        prefix = "[",
        postfix = "]",
    )

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private companion object {
        const val FOR247_PROBE_PROPERTY: String = "kanvas.webgpu.for247.cropOffsetScratchProbe"
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
    }
}
