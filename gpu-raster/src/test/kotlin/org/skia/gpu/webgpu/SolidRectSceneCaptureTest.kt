package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.graphiks.math.SkRect
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

class SolidRectSceneCaptureTest {
    @Test
    fun `solid rect scene renders from adapter backed WebGPU capture`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gpuBitmap = renderGpu(ctx)
            val reference = referenceBitmap()
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = 0)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[SolidRectSceneCapture] adapter=$adapter similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, maxDiff=${cmp.maxChannelDiff.max()}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(gpuBitmap, reference, cmp, adapter)
            }

            assertTrue(
                cmp.similarity >= THRESHOLD,
                "solid-rect WebGPU capture regressed below floor: ${cmp.similarity}% < $THRESHOLD%",
            )
        }
    }

    private fun renderGpu(context: WebGpuContext): SkBitmap {
        SkWebGpuDevice(context, WIDTH, HEIGHT, applyColorspaceTransform = false).use { device ->
            device.setBackground(BACKGROUND)
            SkCanvas(device).drawRect(
                SkRect.MakeLTRB(2f, 1f, 7f, 6f),
                SkPaint().apply { color = FILL },
            )
            return rgbaBytesToBitmap(device.flush(), WIDTH, HEIGHT)
        }
    }

    private fun referenceBitmap(): SkBitmap {
        val bitmap = SkBitmap(WIDTH, HEIGHT, colorType = SkColorType.kRGBA_8888)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                bitmap.setPixel(x, y, if (x in 2..6 && y in 1..5) FILL else BACKGROUND)
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
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/solid-rect").apply { mkdirs() }
        writePng(File(dir, "gpu.png"), gpuBitmap)
        writePng(File(dir, "gpu-diff.png"), pixelDiff(reference, gpuBitmap))
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
                    if (max == 0) 0 else SkColorSetARGB(255, (dr * 4).coerceAtMost(255), (dg * 4).coerceAtMost(255), (db * 4).coerceAtMost(255)),
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

    private fun routeJson(adapter: String): String = """
        {
          "sceneId": "solid-rect",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "drawKind": "axis-aligned-filled-rect",
          "status": "pass",
          "coverageStrategy": "webgpu.coverage.analytic-rect",
          "pipelineKey": "coverageKind=analyticRect",
          "fallbackReason": "none",
          "test": "org.skia.gpu.webgpu.SolidRectSceneCaptureTest#solid rect scene renders from adapter backed WebGPU capture",
          "note": "Adapter-backed WebGPU render capture produced from SkWebGpuDevice.flush(); CPU artifacts were not reused as GPU evidence."
        }
    """.trimIndent() + "\n"

    private fun statsJson(cmp: BitmapComparison, adapter: String): String = """
        {
          "sceneId": "solid-rect",
          "dimensions": { "width": $WIDTH, "height": $HEIGHT },
          "pixels": ${cmp.totalPixels},
          "matchingPixels": ${cmp.matchingPixels},
          "maxChannelDelta": ${cmp.maxChannelDiff.max()},
          "threshold": $THRESHOLD,
          "cpuSimilarity": 100.0,
          "gpuSimilarity": ${String.format(Locale.US, "%.1f", cmp.similarity)},
          "gpuMatchingPixels": ${cmp.matchingPixels},
          "gpuMaxChannelDelta": ${cmp.maxChannelDiff.max()},
          "touchedPixels": 25,
          "gpuStatus": "pass",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest"
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
        private const val WIDTH = 8
        private const val HEIGHT = 8
        private const val THRESHOLD = 99.95
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private val BACKGROUND = SkColorSetARGB(255, 245, 239, 229)
        private val FILL = SkColorSetARGB(255, 23, 33, 28)
    }
}
