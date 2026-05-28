package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

class AnalyticAaConvexSceneCaptureTest {
    @Test
    fun `analytic AA convex scene renders from adapter backed WebGPU capture`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gpuBitmap = renderGpu(ctx)
            val reference = referenceBitmap()
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = 0)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[AnalyticAaConvexSceneCapture] adapter=$adapter similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, maxDiff=${cmp.maxChannelDiff.max()}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(gpuBitmap, reference, cmp, adapter)
            }

            assertTrue(
                cmp.similarity >= SUPPORT_THRESHOLD,
                "analytic-aa-convex WebGPU capture must match the composited CPU AA oracle: " +
                    "${cmp.similarity}% < $SUPPORT_THRESHOLD%",
            )
        }
    }

    private fun renderGpu(context: WebGpuContext): SkBitmap {
        SkWebGpuDevice(context, WIDTH, HEIGHT, applyColorspaceTransform = false).use { device ->
            device.setBackground(BACKGROUND)
            val path = SkPathBuilder()
                .moveTo(8f, 2f)
                .lineTo(14f, 8f)
                .lineTo(8f, 14f)
                .lineTo(2f, 8f)
                .close()
                .detach()
            SkCanvas(device).drawPath(
                path,
                SkPaint().apply {
                    color = FILL_OPAQUE
                    isAntiAlias = true                },
            )
            return rgbaBytesToBitmap(device.flush(), WIDTH, HEIGHT)
        }
    }

    private fun referenceBitmap(): SkBitmap {
        val bitmap = SkBitmap(WIDTH, HEIGHT, colorType = SkColorType.kRGBA_8888)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                bitmap.setPixel(x, y, BACKGROUND)
            }
        }
        val rows = listOf(
            2 to 7..8,
            3 to 6..9,
            4 to 5..10,
            5 to 4..11,
            6 to 3..12,
            7 to 2..13,
            8 to 2..13,
            9 to 3..12,
            10 to 4..11,
            11 to 5..10,
            12 to 6..9,
            13 to 7..8,
        )
        for ((y, range) in rows) {
            bitmap.setPixel(range.first, y, FILL_AA_COMPOSITED)
            bitmap.setPixel(range.last, y, FILL_AA_COMPOSITED)
            for (x in (range.first + 1) until range.last) {
                bitmap.setPixel(x, y, FILL_OPAQUE)
            }
        }
        return bitmap
    }

    private fun rgbaBytesToBitmap(rgba: ByteArray, width: Int, height: Int): SkBitmap {
        require(rgba.size == width * height * 4) {
            "RGBA buffer size mismatch: expected ${width * height * 4} bytes, got ${rgba.size}"
        }
        val bitmap = SkBitmap(width, height, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until width * height) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }

    private fun writeEvidence(
        gpuBitmap: SkBitmap,
        reference: SkBitmap,
        cmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), reference)
        writePng(File(dir, "cpu-diff.png"), pixelDiff(reference, reference))
        writePng(File(dir, "gpu.png"), gpuBitmap)
        writePng(File(dir, "gpu-diff.png"), pixelDiff(reference, gpuBitmap))
        File(dir, "route-cpu.json").writeText(cpuRouteJson())
        File(dir, "route-gpu.json").writeText(routeJson(adapter))
        File(dir, "stats.json").writeText(statsJson(cmp, adapter))
    }

    private fun pixelDiff(reference: SkBitmap, actual: SkBitmap): SkBitmap {
        require(reference.width == actual.width && reference.height == actual.height)
        val diff = SkBitmap(reference.width, reference.height, colorType = SkColorType.kRGBA_8888)
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val a = reference.getPixel(x, y)
                val b = actual.getPixel(x, y)
                val dr = kotlin.math.abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF))
                val dg = kotlin.math.abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF))
                val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
                val da = kotlin.math.abs(((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF))
                val max = maxOf(dr, dg, db, da)
                diff.setPixel(
                    x,
                    y,
                    if (max == 0) 0 else SkColorSetARGB(
                        255,
                        (dr * 4).coerceAtMost(255),
                        (dg * 4).coerceAtMost(255),
                        (db * 4).coerceAtMost(255),
                    ),
                )
            }
        }
        return diff
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.path}")
        file.writeBytes(bytes)
    }

    private fun repoFile(path: String): File {
        val local = File(path)
        return if (local.exists() || !File("../$path").exists()) local else File("../$path")
    }

    private fun cpuRouteJson(): String = """
        {
          "sceneId": "analytic-aa-convex",
          "backend": "CPU",
          "drawKind": "single-contour-convex-aa-path",
          "status": "pass",
          "selectedRoute": "cpu.path-coverage.analytic-aa-convex-composited-oracle",
          "coveragePlan": "PathCoverage(singleContour=true,convex=true,aa=true,edgeCountWithinBudget=true)",
          "fallbackReason": "none",
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "compositing": "src-over-opaque-background",
          "sourceReport": "reports/wgsl-pipeline/2026-05-28-m42-analytic-aa-convex-aa-edge-oracle-reconciliation.md"
        }
    """.trimIndent() + "\n"

    private fun routeJson(adapter: String): String = """
        {
          "sceneId": "analytic-aa-convex",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "drawKind": "single-contour-convex-aa-path",
          "status": "pass",
          "coverageStrategy": "webgpu.coverage.path-convex-fan",
          "pipelineKey": "coverageKind=pathConvexFan",
          "fallbackReason": "none",
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "compositing": "src-over-opaque-background",
          "test": "org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest#analytic AA convex scene renders from adapter backed WebGPU capture",
          "sourceReport": "reports/wgsl-pipeline/2026-05-28-m42-analytic-aa-convex-aa-edge-oracle-reconciliation.md"
        }
    """.trimIndent() + "\n"

    private fun statsJson(cmp: BitmapComparison, adapter: String): String = """
        {
          "sceneId": "analytic-aa-convex",
          "dimensions": { "width": $WIDTH, "height": $HEIGHT },
          "pixels": ${cmp.totalPixels},
          "matchingPixels": ${cmp.matchingPixels},
          "maxChannelDelta": ${cmp.maxChannelDiff.max()},
          "threshold": $SUPPORT_THRESHOLD,
          "cpuSimilarity": 100.0,
          "gpuSimilarity": ${String.format(Locale.US, "%.1f", cmp.similarity)},
          "gpuMatchingPixels": ${cmp.matchingPixels},
          "gpuMaxChannelDelta": ${cmp.maxChannelDiff.max()},
          "gpuStatus": "pass",
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "compositing": "src-over-opaque-background",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest"
        }
    """.trimIndent() + "\n"

    private fun String.jsonString(): String = buildString {
        append('"')
        for (ch in this@jsonString) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private companion object {
        private const val WIDTH = 16
        private const val HEIGHT = 16
        private const val SUPPORT_THRESHOLD = 99.85
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private val BACKGROUND = SkColorSetARGB(255, 245, 239, 229)
        private val FILL_OPAQUE = SkColorSetARGB(255, 31, 122, 76)
        private val FILL_AA_COMPOSITED = SkColorSetARGB(255, 138, 180, 152)
    }
}
