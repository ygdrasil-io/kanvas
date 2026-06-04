package org.skia.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkAAClipDifferenceTrace
import org.skia.core.SkCanvas
import org.skia.foundation.SkAAClip
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.testing.TestUtils
import java.io.File

class For301M60SkAAClipBandTraceTest {

    @Test
    fun `FOR-301 exports M60 SkAAClip band trace`() {
        val rawProbesFile = configValue(PROBES_FILE_PROPERTY, PROBES_FILE_ENV).orEmpty()
        val rawOutput = configValue(OUTPUT_PROPERTY, OUTPUT_ENV).orEmpty()
        assumeTrue(
            rawProbesFile.isNotBlank() && rawOutput.isNotBlank(),
            "FOR-301 SkAAClip band trace export is driven by its validator script",
        )
        val groupedProbePoints = parseProbePoints(File(rawProbesFile))
        val probePoints = groupedProbePoints.values.flatten()
            .map { SkAAClip.DebugProbePoint(it.x, it.y) }
            .toSet()
        val probeRows = probePoints.flatMap { point -> listOf(point.y - 1, point.y, point.y + 1) }.toSet()
        val output = File(rawOutput)

        assertTrue(groupedProbePoints.getValue("original-59-targets").size == 59)
        assertTrue(groupedProbePoints.containsKey("candidate-minus-runtime-002"))
        assertTrue(groupedProbePoints.keys.any { it.startsWith("red-runtime-") })

        val gm = BlurredClippedCircleGM()
        val size = gm.size()
        SkAAClipDifferenceTrace.configure(probeRows = probeRows, probePoints = probePoints)
        renderM60Rgba8888(gm)
        val events = SkAAClipDifferenceTrace.snapshot()
        SkAAClipDifferenceTrace.reset()

        assertEquals(1, events.size, "M60 should emit exactly one clipRRect(kDifference) trace event")

        output.parentFile.mkdirs()
        output.writeText(toJson(size.width, size.height, groupedProbePoints, events), Charsets.UTF_8)
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

    private fun parseProbePoints(file: File): Map<String, List<ProbePoint>> {
        val out = linkedMapOf<String, MutableList<ProbePoint>>()
        file.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { item ->
                val parts = item.split(",")
                require(parts.size == 3) { "Invalid FOR-301 probe row: `$item`" }
                out.getOrPut(parts[0]) { mutableListOf() } += ProbePoint(parts[1].toInt(), parts[2].toInt())
            }
        return out
    }

    private fun toJson(
        width: Int,
        height: Int,
        groupedProbePoints: Map<String, List<ProbePoint>>,
        events: List<SkAAClipDifferenceTrace.Event>,
    ): String {
        val groupJson = groupedProbePoints.entries.joinToString(",\n") { (id, points) ->
            val pointsJson = points.joinToString(",\n") { point ->
                """{"x": ${point.x}, "y": ${point.y}}"""
            }
            """
              ${quote(id)}: [
            ${pointsJson.prependIndent("    ")}
              ]""".trimIndent()
        }
        val eventJson = events.joinToString(",\n") { event -> eventJson(event) }
        return """
            {
              "linear": "FOR-301",
              "probe": "m60-skaaclip-band-trace-raw",
              "sceneId": "m60-bounded-nested-rrect-clip",
              "backend": "CPU/RGBA_8888/runtime-skaaclip-trace",
              "imageSize": {"width": $width, "height": $height},
              "probeGroups": {
            ${groupJson.prependIndent("    ")}
              },
              "eventCount": ${events.size},
              "events": [
            ${eventJson.prependIndent("    ")}
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun eventJson(event: SkAAClipDifferenceTrace.Event): String =
        """
          {
            "index": ${event.index},
            "op": ${quote(event.op)},
            "doAntiAlias": ${event.doAntiAlias},
            "stateClipBounds": ${bounds(event.stateClipBounds)},
            "matrix": ${matrix(event.matrix)},
            "parent": ${snapshot(event.parent)},
            "path": ${snapshot(event.path)},
            "result": ${snapshot(event.result)}
          }""".trimIndent()

    private fun snapshot(snapshot: SkAAClip.DebugSnapshot): String {
        val bandsJson = snapshot.bands.joinToString(",\n") { band ->
            """
              {
                "index": ${band.index},
                "top": ${band.top},
                "bottom": ${band.bottom},
                "runs": ${runs(band.runs)}
              }""".trimIndent()
        }
        val lineProbesJson = snapshot.lineProbes.joinToString(",\n") { probe ->
            """
              {
                "y": ${probe.y},
                "bandIndex": ${nullableInt(probe.bandIndex)},
                "bandTop": ${nullableInt(probe.bandTop)},
                "bandBottom": ${nullableInt(probe.bandBottom)},
                "runs": ${runs(probe.runs)}
              }""".trimIndent()
        }
        val coverageProbeJson = snapshot.coverageProbes.joinToString(",\n") { probe ->
            """{"x": ${probe.x}, "y": ${probe.y}, "coverage": ${probe.coverage}}"""
        }
        return """
          {
            "bounds": ${bounds(snapshot.bounds)},
            "isEmpty": ${snapshot.isEmpty},
            "isRect": ${snapshot.isRect},
            "rowCount": ${snapshot.rowCount},
            "runCount": ${snapshot.runCount},
            "bands": [
        ${bandsJson.prependIndent("    ")}
            ],
            "lineProbes": [
        ${lineProbesJson.prependIndent("    ")}
            ],
            "coverageProbes": [
        ${coverageProbeJson.prependIndent("    ")}
            ]
          }""".trimIndent()
    }

    private fun runs(runs: List<SkAAClip.DebugRun>): String =
        if (runs.isEmpty()) {
            "[]"
        } else {
            runs.joinToString(",\n", prefix = "[\n", postfix = "\n]") { run ->
                """
                  {
                    "index": ${run.index},
                    "left": ${run.left},
                    "right": ${run.right},
                    "width": ${run.width},
                    "alpha": ${run.alpha}
                  }""".trimIndent()
            }
        }

    private fun bounds(value: org.graphiks.math.SkIRect): String =
        """{"left": ${value.left}, "top": ${value.top}, "right": ${value.right}, "bottom": ${value.bottom}}"""

    private fun matrix(value: SkAAClipDifferenceTrace.MatrixSnapshot): String =
        """{"sx": ${value.sx}, "kx": ${value.kx}, "tx": ${value.tx}, "ky": ${value.ky}, "sy": ${value.sy}, "ty": ${value.ty}, "persp0": ${value.persp0}, "persp1": ${value.persp1}, "persp2": ${value.persp2}}"""

    private fun nullableInt(value: Int?): String = value?.toString() ?: "null"

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

    private data class ProbePoint(val x: Int, val y: Int)

    private companion object {
        const val PROBES_FILE_PROPERTY = "kanvas.for301.probesFile"
        const val OUTPUT_PROPERTY = "kanvas.for301.output"
        const val PROBES_FILE_ENV = "KANVAS_FOR301_PROBES_FILE"
        const val OUTPUT_ENV = "KANVAS_FOR301_OUTPUT"

        fun configValue(property: String, env: String): String? =
            System.getProperty(property)?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    }
}
