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

class For361BoundedIndependentArcF16CurrentCaptureTest {

    @Test
    fun `FOR-361 exports current Kanvas bounded independent arc Rec2020 F16 samples`() {
        val rawOutput = configValue(OUTPUT_PROPERTY, OUTPUT_ENV).orEmpty()
        assumeTrue(
            rawOutput.isNotBlank(),
            "FOR-361 current Kanvas capture is driven by its validator script",
        )

        val output = repoFile(rawOutput)
        val bitmap = renderCurrentKanvas()
        output.parentFile.mkdirs()
        output.writeText(toJson(bitmap), Charsets.UTF_8)
    }

    private fun renderCurrentKanvas(): SkBitmap {
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val bitmap = SkBitmap(64, 64, rec2020, SkColorType.kRGBA_F16Norm)
        bitmap.eraseColor(SK_ColorWHITE)

        val paint = SkPaint(SkColorSetARGB(100, 0, 0, 255)).apply {
            isAntiAlias = false
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 8f
            strokeCap = SkPaint.Cap.kRound_Cap
            blendMode = SkBlendMode.kSrcOver
        }
        SkCanvas(bitmap).drawArc(SkRect.MakeXYWH(12f, 12f, 40f, 40f), 0f, 120f, false, paint)
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
              "linear": "FOR-361",
              "sceneId": "f16-bounded-independent-arc-capture-for361",
              "sourceType": "current-kanvas-bounded-independent-arc-rec2020-f16-for361-round-cap",
              "dimensions": {"width": 64, "height": 64},
              "colorType": "kRGBA_F16Norm",
              "colorSpace": "Rec.2020",
              "blendMode": "kSrcOver",
              "arcScene": true,
              "independentFromFor340For341AdjacentGroups": true,
              "excludedScene": "circular_arcs_stroke_butt_adjacent_groups",
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
        const val OUTPUT_PROPERTY = "kanvas.for361.currentCapture.output"
        const val OUTPUT_ENV = "KANVAS_FOR361_CURRENT_CAPTURE_OUTPUT"

        val samples = listOf(
            Sample("for361_background_top_left", "background", 0, 0),
            Sample("for361_arc_right_stroke_center", "stroke-center", 52, 32),
            Sample("for361_arc_lower_right_stroke", "stroke-edge", 44, 46),
            Sample("for361_arc_interior_clear", "interior-clear", 32, 32),
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
