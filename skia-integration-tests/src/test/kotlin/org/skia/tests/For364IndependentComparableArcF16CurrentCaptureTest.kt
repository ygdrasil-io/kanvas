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

class For364IndependentComparableArcF16CurrentCaptureTest {

    @Test
    fun `FOR-364 exports current Kanvas independent arc Rec2020 F16 samples`() {
        val rawOutput = configValue(OUTPUT_PROPERTY, OUTPUT_ENV).orEmpty()
        assumeTrue(
            rawOutput.isNotBlank(),
            "FOR-364 current Kanvas capture is driven by its validator script",
        )

        val output = repoFile(rawOutput)
        val bitmap = renderCurrentKanvas()
        output.parentFile.mkdirs()
        output.writeText(toJson(bitmap), Charsets.UTF_8)
    }

    private fun renderCurrentKanvas(): SkBitmap {
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val bitmap = SkBitmap(72, 72, rec2020, SkColorType.kRGBA_F16Norm)
        bitmap.eraseColor(SK_ColorWHITE)

        val paint = SkPaint(SkColorSetARGB(128, 255, 0, 0)).apply {
            isAntiAlias = false
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kButt_Cap
            blendMode = SkBlendMode.kSrcOver
        }
        SkCanvas(bitmap).drawArc(SkRect.MakeXYWH(10f, 10f, 44f, 44f), 180f, 100f, false, paint)
        return bitmap
    }

    private fun toJson(bitmap: SkBitmap): String {
        assertEquals(SkColorType.kRGBA_F16Norm, bitmap.colorType)
        val sampleJson = samples.joinToString(",\n") { sample ->
            val color = bitmap.getPixelAsSrgb(sample.x, sample.y)
            """
              {
                "name": ${quote(sample.name)},
                "zone": ${quote(sample.zone)},
                "x": ${sample.x},
                "y": ${sample.y},
                "currentKanvasSrgbRgba": ${rgba(color)}
              }""".trimIndent()
        }
        return """
            {
              "linear": "FOR-364",
              "sceneId": "f16-independent-comparable-arc-evidence-for364",
              "sourceType": "current-kanvas-independent-arc-rec2020-f16-for364-butt-cap",
              "dimensions": {"width": 72, "height": 72},
              "colorType": "kRGBA_F16Norm",
              "colorSpace": "Rec.2020",
              "blendMode": "kSrcOver",
              "arcScene": true,
              "independentFromFor361": true,
              "independentFromFor340For341AdjacentGroups": true,
              "excludedScene": "for361_and_circular_arcs_stroke_butt_adjacent_groups",
              "captureMethod": "Kanvas SkBitmap + SkCanvas public CPU drawArc path, sampled through SkBitmap.getPixelAsSrgb",
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

    private data class Sample(val name: String, val zone: String, val x: Int, val y: Int)

    private companion object {
        const val OUTPUT_PROPERTY = "kanvas.for364.currentCapture.output"
        const val OUTPUT_ENV = "KANVAS_FOR364_CURRENT_CAPTURE_OUTPUT"

        val samples = listOf(
            Sample("for364_background_top_left", "background", 0, 0),
            Sample("for364_arc_diagonal_stroke_center", "stroke-center", 16, 16),
            Sample("for364_arc_top_stroke_center", "stroke-center", 32, 10),
            Sample("for364_arc_interior_clear", "interior-clear", 32, 32),
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
