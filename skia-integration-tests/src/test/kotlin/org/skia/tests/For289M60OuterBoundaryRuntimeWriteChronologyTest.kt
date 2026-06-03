package org.skia.tests

import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkCpuWriteChronologyTrace
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.testing.TestUtils
import java.io.File

class For289M60OuterBoundaryRuntimeWriteChronologyTest {

    @Test
    fun `FOR-289 exports M60 outer-boundary runtime write chronology`() {
        val rawTargets = configValue(TARGETS_PROPERTY, TARGETS_ENV).orEmpty()
        val rawOutput = configValue(OUTPUT_PROPERTY, OUTPUT_ENV).orEmpty()
        assumeTrue(
            rawTargets.isNotBlank() && rawOutput.isNotBlank(),
            "FOR-289 runtime chronology export is driven by its validator script",
        )
        val targetPixels = parseTargets(rawTargets)
        val output = File(rawOutput)
        assertEquals(59, targetPixels.size, "FOR-289 must trace exactly the 59 outer-boundary target pixels")

        val gm = BlurredClippedCircleGM()
        val size = gm.size()
        SkCpuWriteChronologyTrace.configureForTargets(targetPixels, width = size.width, height = size.height)
        val bitmap = renderM60Rgba8888(gm)
        val events = SkCpuWriteChronologyTrace.snapshot()
        SkCpuWriteChronologyTrace.reset()

        val eventTargets = events.map { SkCpuWriteChronologyTrace.Target(it.x, it.y) }.toSet()
        assertTrue(
            eventTargets.containsAll(targetPixels),
            "every FOR-289 target pixel must have at least one traced runtime write",
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

    private fun parseTargets(raw: String): Set<SkCpuWriteChronologyTrace.Target> {
        if (raw.isBlank()) return emptySet()
        return raw.split(";")
            .filter { it.isNotBlank() }
            .map { item ->
                val parts = item.split(",")
                require(parts.size == 2) { "Invalid FOR-289 target coordinate: `$item`" }
                SkCpuWriteChronologyTrace.Target(parts[0].toInt(), parts[1].toInt())
            }
            .toSet()
    }

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
                "valueReadAfterRgba": ${rgba(event.valueReadAfter)}
              }""".trimIndent()
        }
        return """
            {
              "linear": "FOR-289",
              "probe": "m60-outer-boundary-runtime-write-chronology-raw",
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
        const val TARGETS_PROPERTY = "kanvas.for289.targets"
        const val OUTPUT_PROPERTY = "kanvas.for289.output"
        const val TARGETS_ENV = "KANVAS_FOR289_TARGETS"
        const val OUTPUT_ENV = "KANVAS_FOR289_OUTPUT"

        fun configValue(property: String, env: String): String? =
            System.getProperty(property)?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    }
}
