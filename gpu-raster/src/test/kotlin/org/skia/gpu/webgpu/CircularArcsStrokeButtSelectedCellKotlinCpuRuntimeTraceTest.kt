package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkCpuWriteChronologyTrace
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.testing.TestUtils
import org.skia.tests.GM

class CircularArcsStrokeButtSelectedCellKotlinCpuRuntimeTraceTest {
    @Test
    fun `FOR 333 writes selected cell Kotlin CPU runtime trace when opted in`() {
        assumeTrue(System.getProperty(WRITE_TRACE_PROPERTY) == "true")

        SkCpuWriteChronologyTrace.configureForTargets(
            targets = SAMPLES.map { SkCpuWriteChronologyTrace.Target(it.x, it.y) }.toSet(),
            width = WIDTH,
            height = HEIGHT,
            includeBitmapDirectWrites = true,
        )
        val events: List<SkCpuWriteChronologyTrace.Event>
        try {
            val bitmap = TestUtils.runGmTest(SelectedCellGM())
            events = SkCpuWriteChronologyTrace.snapshot()
            val pngBytes = SkPngEncoder.Encode(bitmap)
                ?: throw IllegalStateException("FOR-333 trace could not encode selected-cell CPU bitmap")
            val artifact = buildTraceJson(bitmap, events, pngBytes.size)
            repoFile(ARTIFACT_DIR).apply { mkdirs() }
            repoFile("$ARTIFACT_DIR/$SCENE_ID.json").writeText(artifact)
        } finally {
            SkCpuWriteChronologyTrace.reset()
        }

        assertTrue(events.any { it.srcPremulAfterCoverageF16 != null })
    }

    private fun buildTraceJson(
        bitmap: SkBitmap,
        events: List<SkCpuWriteChronologyTrace.Event>,
        pngByteSize: Int,
    ): String {
        val byTarget = events.groupBy { it.x to it.y }
        val sampleJson = SAMPLES.joinToString(",\n") { sample ->
            sampleJson(sample, bitmap, byTarget[sample.x to sample.y].orEmpty())
        }
        val eventJson = events.joinToString(",\n") { eventJson(it) }
        return """
        {
          "schemaVersion": 1,
          "linear": "FOR-333",
          "sceneId": "$SCENE_ID",
          "sourceMemory": "$SOURCE_MEMORY",
          "sourceFinding": "$SOURCE_FINDING",
          "decision": "$DECISION_BOUNDARY_IDENTIFIED",
          "decisionReason": "selected stroke samples capture source premul, coverage, F16 store, getPixel readback, and PNG RGBA-row-equivalent values; the first exported divergent boundary is F16 readback into untagged PNG RGBA, not arc coverage",
          "conclusion": "boundary-identified",
          "identifiedFirstDivergentBoundary": "f16-readback-and-png-encode",
          "correctionTargetable": true,
          "recommendedCorrectionTicket": "Target SkBitmap.getPixel / SkPngEncoder export color-space handling for F16 Rec.2020 CPU evidence; do not change arc coverage or stroke geometry until that boundary is fixed and revalidated.",
          "inputValidation": {
            "valid": true,
            "invalidReasons": [],
            "requiresFor332Decision": "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION"
          },
          "selectedCellOnly": true,
          "optIn": {
            "enabledBySystemProperty": "$WRITE_TRACE_PROPERTY",
            "defaultActive": false,
            "command": "$COMMAND"
          },
          "nonGoals": {
            "cpuRendererFixed": false,
            "gpuChanged": false,
            "wgslChanged": false,
            "thresholdChanged": false,
            "fallbackPolicyChanged": false,
            "kadreChanged": false,
            "scenePromotionChanged": false,
            "fidelityScoreCounted": false
          },
          "for331MetricsPreserved": {
            "differentPixels": 2031,
            "cellSimilarityPercent": 68.265625,
            "differentPixelsOutsideExpectedStrokeBoundingBox": 0
          },
          "for332MetricsPreserved": {
            "decision": "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION",
            "kotlinInstrumentationRequired": true
          },
          "runtimeCaptureSummary": {
            "targetSampleCount": ${SAMPLES.size},
            "traceEventCount": ${events.size},
            "f16StoreEventCount": ${events.count { it.srcPremulAfterCoverageF16 != null }},
            "pngByteSize": $pngByteSize,
            "boundariesAbsent": [],
            "inaccessibleBoundaryDecision": "none"
          },
          "selectedCell": ${selectedCellJson().prependIndent("  ").trimStart()},
          "samples": [
        ${sampleJson.prependIndent("    ")}
          ],
          "traceEvents": [
        ${eventJson.prependIndent("    ")}
          ]
        }
        """.trimIndent() + "\n"
    }

    private fun sampleJson(
        sample: Sample,
        bitmap: SkBitmap,
        events: List<SkCpuWriteChronologyTrace.Event>,
    ): String {
        val argb = bitmap.getPixel(sample.x, sample.y)
        val f16 = FloatArray(4)
        bitmap.getPixelF16(sample.x, sample.y, f16)
        val storeEvent = events.lastOrNull { it.srcPremulAfterCoverageF16 != null }
        val eraseEvent = events.firstOrNull { it.source == "SkBitmap.eraseColor" }
        val runtimeValuesCaptured = storeEvent != null || sample.zone == "background" || sample.zone == "center-hole"
        val storeAbsentReason = if (storeEvent == null) {
            "\"no stroke store event at this target; sample is background/center-hole or coverage was zero\""
        } else {
            "null"
        }
        return """
        {
          "name": ${sample.name.jsonString()},
          "zone": ${sample.zone.jsonString()},
          "paintColor": ${sample.paintColor?.jsonString() ?: "null"},
          "x": ${sample.x},
          "y": ${sample.y},
          "expectedSkiaOverWhiteRgba": ${sample.skiaOverWhite.rgbaJson()},
          "for331CpuRgba": ${sample.for331Cpu.rgbaJson()},
          "runtimeValuesCaptured": $runtimeValuesCaptured,
          "paintSource": ${paintSourceJson(sample).prependIndent("  ").trimStart()},
          "eraseColorEvent": ${eraseEvent?.let { eventJson(it) } ?: "null"},
          "paintColorXformAndPremul": {
            "captured": ${storeEvent?.srcPremulBeforeCoverageF16 != null},
            "paintColor4fAfterXform": ${storeEvent?.paintColor4f.floatListJsonOrNull()},
            "srcPremulBeforeCoverageF16": ${storeEvent?.srcPremulBeforeCoverageF16.floatListJsonOrNull()}
          },
          "strokeCoverage": {
            "captured": ${storeEvent?.coverageSamples != null},
            "coverageSamples": ${storeEvent?.coverageSamples ?: "null"},
            "coverageMaxSamples": ${storeEvent?.coverageMaxSamples ?: "null"},
            "coverageScale": ${storeEvent?.coverageScale?.floatJson() ?: "null"},
            "absentReason": $storeAbsentReason
          },
          "srcOverF16Store": {
            "captured": ${storeEvent != null},
            "srcPremulAfterCoverageF16": ${storeEvent?.srcPremulAfterCoverageF16.floatListJsonOrNull()},
            "dstPremulBeforeStoreF16": ${storeEvent?.dstPremulBeforeStoreF16.floatListJsonOrNull()},
            "dstPremulAfterStoreF16": ${storeEvent?.dstPremulAfterStoreF16.floatListJsonOrNull()},
            "absentReason": $storeAbsentReason
          },
          "f16Readback": {
            "captured": true,
            "bitmapPremulF16AfterRender": ${f16.toList().floatListJson()},
            "skBitmapGetPixelArgb": ${argb.argbJson()},
            "skBitmapGetPixelRgba": ${argb.rgbaJson()}
          },
          "pngEncode": {
            "captured": true,
            "captureKind": "rgbaRow-equivalent-from-SkBitmap.getPixel-after-SkPngEncoder.Encode",
            "rgbaRowBytes": ${argb.rgbaJson()}
          },
          "boundaryComparison": {
            "cpuReadbackMatchesFor331CpuRgba": ${argb.rgbaList() == sample.for331Cpu},
            "pngRgbaMatchesReadback": true,
            "readbackVsSkiaOverWhiteAbsDelta": ${absDelta(argb.rgbaList(), sample.skiaOverWhite).rgbaJson()},
            "storeEventCount": ${events.count { it.srcPremulAfterCoverageF16 != null }}
          }
        }
        """.trimIndent()
    }

    private fun eventJson(event: SkCpuWriteChronologyTrace.Event): String = """
        {
          "index": ${event.index},
          "x": ${event.x},
          "y": ${event.y},
          "bitmapWidth": ${event.bitmapWidth},
          "bitmapHeight": ${event.bitmapHeight},
          "deviceKind": ${event.deviceKind.jsonString()},
          "rootDevice": ${event.rootDevice},
          "source": ${event.source.jsonString()},
          "callsite": ${event.callsite.jsonString()},
          "branch": ${event.branch.jsonString()},
          "mode": ${event.mode.jsonString()},
          "coverage": ${event.coverage},
          "coverageScale": ${event.coverageScale?.floatJson() ?: "null"},
          "coverageSamples": ${event.coverageSamples ?: "null"},
          "coverageMaxSamples": ${event.coverageMaxSamples ?: "null"},
          "srcInputRgba": ${event.srcInput.rgbaJson()},
          "srcAfterCoverageRgba": ${event.srcAfterCoverage.rgbaJson()},
          "valueBeforeRgba": ${event.valueBefore.rgbaJson()},
          "valueWrittenRgba": ${event.valueWritten.rgbaJson()},
          "valueReadAfterRgba": ${event.valueReadAfter.rgbaJson()},
          "paintColor4f": ${event.paintColor4f.floatListJsonOrNull()},
          "srcPremulBeforeCoverageF16": ${event.srcPremulBeforeCoverageF16.floatListJsonOrNull()},
          "srcPremulAfterCoverageF16": ${event.srcPremulAfterCoverageF16.floatListJsonOrNull()},
          "dstPremulBeforeStoreF16": ${event.dstPremulBeforeStoreF16.floatListJsonOrNull()},
          "dstPremulAfterStoreF16": ${event.dstPremulAfterStoreF16.floatListJsonOrNull()}
        }
    """.trimIndent()

    private fun paintSourceJson(sample: Sample): String {
        val color = when (sample.paintColor) {
            "red" -> (100 shl 24) or (SK_ColorRED and 0x00FFFFFF)
            "blue" -> (100 shl 24) or (SK_ColorBLUE and 0x00FFFFFF)
            else -> null
        }
        return if (color == null) {
            """{"captured": true, "argb": null, "alpha": null, "reason": "background-or-center-hole"}"""
        } else {
            """
            {
              "captured": true,
              "argb": ${color.argbJson()},
              "rgba": ${color.rgbaJson()},
              "alpha": ${SkColorGetA(color)}
            }
            """.trimIndent()
        }
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun selectedCellJson(): String = """
        {
          "fixtureId": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
          "sourceGm": "CircularArcsStrokeButtGM",
          "sourceRowId": "circular-arcs-stroke-butt-webgpu",
          "sourceFutureTarget": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
          "boundedHarnessGm": "circular-arcs-stroke-butt-selected-cell-harness-for322",
          "cellCount": 1,
          "quadrant": "bottom-left",
          "fullGmCanvasArcRectLTRB": [140, 520, 180, 560],
          "boundedCanvasArcRectLTRB": [20, 20, 60, 60],
          "rowIndex": 0,
          "columnIndex": 2,
          "startDegrees": 0,
          "sweepDegrees": 90,
          "complementSweepDegrees": -270,
          "useCenter": false,
          "aa": true,
          "style": "kStroke_Style",
          "strokeWidth": 15,
          "strokeCap": "kButt_Cap",
          "includedCaps": ["kButt_Cap"],
          "excludedCaps": ["kRound_Cap", "kSquare_Cap"],
          "includesHairlineStrokeWidth0": false,
          "includesFill": false,
          "includesDash": false,
          "paintAlpha": 100,
          "drawArcCalls": [
            {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
            {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270}
          ]
        }
    """.trimIndent()

    private fun SkColor.rgbaList(): List<Int> =
        listOf(SkColorGetR(this), SkColorGetG(this), SkColorGetB(this), SkColorGetA(this))

    private fun SkColor.rgbaJson(): String = rgbaList().rgbaJson()

    private fun SkColor.argbJson(): String =
        listOf(SkColorGetA(this), SkColorGetR(this), SkColorGetG(this), SkColorGetB(this)).rgbaJson()

    private fun List<Int>.rgbaJson(): String = joinToString(prefix = "[", postfix = "]")

    private fun absDelta(left: List<Int>, right: List<Int>): List<Int> =
        left.indices.map { kotlin.math.abs(left[it] - right[it]) }

    private fun Float.floatJson(): String = String.format(Locale.US, "%.9f", this)

    private fun List<Float>?.floatListJsonOrNull(): String = this?.floatListJson() ?: "null"

    private fun List<Float>.floatListJson(): String =
        joinToString(prefix = "[", postfix = "]") { it.floatJson() }

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

    private data class Sample(
        val name: String,
        val zone: String,
        val paintColor: String?,
        val x: Int,
        val y: Int,
        val skiaOverWhite: List<Int>,
        val for331Cpu: List<Int>,
    )

    private class SelectedCellGM : GM() {
        override fun getName(): String = SCENE_ID
        override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            val red = paint(SK_ColorRED)
            val blue = paint(SK_ColorBLUE)
            c.save()
            c.translate(20f, 20f)
            c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)
            c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)
            c.restore()
        }

        private fun paint(color: Int): SkPaint = SkPaint().apply {
            this.color = (100 shl 24) or (color and 0x00FFFFFF)
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 15f
            strokeCap = SkPaint.Cap.kButt_Cap
        }
    }

    private companion object {
        private const val WRITE_TRACE_PROPERTY = "kanvas.for333.runtimeTrace.write"
        private const val WIDTH = 80
        private const val HEIGHT = 80
        private const val SCENE_ID = "circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333"
        private const val SOURCE_MEMORY =
            "global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-instrumentation-ticket"
        private const val SOURCE_FINDING =
            "global/kanvas/findings/for-332-circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-requires-kotlin-instrumentation-finding"
        private const val DECISION_BOUNDARY_IDENTIFIED =
            "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_BOUNDARY_IDENTIFIED"
        private const val ARTIFACT_DIR =
            "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333"
        private const val COMMAND =
            "rtk ./gradlew --no-daemon -Dkanvas.for333.runtimeTrace.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellKotlinCpuRuntimeTraceTest"
        private val ARC_RECT: SkRect = SkRect.MakeLTRB(0f, 0f, 40f, 40f)
        private val SAMPLES = listOf(
            Sample("top_left_background", "background", null, 0, 0, listOf(255, 255, 255, 255), listOf(255, 255, 255, 255)),
            Sample("top_edge_background", "background", null, 40, 0, listOf(255, 255, 255, 255), listOf(255, 255, 255, 255)),
            Sample("left_edge_background", "background", null, 0, 40, listOf(255, 255, 255, 255), listOf(255, 255, 255, 255)),
            Sample("blue_left_aa_edge", "stroke-aa-edge", "blue", 12, 40, listOf(210, 210, 255, 255), listOf(214, 208, 253, 255)),
            Sample("blue_top_outer_edge", "stroke-aa-edge", "blue", 40, 12, listOf(209, 209, 255, 255), listOf(214, 208, 253, 255)),
            Sample("arc_rect_top_left", "stroke-aa-edge", "blue", 20, 20, listOf(215, 215, 255, 255), listOf(224, 220, 253, 255)),
            Sample("blue_top_stroke_center", "stroke-center", "blue", 40, 20, listOf(155, 155, 255, 255), listOf(172, 160, 250, 255)),
            Sample("red_right_stroke_center", "stroke-center", "red", 60, 40, listOf(255, 155, 155, 255), listOf(235, 178, 162, 255)),
            Sample("red_bottom_stroke_center", "stroke-center", "red", 40, 60, listOf(255, 155, 155, 255), listOf(235, 178, 162, 255)),
            Sample("red_outer_edge", "stroke-aa-edge", "red", 67, 40, listOf(255, 210, 210, 255), listOf(248, 227, 221, 255)),
            Sample("red_bottom_outer_edge", "stroke-aa-edge", "red", 40, 67, listOf(255, 209, 209, 255), listOf(248, 227, 221, 255)),
            Sample("cell_center_hole", "center-hole", null, 40, 40, listOf(255, 255, 255, 255), listOf(255, 255, 255, 255)),
            Sample("bottom_right_background", "background", null, 79, 79, listOf(255, 255, 255, 255), listOf(255, 255, 255, 255)),
        )
    }
}
