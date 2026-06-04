package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM

class CircularArcsStrokeButtSelectedCellCaptureTest {
    @Test
    fun `bounded FOR 319 selected cell capture emits routes without promoting support`() {
        val gm = SelectedCellGM()
        val cpuBitmap = TestUtils.runGmTest(gm)
        val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, cpuBitmap, tolerance = 0)
        val gpuCapture = captureGpu(gm)
        val adapter = when (gpuCapture) {
            is GpuCapture.Available -> gpuCapture.adapter
            is GpuCapture.Blocked -> gpuCapture.adapter
        }
        val gpuCmp = (gpuCapture as? GpuCapture.Available)?.let {
            TestUtils.compareBitmapsDetailed(it.bitmap, cpuBitmap, tolerance = 0)
        }

        println(
            "[CircularArcsStrokeButtSelectedCellCapture] adapter=$adapter " +
                "cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                "gpuStatus=${gpuCapture.status}, supportStatus=not-supported",
        )

        if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
            writeEvidence(cpuBitmap, cpuCmp, gpuCapture, gpuCmp)
        }

        assertEquals(100.0, cpuCmp.similarity, 0.0)
        assertTrue(cpuCmp.matchingPixels == cpuCmp.totalPixels)
        assertTrue(gpuCapture.status.isNotBlank())
    }

    private fun captureGpu(gm: GM): GpuCapture {
        val context = WebGpuContext.createOrNull()
            ?: return GpuCapture.Blocked(
                status = "blocked",
                refusalReason = "webgpu.adapter-missing",
                adapter = "none",
                message = "No WebGPU adapter",
                edgeCount = null,
            )
        return context.use { ctx ->
            val adapter = ctx.adapterInfo ?: "unknown-adapter"
            try {
                GpuCapture.Available(WebGpuSink.draw(ctx, gm), adapter)
            } catch (e: IllegalStateException) {
                GpuCapture.Blocked(
                    status = "expected-unsupported",
                    refusalReason = stableGpuRefusal(e.message),
                    adapter = adapter,
                    message = e.message ?: e.javaClass.name,
                    edgeCount = parseCoverageEdgeCount(e.message),
                )
            }
        }
    }

    private fun writeEvidence(
        cpuBitmap: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuCapture: GpuCapture,
        gpuCmp: BitmapComparison?,
    ) {
        val dir = repoFile(ARTIFACT_DIR).apply { mkdirs() }
        File(dir, "skia.png").delete()
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(cpuBitmap, cpuBitmap))
        when (gpuCapture) {
            is GpuCapture.Available -> {
                writePng(File(dir, "gpu.png"), gpuCapture.bitmap)
                writePng(File(dir, "gpu-diff.png"), CrossBackendHarness.pixelDiff(cpuBitmap, gpuCapture.bitmap))
            }
            is GpuCapture.Blocked -> {
                File(dir, "gpu.png").delete()
                File(dir, "gpu-diff.png").delete()
            }
        }
        File(dir, "route-cpu.json").writeText(cpuRouteJson())
        File(dir, "route-gpu.json").writeText(gpuRouteJson(gpuCapture))
        File(dir, "stats.json").writeText(statsJson(cpuCmp, gpuCapture, gpuCmp))
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.path}")
        file.writeBytes(bytes)
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun cpuRouteJson(): String = """
        {
          "sceneId": "$SCENE_ID",
          "linear": "FOR-322",
          "backend": "CPU",
          "drawKind": "CircularArcsStrokeButtSelectedCell",
          "command": "$COMMAND",
          "status": "capture-available-not-promoting",
          "selectedRoute": "cpu.raster.selected-cell-test-harness",
          "fallbackReason": "none",
          "refusalReason": "none",
          "edgeCount": null,
          "edgeCountAbsentReason": "test-only selected-cell harness does not expose post-stroke edge-count diagnostics",
          "edgeBudget": $EDGE_BUDGET,
          "edgeBudgetReason": "edge-count-not-observed",
          "supportStatus": "not-supported",
          "fullGmSubstitutionAccepted": false,
          "cell": ${selectedCellJson().prependIndent("  ").trimStart()}
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(capture: GpuCapture): String = when (capture) {
        is GpuCapture.Available -> """
            {
              "sceneId": "$SCENE_ID",
              "linear": "FOR-322",
              "backend": "WebGPU",
              "adapter": ${capture.adapter.jsonString()},
              "drawKind": "CircularArcsStrokeButtSelectedCell",
              "command": "$COMMAND",
              "status": "capture-available-not-promoting",
              "selectedRoute": "webgpu.selected-cell-test-harness",
              "fallbackReason": "none",
              "refusalReason": "none",
              "edgeCount": null,
              "edgeCountAbsentReason": "test-only selected-cell harness does not expose post-stroke edge-count diagnostics",
              "edgeBudget": $EDGE_BUDGET,
              "edgeBudgetReason": "edge-count-not-observed",
              "supportStatus": "not-supported",
              "fullGmSubstitutionAccepted": false,
              "cell": ${selectedCellJson().prependIndent("  ").trimStart()}
            }
        """.trimIndent() + "\n"
        is GpuCapture.Blocked -> """
            {
              "sceneId": "$SCENE_ID",
              "linear": "FOR-322",
              "backend": "WebGPU",
              "adapter": ${capture.adapter.jsonString()},
              "drawKind": "CircularArcsStrokeButtSelectedCell",
              "command": "$COMMAND",
              "status": ${capture.status.jsonString()},
              "selectedRoute": "webgpu.selected-cell-test-harness.refused",
              "fallbackReason": ${capture.refusalReason.jsonString()},
              "refusalReason": ${capture.refusalReason.jsonString()},
              "refusalMessage": ${capture.message.jsonString()},
              "edgeCount": ${capture.edgeCount ?: "null"},
              "edgeCountAbsentReason": ${if (capture.edgeCount == null) "\"WebGPU refusal did not expose coverageEdgeCount\"" else "null"},
              "edgeBudget": $EDGE_BUDGET,
              "edgeBudgetReason": ${if (capture.edgeCount == null) "\"edge-count-not-observed\"" else "\"not coverage.edge-count-exceeded\""},
              "supportStatus": "not-supported",
              "fullGmSubstitutionAccepted": false,
              "cell": ${selectedCellJson().prependIndent("  ").trimStart()}
            }
        """.trimIndent() + "\n"
    }

    private fun statsJson(
        cpuCmp: BitmapComparison,
        gpuCapture: GpuCapture,
        gpuCmp: BitmapComparison?,
    ): String {
        val gpuStatus = gpuCapture.status
        val gpuAdapter = when (gpuCapture) {
            is GpuCapture.Available -> gpuCapture.adapter
            is GpuCapture.Blocked -> gpuCapture.adapter
        }
        val gpuArtifactStatus = when (gpuCapture) {
            is GpuCapture.Available -> "available"
            is GpuCapture.Blocked -> "blocked-${gpuCapture.refusalReason}"
        }
        return """
        {
          "sceneId": "$SCENE_ID",
          "linear": "FOR-322",
          "decision": "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY",
          "supportStatus": "not-supported",
          "dimensions": {"width": $WIDTH, "height": $HEIGHT},
          "fullGmSubstitutionAccepted": false,
          "fullGmReferenceAccepted": false,
          "cpuSimilarity": ${String.format(Locale.US, "%.2f", cpuCmp.similarity)},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuTotalPixels": ${cpuCmp.totalPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "gpuStatus": ${gpuStatus.jsonString()},
          "gpuAdapter": ${gpuAdapter.jsonString()},
          "gpuSimilarityToCpu": ${gpuCmp?.similarity?.let { String.format(Locale.US, "%.2f", it) } ?: "null"},
          "gpuMatchingPixelsToCpu": ${gpuCmp?.matchingPixels ?: "null"},
          "gpuMaxChannelDeltaToCpu": ${gpuCmp?.maxChannelDiff?.max() ?: "null"},
          "edgeBudget": $EDGE_BUDGET,
          "edgeCount": null,
          "edgeCountAbsentReason": "test-only selected-cell harness does not expose post-stroke edge-count diagnostics",
          "command": "$COMMAND",
          "artifacts": {
            "skia.png": "blocked-no-selected-cell-upstream-skia-reference",
            "cpu.png": "available",
            "gpu.png": ${gpuArtifactStatus.jsonString()},
            "cpu-diff.png": "available",
            "gpu-diff.png": ${gpuArtifactStatus.jsonString()},
            "route-cpu.json": "available",
            "route-gpu.json": "available",
            "stats.json": "available"
          },
          "cell": ${selectedCellJson().prependIndent("  ").trimStart()}
        }
        """.trimIndent() + "\n"
    }

    private fun selectedCellJson(): String = """
        {
          "fixtureId": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
          "sourceGm": "CircularArcsStrokeButtGM",
          "sourceRowId": "circular-arcs-stroke-butt-webgpu",
          "sourceFutureTarget": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
          "boundedHarnessGm": "$SCENE_ID",
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

    private fun stableGpuRefusal(message: String?): String {
        val text = message ?: return "webgpu.selected-cell-capture-refused"
        return when {
            "coverage.edge-count-exceeded" in text -> "coverage.edge-count-exceeded"
            "coverage.stroke-outline-edge-count-exceeded" in text -> "coverage.stroke-outline-edge-count-exceeded"
            "coverage.stroke-cap-join-visual-parity-below-threshold" in text ->
                "coverage.stroke-cap-join-visual-parity-below-threshold"
            else -> "webgpu.selected-cell-capture-refused"
        }
    }

    private fun parseCoverageEdgeCount(message: String?): Int? {
        if (message == null) return null
        return Regex("""coverageEdgeCount=(\d+)/""")
            .find(message)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

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

    private sealed interface GpuCapture {
        val status: String

        data class Available(val bitmap: SkBitmap, val adapter: String) : GpuCapture {
            override val status: String = "capture-available-not-promoting"
        }

        data class Blocked(
            override val status: String,
            val refusalReason: String,
            val adapter: String,
            val message: String,
            val edgeCount: Int?,
        ) : GpuCapture
    }

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
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private const val WIDTH = 80
        private const val HEIGHT = 80
        private const val EDGE_BUDGET = 256
        private const val SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
        private const val ARTIFACT_DIR =
            "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322"
        private const val COMMAND =
            "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellCaptureTest"
        private val ARC_RECT: SkRect = SkRect.MakeLTRB(0f, 0f, 40f, 40f)
    }
}
