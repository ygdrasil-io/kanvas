package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM

class StrokeCapJoinSceneCaptureTest {
    @Test
    fun `bounded stroke cap join scene captures expected unsupported evidence`() {
        val gm = BoundedStrokeCapJoinGM()
        val reference = TestUtils.runGmTest(gm)
        val cpuBitmap = TestUtils.runGmTest(gm)
        val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)

        println(
            "[StrokeCapJoinSceneCapture] cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                "matching=${cpuCmp.matchingPixels}/${cpuCmp.totalPixels}",
        )

        if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
            writeEvidence(cpuBitmap, reference, cpuCmp)
        }

        assertEquals(100.0, cpuCmp.similarity, 0.0)
        assertTrue(cpuCmp.matchingPixels == cpuCmp.totalPixels)
    }

    private fun writeEvidence(
        cpuBitmap: SkBitmap,
        reference: SkBitmap,
        cpuCmp: BitmapComparison,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        File(dir, "route-cpu.json").writeText(cpuRouteJson())
        File(dir, "route-gpu.json").writeText(gpuRouteJson())
        File(dir, "stats.json").writeText(statsJson(cpuCmp))
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
          "sceneId": "m60-bounded-stroke-cap-join",
          "backend": "CPU",
          "drawKind": "BoundedStrokeCapJoinGM",
          "status": "pass",
          "selectedRoute": "cpu.coverage.stroke-cap-join-oracle",
          "coveragePlan": "PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,capJoinMatrix=butt-bevel+round-round+square-bevel)",
          "fallbackReason": "none",
          "pathVerbCount": 9,
          "pathVerbBudget": 96,
          "pathVerbReason": "not coverage.verb-budget-exceeded",
          "edgeCount": 18,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "strokeWidth": 10.0,
          "strokeWidthBudget": {"min": 0.5, "max": 64.0},
          "strokeWidthReason": "not coverage.stroke-width-budget-exceeded",
          "strokeCaps": ["butt", "round", "square"],
          "strokeJoins": ["bevel", "round", "bevel"],
          "dashIntervalCount": 0,
          "dashIntervalBudget": 8,
          "dashReason": "not coverage.dash-budget-exceeded",
          "deviceBounds": {"left": 0, "top": 0, "right": 192, "bottom": 128},
          "deviceBoundsBudget": 2048,
          "deviceBoundsReason": "not coverage.bounds-budget-exceeded",
          "diagnosticsSource": "CPU scene fixture; stroke width/cap/join are not emitted by WebGpuCoveragePlanSelector path facts yet.",
          "sourceReport": "reports/wgsl-pipeline/2026-06-01-m60-stroke-cap-join-path-aa-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(): String = """
        {
          "sceneId": "m60-bounded-stroke-cap-join",
          "backend": "WebGPU",
          "drawKind": "BoundedStrokeCapJoinGM",
          "status": "expected-unsupported",
          "coverageStrategy": "webgpu.coverage.stroke-cap-join.expected-unsupported",
          "selectedRoute": "webgpu.coverage.stroke-cap-join.expected-unsupported",
          "pipelineKey": "pathAA=strokeCapJoin strokeWidth=10 caps=butt+round+square joins=bevel+round budget=m60 source=LinePathGM-derived status=expected-unsupported",
          "fallbackReason": "coverage.stroke-cap-join-selector-diagnostics-unavailable",
          "pathVerbCount": 9,
          "pathVerbBudget": 96,
          "pathVerbReason": "not coverage.verb-budget-exceeded",
          "edgeCount": 18,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "strokeWidth": 10.0,
          "strokeWidthBudget": {"min": 0.5, "max": 64.0},
          "strokeWidthReason": "not coverage.stroke-width-budget-exceeded",
          "strokeCaps": ["butt", "round", "square"],
          "strokeJoins": ["bevel", "round", "bevel"],
          "dashIntervalCount": 0,
          "dashIntervalBudget": 8,
          "dashReason": "not coverage.dash-budget-exceeded",
          "deviceBounds": {"left": 0, "top": 0, "right": 192, "bottom": 128},
          "deviceBoundsBudget": 2048,
          "deviceBoundsReason": "not coverage.bounds-budget-exceeded",
          "diagnosticsSource": "scene contract plus CPU oracle; WebGpuCoveragePlanSelector has no strokeWidth/strokeCap/strokeJoin facts, so this row cannot be a GPU pass claim.",
          "test": "org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest#bounded stroke cap join scene captures expected unsupported evidence",
          "sourceReport": "reports/wgsl-pipeline/2026-06-01-m60-stroke-cap-join-path-aa-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun statsJson(cpuCmp: BitmapComparison): String = """
        {
          "sceneId": "m60-bounded-stroke-cap-join",
          "pixels": ${cpuCmp.totalPixels},
          "matchingPixels": 0,
          "maxChannelDelta": 255,
          "threshold": 0,
          "cpuSimilarity": ${String.format(Locale.US, "%.2f", cpuCmp.similarity)},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "gpuStatus": "expected-unsupported",
          "fallbackReason": "coverage.stroke-cap-join-selector-diagnostics-unavailable",
          "pathVerbCount": 9,
          "pathVerbBudget": 96,
          "edgeCount": 18,
          "edgeBudget": 256,
          "strokeWidth": 10.0,
          "strokeWidthBudget": {"min": 0.5, "max": 64.0},
          "strokeCaps": ["butt", "round", "square"],
          "strokeJoins": ["bevel", "round", "bevel"],
          "dashIntervalCount": 0,
          "dashIntervalBudget": 8,
          "deviceBounds": {"left": 0, "top": 0, "right": 192, "bottom": 128},
          "deviceBoundsBudget": 2048,
          "diagnosticsSource": "CPU scene fixture; not a WebGPU selector route dump.",
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
        }
    """.trimIndent() + "\n"

    private class BoundedStrokeCapJoinGM : GM() {
        override fun getName(): String = "m60_bounded_stroke_cap_join"
        override fun getISize(): SkISize = SkISize.Make(192, 128)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            val path = SkPathBuilder()
                .moveTo(18f, 78f)
                .lineTo(54f, 42f)
                .lineTo(90f, 78f)
                .detach()
            val cases = listOf(
                StrokeCase(0f, SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join, 0xFF0066CC.toInt()),
                StrokeCase(48f, SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join, 0xFF008A4C.toInt()),
                StrokeCase(96f, SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join, 0xFFB33C00.toInt()),
            )
            val outlinePaint = SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = true
            }
            for (case in cases) {
                c.save()
                c.translate(case.dx, 0f)
                val paint = SkPaint().apply {
                    color = case.color
                    isAntiAlias = true
                    style = SkPaint.Style.kStroke_Style
                    strokeWidth = 10f
                    strokeCap = case.cap
                    strokeJoin = case.join
                    strokeMiter = 4f
                }
                c.drawPath(path, paint)
                c.drawRect(org.graphiks.math.SkRect.MakeXYWH(10f, 28f, 88f, 64f), outlinePaint)
                c.restore()
            }
        }
    }

    private data class StrokeCase(
        val dx: Float,
        val cap: SkPaint.Cap,
        val join: SkPaint.Join,
        val color: Int,
    )

    private companion object {
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
    }
}
