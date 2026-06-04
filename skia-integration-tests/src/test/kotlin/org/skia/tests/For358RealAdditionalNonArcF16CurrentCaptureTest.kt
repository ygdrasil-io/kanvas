package org.skia.tests

import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.io.File

class For358RealAdditionalNonArcF16CurrentCaptureTest {

    @Test
    fun `FOR-358 exports current Kanvas additional non-arc Rec2020 F16 samples`() {
        val rawOutput = configValue(OUTPUT_PROPERTY, OUTPUT_ENV).orEmpty()
        assumeTrue(
            rawOutput.isNotBlank(),
            "FOR-358 current Kanvas capture is driven by its validator script",
        )

        val output = repoFile(rawOutput)
        val bitmap = renderCurrentKanvas()
        output.parentFile.mkdirs()
        output.writeText(toJson(bitmap), Charsets.UTF_8)
    }

    private fun renderCurrentKanvas(): SkBitmap {
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val bitmap = SkBitmap(40, 28, rec2020, SkColorType.kRGBA_F16Norm)
        bitmap.eraseColor(SK_ColorWHITE)

        val paint = SkPaint(SkColorSetARGB(160, 0, 192, 64)).apply {
            isAntiAlias = false
            style = SkPaint.Style.kFill_Style
            blendMode = SkBlendMode.kSrcOver
        }
        SkCanvas(bitmap).drawRect(SkRect.MakeXYWH(5f, 6f, 18f, 15f), paint)
        return bitmap
    }

    private fun toJson(bitmap: SkBitmap): String {
        assertEquals(SkColorType.kRGBA_F16Norm, bitmap.colorType)
        val sampleJson = samples.joinToString(",\n") { sample ->
            val color = bitmap.getPixelAsSrgb(sample.x, sample.y)
            """
              {
                "name": ${quote(sample.name)},
                "x": ${sample.x},
                "y": ${sample.y},
                "currentKanvasSrgbRgba": ${rgba(color)}
              }""".trimIndent()
        }
        return """
            {
              "linear": "FOR-358",
              "sceneId": "f16-real-additional-non-arc-row-for358",
              "sourceType": "current-kanvas-non-arc-rec2020-f16-for358-src-over-rect",
              "dimensions": {"width": 40, "height": 28},
              "colorType": "kRGBA_F16Norm",
              "colorSpace": "Rec.2020",
              "blendMode": "kSrcOver",
              "nonArc": true,
              "excludedScene": "circular_arcs_stroke_butt",
              "captureMethod": "Kanvas SkBitmap + SkCanvas public CPU path, sampled through SkBitmap.getPixelAsSrgb",
              "samples": [
            ${sampleJson.prependIndent("    ")}
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun rgba(color: Int): String =
        "[${SkColorGetR(color)}, ${SkColorGetG(color)}, ${SkColorGetB(color)}, ${SkColorGetA(color)}]"

    private fun quote(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
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

    private data class Sample(val name: String, val x: Int, val y: Int)

    private companion object {
        const val OUTPUT_PROPERTY = "kanvas.for358.currentCapture.output"
        const val OUTPUT_ENV = "KANVAS_FOR358_CURRENT_CAPTURE_OUTPUT"

        val samples = listOf(
            Sample("for358_background_top_left", 0, 0),
            Sample("for358_rect_center", 14, 14),
            Sample("for358_rect_left_inside", 5, 14),
            Sample("for358_rect_bottom_inside", 14, 20),
        )

        fun configValue(property: String, env: String): String? =
            System.getProperty(property)?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.takeIf { it.isNotBlank() }

        fun repoFile(path: String): File {
            val file = File(path)
            if (file.isAbsolute) return file
            return File(File("").absoluteFile.parentFile, path)
        }
    }
}
