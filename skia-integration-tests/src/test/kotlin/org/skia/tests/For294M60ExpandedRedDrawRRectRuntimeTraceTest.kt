package org.skia.tests

import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkCpuWriteChronologyTrace
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.testing.TestUtils
import java.io.File

class For294M60ExpandedRedDrawRRectRuntimeTraceTest {

    @Test
    fun `FOR-294 exports M60 expanded red drawRRect runtime trace`() {
        val rawTargetsFile = configValue(TARGETS_FILE_PROPERTY, TARGETS_FILE_ENV).orEmpty()
        val rawOutput = configValue(OUTPUT_PROPERTY, OUTPUT_ENV).orEmpty()
        assumeTrue(
            rawTargetsFile.isNotBlank() && rawOutput.isNotBlank(),
            "FOR-294 expanded red drawRRect runtime trace export is driven by its validator script",
        )
        val targetPixels = parseTargetsFile(File(rawTargetsFile))
        val output = File(rawOutput)
        assertTrue(
            targetPixels.size > 59,
            "FOR-294 must trace an expanded target set beyond the FOR-290 outer-boundary pixels",
        )

        val gm = BlurredClippedCircleGM()
        val size = gm.size()
        SkCpuWriteChronologyTrace.configureForTargets(
            targetPixels,
            width = size.width,
            height = size.height,
            includeBitmapDirectWrites = true,
        )
        val bitmap = renderM60Rgba8888(gm)
        val events = SkCpuWriteChronologyTrace.snapshot()
        SkCpuWriteChronologyTrace.reset()

        val eventTargets = events.map { SkCpuWriteChronologyTrace.Target(it.x, it.y) }.toSet()
        assertTrue(
            eventTargets.containsAll(targetPixels),
            "every FOR-294 expanded target pixel must have at least one traced runtime write",
        )

        output.parentFile.mkdirs()
        output.writeText(toJson(targetPixels, events, bitmap))
    }

    private fun renderM60Rgba8888(gm: BlurredClippedCircleGM): SkBitmap {
        val size = gm.size()
        val bitmap = SkBitmap(
            size.width,
            size.height,
            TestUtils.DM_REFERENCE_COLOR_SPACE,
            SkColorType.kRGBA_8888,
        ).also { it.eraseColor(gm.bgColor()) }
        gm.draw(SkCanvas(bitmap))
        return bitmap
    }

    private fun parseTargetsFile(file: File): Set<SkCpuWriteChronologyTrace.Target> =
        file.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { item ->
                val parts = item.split(",")
                require(parts.size == 2) { "Invalid FOR-294 target coordinate: `$item`" }
                SkCpuWriteChronologyTrace.Target(parts[0].toInt(), parts[1].toInt())
            }
            .toSet()

    private fun toJson(
        targets: Set<SkCpuWriteChronologyTrace.Target>,
        events: List<SkCpuWriteChronologyTrace.Event>,
        bitmap: SkBitmap,
    ): String {
        val targetJson = targets
            .sortedWith(compareBy<SkCpuWriteChronologyTrace.Target> { it.y }.thenBy { it.x })
            .joinToString(",\n") { target ->
                val final = bitmap.getPixel(target.x, target.y)
                """
                  {
                    "x": ${target.x},
                    "y": ${target.y},
                    "finalReadbackRgba": ${rgba(final)}
                  }""".trimIndent()
            }
        val eventJson = events.joinToString(",\n") { event ->
            """
              {
                "index": ${event.index},
                "x": ${event.x},
                "y": ${event.y},
                "bitmapWidth": ${event.bitmapWidth},
                "bitmapHeight": ${event.bitmapHeight},
                "deviceKind": ${quote(event.deviceKind)},
                "rootDevice": ${event.rootDevice},
                "source": ${quote(event.source)},
                "callsite": ${quote(event.callsite)},
                "branch": ${quote(event.branch)},
                "mode": ${quote(event.mode)},
                "blender": ${quote(event.blender)},
                "coverage": ${event.coverage},
                "srcInputRgba": ${rgba(event.srcInput)},
                "srcAfterCoverageRgba": ${rgba(event.srcAfterCoverage)},
                "valueBeforeRgba": ${rgba(event.valueBefore)},
                "valueWrittenRgba": ${rgba(event.valueWritten)},
                "valueReadAfterRgba": ${rgba(event.valueReadAfter)},
                "maskLocalX": ${nullableInt(event.maskLocalX)},
                "maskLocalY": ${nullableInt(event.maskLocalY)},
                "maskOriginLeft": ${nullableInt(event.maskOriginLeft)},
                "maskOriginTop": ${nullableInt(event.maskOriginTop)},
                "maskWidth": ${nullableInt(event.maskWidth)},
                "maskHeight": ${nullableInt(event.maskHeight)},
                "compositeX0": ${nullableInt(event.compositeX0)},
                "compositeY0": ${nullableInt(event.compositeY0)},
                "compositeX1": ${nullableInt(event.compositeX1)},
                "compositeY1": ${nullableInt(event.compositeY1)},
                "blurredMaskAlpha": ${nullableInt(event.blurredMaskAlpha)},
                "maskedAlphaBeforeBlend": ${nullableInt(event.maskedAlphaBeforeBlend)},
                "a8SkipReason": ${nullableString(event.a8SkipReason)},
                "a8SpanLeft": ${nullableInt(event.a8SpanLeft)},
                "a8SpanRight": ${nullableInt(event.a8SpanRight)},
                "activeClipBounds": ${bounds(event.activeClipBounds)},
                "layerBounds": ${bounds(event.layerBounds)},
                "sourceLayerBounds": ${bounds(event.sourceLayerBounds)}
              }""".trimIndent()
        }
        return """
            {
              "linear": "FOR-294",
              "probe": "m60-expanded-red-drawrrect-runtime-trace-raw",
              "sceneId": "m60-bounded-nested-rrect-clip",
              "backend": "CPU/RGBA_8888/runtime",
              "targetPixelCount": ${targets.size},
              "eventCount": ${events.size},
              "targetPixels": [
            ${targetJson.prependIndent("    ")}
              ],
              "events": [
            ${eventJson.prependIndent("    ")}
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun rgba(color: SkColor): String =
        "[${SkColorGetR(color)}, ${SkColorGetG(color)}, ${SkColorGetB(color)}, ${SkColorGetA(color)}]"

    private fun nullableInt(value: Int?): String = value?.toString() ?: "null"

    private fun nullableString(value: String?): String =
        if (value == null) "null" else quote(value)

    private fun bounds(value: SkCpuWriteChronologyTrace.Bounds?): String =
        if (value == null) {
            "null"
        } else {
            """
              {
                "left": ${value.left},
                "top": ${value.top},
                "right": ${value.right},
                "bottom": ${value.bottom}
              }""".trimIndent()
        }

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

    private companion object {
        const val TARGETS_FILE_PROPERTY = "kanvas.for294.targetsFile"
        const val OUTPUT_PROPERTY = "kanvas.for294.output"
        const val TARGETS_FILE_ENV = "KANVAS_FOR294_TARGETS_FILE"
        const val OUTPUT_ENV = "KANVAS_FOR294_OUTPUT"

        fun configValue(property: String, env: String): String? =
            System.getProperty(property)?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    }
}
