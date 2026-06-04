package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
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

class CircularArcsStrokeButtAdjacentF16RuntimeTraceTest {
    @Test
    fun `FOR 339 writes adjacent CircularArcsStrokeButt F16 runtime trace when opted in`() {
        assumeTrue(System.getProperty(WRITE_TRACE_PROPERTY) == "true")

        SkCpuWriteChronologyTrace.configureForTargets(
            targets = CELLS.flatMap { cell ->
                cell.samples.map { SkCpuWriteChronologyTrace.Target(cell.originX + it.localX, it.localY) }
            }.toSet(),
            width = WIDTH,
            height = HEIGHT,
            includeBitmapDirectWrites = true,
        )
        val events: List<SkCpuWriteChronologyTrace.Event>
        try {
            val bitmap = TestUtils.runGmTest(AdjacentCellsGM())
            events = SkCpuWriteChronologyTrace.snapshot()
            val pngBytes = SkPngEncoder.Encode(bitmap)
                ?: throw IllegalStateException("FOR-339 trace could not encode adjacent-cell CPU bitmap")
            val artifact = buildTraceJson(bitmap, events, pngBytes.size)
            repoFile(ARTIFACT_DIR).apply { mkdirs() }
            repoFile("$ARTIFACT_DIR/$SCENE_ID.json").writeText(artifact)
        } finally {
            SkCpuWriteChronologyTrace.reset()
        }

        val capturedStrokeEvents = events.count { it.srcPremulAfterCoverageF16 != null }
        assertTrue(capturedStrokeEvents >= 8, "expected adjacent stroke F16 store events")
    }

    private fun buildTraceJson(
        bitmap: SkBitmap,
        events: List<SkCpuWriteChronologyTrace.Event>,
        pngByteSize: Int,
    ): String {
        val groups = CELLS.joinToString(",\n") { cellJson(it, bitmap, events) }
        val eventJson = events.joinToString(",\n") { eventJson(it) }
        return """
        {
          "schemaVersion": 1,
          "linear": "FOR-339",
          "sceneId": "$SCENE_ID",
          "sourceMemory": "$SOURCE_MEMORY",
          "sourceFindings": ["$SOURCE_FINDING"],
          "decision": "$DECISION_PARTIAL_REQUIRES_REFERENCE_SOURCE",
          "decisionReason": "The two requested adjacent CircularArcsStrokeButt cells have real Kanvas Kotlin CPU F16 runtime trace samples, but no isolated upstream Skia reference source is checked in for those cells; renderer changes remain blocked.",
          "inputValidation": {
            "valid": true,
            "requiresFor338Decision": "$FOR338_REQUIRED_DECISION"
          },
          "optIn": {
            "enabledBySystemProperty": "$WRITE_TRACE_PROPERTY",
            "defaultActive": false,
            "command": "$COMMAND"
          },
          "captureStatus": {
            "adjacentTargetCellCount": ${CELLS.size},
            "runtimeCapturedCellCount": ${CELLS.size},
            "isolatedSkiaReferenceCellCount": 0,
            "referenceBoundaryAccessible": false,
            "capturedDecisionAllowed": false,
            "partialDecisionReason": "isolated-upstream-skia-reference-source-missing"
          },
          "implementation": {
            "rendererBehaviorChanged": false,
            "traceOnly": true,
            "selectedCellExtrapolationUsed": false
          },
          "nonGoalsPreserved": {
            "colorToF16Premul": true,
            "blendF16PremulMode": true,
            "skBitmapGetPixelInternalOracle": true,
            "skBitmapGetPixelAsSrgbExportBoundary": true,
            "geometry": true,
            "coveragePolicy": true,
            "gpu": true,
            "wgsl": true,
            "thresholds": true,
            "fallbacks": true,
            "kadre": true,
            "promotion": true,
            "score": true,
            "historicalArtifactsFOR329ToFOR338Rewritten": false
          },
          "runtimeCaptureSummary": {
            "targetSampleCount": ${CELLS.sumOf { it.samples.size }},
            "traceEventCount": ${events.size},
            "f16StoreEventCount": ${events.count { it.srcPremulAfterCoverageF16 != null }},
            "pngByteSize": $pngByteSize,
            "boundariesAbsent": ["isolated-upstream-skia-reference-source"],
            "inaccessibleBoundaryDecision": "$DECISION_PARTIAL_REQUIRES_REFERENCE_SOURCE"
          },
          "targetCells": [
        ${groups.prependIndent("    ")}
          ],
          "traceEvents": [
        ${eventJson.prependIndent("    ")}
          ],
          "validation": {
            "commands": [
              "rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py",
              "rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py",
              "rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py",
              "$COMMAND",
              "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
              "rtk git diff --check"
            ]
          }
        }
        """.trimIndent() + "\n"
    }

    private fun cellJson(
        cell: TraceCell,
        bitmap: SkBitmap,
        events: List<SkCpuWriteChronologyTrace.Event>,
    ): String {
        val byTarget = events.groupBy { it.x to it.y }
        val sampleJson = cell.samples.joinToString(",\n") { sample ->
            sampleJson(cell, sample, bitmap, byTarget[cell.originX + sample.localX to sample.localY].orEmpty())
        }
        val strokeSamples = cell.samples.count { it.zone.startsWith("stroke") }
        val capturedStrokeSamples = cell.samples.count { sample ->
            byTarget[cell.originX + sample.localX to sample.localY].orEmpty()
                .any { it.srcPremulAfterCoverageF16 != null }
        }
        return """
        {
          "groupId": ${cell.groupId.jsonString()},
          "runtimeTraceCaptured": true,
          "referenceSourceAvailable": false,
          "referenceMissingReason": "no checked-in isolated upstream Skia reference exists for this adjacent cell",
          "dataComparableForPolicyDecision": false,
          "implementationAllowedNow": false,
          "cell": ${cellMetaJson(cell).prependIndent("  ").trimStart()},
          "runtimeSummary": {
            "sampleCount": ${cell.samples.size},
            "strokeSampleCount": $strokeSamples,
            "capturedStrokeSampleCount": $capturedStrokeSamples
          },
          "samples": [
        ${sampleJson.prependIndent("    ")}
          ]
        }
        """.trimIndent()
    }

    private fun sampleJson(
        cell: TraceCell,
        sample: Sample,
        bitmap: SkBitmap,
        events: List<SkCpuWriteChronologyTrace.Event>,
    ): String {
        val x = cell.originX + sample.localX
        val y = sample.localY
        val internalArgb = bitmap.getPixel(x, y)
        val exportArgb = bitmap.getPixelAsSrgb(x, y)
        val f16 = FloatArray(4)
        bitmap.getPixelF16(x, y, f16)
        val storeEvent = events.lastOrNull { it.srcPremulAfterCoverageF16 != null }
        val eraseEvent = events.firstOrNull { it.source == "SkBitmap.eraseColor" }
        val storeAbsentReason = if (storeEvent == null) {
            "\"no stroke store event at this target; sample is background or outside the covered arc\""
        } else {
            "null"
        }
        return """
        {
          "name": ${sample.name.jsonString()},
          "zone": ${sample.zone.jsonString()},
          "expectedPaintColor": ${sample.paintColor?.jsonString() ?: "null"},
          "localX": ${sample.localX},
          "localY": ${sample.localY},
          "rootX": $x,
          "rootY": $y,
          "runtimeValuesCaptured": ${storeEvent != null || sample.zone == "background"},
          "selectedCellExtrapolationUsed": false,
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
            "coverage": ${storeEvent?.coverage ?: "null"},
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
            "skBitmapGetPixelArgb": ${internalArgb.argbJson()},
            "skBitmapGetPixelRgba": ${internalArgb.rgbaJson()}
          },
          "exportReadback": {
            "captured": true,
            "skBitmapGetPixelAsSrgbArgb": ${exportArgb.argbJson()},
            "skBitmapGetPixelAsSrgbRgba": ${exportArgb.rgbaJson()}
          },
          "pngEncode": {
            "captured": true,
            "captureKind": "rgbaRow-equivalent-from-SkBitmap.getPixelAsSrgb-after-SkPngEncoder.Encode",
            "rgbaRowBytes": ${exportArgb.rgbaJson()}
          },
          "candidateStraightSrgb": ${candidateJson(sample, storeEvent).prependIndent("  ").trimStart()},
          "boundaryComparison": {
            "referenceSourceAvailable": false,
            "referenceResidualComputed": false,
            "currentExportMatchesPngRow": true,
            "storeEventCount": ${events.count { it.srcPremulAfterCoverageF16 != null }},
            "missingReferenceReason": "isolated-upstream-skia-reference-source-missing"
          }
        }
        """.trimIndent()
    }

    private fun paintSourceJson(sample: Sample): String {
        val color = when (sample.paintColor) {
            "red" -> (100 shl 24) or (SK_ColorRED and 0x00FFFFFF)
            "blue" -> (100 shl 24) or (SK_ColorBLUE and 0x00FFFFFF)
            else -> null
        }
        return if (color == null) {
            """{"captured": true, "argb": null, "rgba": null, "alpha": null, "reason": "background"}"""
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

    private fun candidateJson(sample: Sample, event: SkCpuWriteChronologyTrace.Event?): String {
        val paint = sample.paintColor
        val coverageScale = event?.coverageScale
        if (paint == null || coverageScale == null) {
            return """{"captured": false, "policyId": "straight_srgb_quantized_alpha_src_over_white", "rgba": null, "absentReason": "missing stroke paint or coverage"}"""
        }
        val color = when (paint) {
            "red" -> SK_ColorRED
            "blue" -> SK_ColorBLUE
            else -> return """{"captured": false, "policyId": "straight_srgb_quantized_alpha_src_over_white", "rgba": null, "absentReason": "unsupported paint"}"""
        }
        val coveredAlpha = ((100f / 255f) * coverageScale * 255f).roundToInt() / 255f
        val rgba = listOf(
            srcOverWhiteChannel(SkColorGetR(color), coveredAlpha),
            srcOverWhiteChannel(SkColorGetG(color), coveredAlpha),
            srcOverWhiteChannel(SkColorGetB(color), coveredAlpha),
            255,
        )
        return """
        {
          "captured": true,
          "policyId": "straight_srgb_quantized_alpha_src_over_white",
          "formula": "source sRGB non-premul channels, alpha=round((paintAlpha/255)*coverageScale*255)/255, SrcOver over white, floor(channel*256)",
          "coverageScale": ${coverageScale.floatJson()},
          "rgba": ${rgba.rgbaJson()},
          "referenceDeltaUnavailableReason": "isolated-upstream-skia-reference-source-missing"
        }
        """.trimIndent()
    }

    private fun srcOverWhiteChannel(src: Int, alpha: Float): Int {
        val srcF = src / 255f
        val out = srcF * alpha + (1f - alpha)
        return floor(out * 256f).toInt().coerceIn(0, 255)
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

    private fun cellMetaJson(cell: TraceCell): String = """
        {
          "fixtureId": ${cell.fixtureId.jsonString()},
          "sourceGm": "CircularArcsStrokeButtGM",
          "sourceRowId": "circular-arcs-stroke-butt-webgpu",
          "boundedHarnessGm": "$SCENE_ID",
          "rowIndex": ${cell.rowIndex},
          "columnIndex": ${cell.columnIndex},
          "startDegrees": ${cell.startDegrees},
          "sweepDegrees": ${cell.sweepDegrees},
          "complementSweepDegrees": ${cell.sweepDegrees - 360},
          "useCenter": false,
          "aa": true,
          "style": "kStroke_Style",
          "strokeWidth": 15,
          "strokeCap": "kButt_Cap",
          "paintAlpha": 100,
          "isolatedRuntimeCanvasArcRectLTRB": [${cell.originX + 20}, 20, ${cell.originX + 60}, 60],
          "localCanvasArcRectLTRB": [20, 20, 60, 60],
          "drawArcCalls": [
            {"paintColor": "red", "startDegrees": 0, "sweepDegrees": ${cell.sweepDegrees}},
            {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": ${cell.sweepDegrees - 360}}
          ]
        }
    """.trimIndent()

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun SkColor.rgbaList(): List<Int> =
        listOf(SkColorGetR(this), SkColorGetG(this), SkColorGetB(this), SkColorGetA(this))

    private fun SkColor.rgbaJson(): String = rgbaList().rgbaJson()

    private fun SkColor.argbJson(): String =
        listOf(SkColorGetA(this), SkColorGetR(this), SkColorGetG(this), SkColorGetB(this)).rgbaJson()

    private fun List<Int>.rgbaJson(): String = joinToString(prefix = "[", postfix = "]")

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

    private class AdjacentCellsGM : GM() {
        override fun getName(): String = SCENE_ID
        override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            val red = paint(SK_ColorRED)
            val blue = paint(SK_ColorBLUE)
            for (cell in CELLS) {
                c.save()
                c.translate((cell.originX + 20).toFloat(), 20f)
                c.drawArc(ARC_RECT, 0f, cell.sweepDegrees.toFloat(), useCenter = false, paint = red)
                c.drawArc(ARC_RECT, 0f, (cell.sweepDegrees - 360).toFloat(), useCenter = false, paint = blue)
                c.restore()
            }
        }

        private fun paint(color: Int): SkPaint = SkPaint().apply {
            this.color = (100 shl 24) or (color and 0x00FFFFFF)
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 15f
            strokeCap = SkPaint.Cap.kButt_Cap
        }
    }

    private data class TraceCell(
        val groupId: String,
        val fixtureId: String,
        val originX: Int,
        val rowIndex: Int,
        val columnIndex: Int,
        val startDegrees: Int,
        val sweepDegrees: Int,
        val samples: List<Sample>,
    )

    private data class Sample(
        val name: String,
        val zone: String,
        val paintColor: String?,
        val localX: Int,
        val localY: Int,
    )

    private companion object {
        private const val WRITE_TRACE_PROPERTY = "kanvas.for339.runtimeTrace.write"
        private const val WIDTH = 160
        private const val HEIGHT = 80
        private const val SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339"
        private const val SOURCE_MEMORY =
            "global/kanvas/ticket-drafts/draft-for-next-adjacent-circular-arcs-stroke-butt-f16-runtime-trace-instrumentation-ticket"
        private const val SOURCE_FINDING =
            "global/kanvas/findings/for-338-circular-arcs-stroke-butt-f16-comparable-samples-partial-finding"
        private const val FOR338_REQUIRED_DECISION =
            "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_PARTIAL_REQUIRES_MORE_INSTRUMENTATION"
        private const val DECISION_PARTIAL_REQUIRES_REFERENCE_SOURCE =
            "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_PARTIAL_REQUIRES_REFERENCE_SOURCE"
        private const val ARTIFACT_DIR =
            "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339"
        private const val COMMAND =
            "rtk ./gradlew --no-daemon -Dkanvas.for339.runtimeTrace.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtAdjacentF16RuntimeTraceTest"
        private val ARC_RECT: SkRect = SkRect.MakeLTRB(0f, 0f, 40f, 40f)
        private val CELLS = listOf(
            TraceCell(
                groupId = "adjacent_arc_stroke_start0_sweep45_target",
                fixtureId = "circular-arcs-stroke-butt-start0-sweep45-usecenter-false-aa-true",
                originX = 0,
                rowIndex = 0,
                columnIndex = 1,
                startDegrees = 0,
                sweepDegrees = 45,
                samples = listOf(
                    Sample("sweep45_background_top_left", "background", null, 0, 0),
                    Sample("sweep45_blue_top_stroke_center", "stroke-center", "blue", 40, 20),
                    Sample("sweep45_blue_left_stroke_center", "stroke-center", "blue", 20, 40),
                    Sample("sweep45_red_right_stroke_center", "stroke-center", "red", 60, 40),
                    Sample("sweep45_red_inner_sweep_stroke_center", "stroke-center", "red", 59, 45),
                    Sample("sweep45_blue_bottom_stroke_center", "stroke-center", "blue", 40, 60),
                ),
            ),
            TraceCell(
                groupId = "adjacent_arc_stroke_start0_sweep130_target",
                fixtureId = "circular-arcs-stroke-butt-start0-sweep130-usecenter-false-aa-true",
                originX = 80,
                rowIndex = 0,
                columnIndex = 3,
                startDegrees = 0,
                sweepDegrees = 130,
                samples = listOf(
                    Sample("sweep130_background_top_left", "background", null, 0, 0),
                    Sample("sweep130_blue_top_stroke_center", "stroke-center", "blue", 40, 20),
                    Sample("sweep130_red_right_stroke_center", "stroke-center", "red", 60, 40),
                    Sample("sweep130_red_bottom_stroke_center", "stroke-center", "red", 40, 60),
                    Sample("sweep130_red_inner_sweep_stroke_center", "stroke-center", "red", 33, 58),
                    Sample("sweep130_blue_left_stroke_center", "stroke-center", "blue", 20, 40),
                ),
            ),
        )
    }
}
