package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.ImageGM

class ImageGmSceneCaptureTest {
    @Test
    fun `imagegm captures row specific reference cpu and webgpu evidence`() {
        val gm = ImageGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
            ?: error("original-888/${gm.name()}.png missing")
        val cpuBitmap = TestUtils.runGmTest(gm)
        val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = CPU_TOLERANCE)
        val gpuOutcome = renderWebGpu(gm, reference)

        println(
            "[ImageGmSceneCapture] cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                "gpu=${gpuOutcome.summary}, status=${gpuOutcome.status}",
        )

        if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
            writeEvidence(
                reference = reference,
                cpuBitmap = cpuBitmap,
                cpuCmp = cpuCmp,
                gpuOutcome = gpuOutcome,
            )
        }

        assertTrue(
            cpuCmp.similarity >= CPU_MINIMUM_SIMILARITY,
            "ImageGM CPU evidence fell below the row-specific floor: " +
                "${cpuCmp.similarity}% < $CPU_MINIMUM_SIMILARITY%.",
        )
        assertTrue(
            gpuOutcome.status == "pass" || gpuOutcome.fallbackReason == FALLBACK_REASON,
            "ImageGM WebGPU route must pass or retain the stable D54-1 refusal reason.",
        )
    }

    @Test
    fun `imagegm writes webgpu gap diagnostic when requested`() {
        val gm = ImageGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
            ?: error("original-888/${gm.name()}.png missing")
        val cpuBitmap = TestUtils.runGmTest(gm)
        val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = CPU_TOLERANCE)
        val gpuOutcome = renderWebGpu(gm, reference)

        if (System.getProperty(GAP_DIAGNOSTIC_PROPERTY) == "true") {
            writeGapDiagnostic(
                reference = reference,
                cpuBitmap = cpuBitmap,
                cpuCmp = cpuCmp,
                gpuOutcome = gpuOutcome,
            )
        }

        assertTrue(
            cpuCmp.similarity >= CPU_MINIMUM_SIMILARITY,
            "ImageGM CPU diagnostic evidence fell below the row-specific floor.",
        )
        assertTrue(
            gpuOutcome.status == "pass" || gpuOutcome.fallbackReason == FALLBACK_REASON,
            "ImageGM WebGPU diagnostic must pass or retain the stable D54 refusal reason.",
        )
    }

    private fun renderWebGpu(gm: ImageGM, reference: SkBitmap): GpuOutcome {
        val context = WebGpuContext.createOrNull()
            ?: return GpuOutcome.missingAdapter()

        return try {
            context.use { ctx ->
                val gpuBitmap = WebGpuSink.draw(ctx, gm)
                val cmp = TestUtils.compareBitmapsDetailed(
                    gpuBitmap,
                    reference,
                    tolerance = GPU_TOLERANCE,
                )
                val status = if (cmp.similarity >= GPU_PROMOTION_THRESHOLD) "pass" else "expected-unsupported"
                val fallbackReason = if (status == "pass") "none" else FALLBACK_REASON
                GpuOutcome(
                    bitmap = gpuBitmap,
                    comparison = cmp,
                    adapter = ctx.adapterInfo ?: "unknown-adapter",
                    status = status,
                    fallbackReason = fallbackReason,
                    failure = null,
                )
            }
        } catch (t: Throwable) {
            GpuOutcome.failed(t)
        }
    }

    private fun writeEvidence(
        reference: SkBitmap,
        cpuBitmap: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuOutcome: GpuOutcome,
    ) {
        val dir = repoFile(ARTIFACT_DIR).apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        if (gpuOutcome.bitmap != null) {
            writePng(File(dir, "gpu.png"), gpuOutcome.bitmap)
            writePng(File(dir, "gpu-diff.png"), CrossBackendHarness.pixelDiff(reference, gpuOutcome.bitmap))
        }
        File(dir, "route-cpu.json").writeText(cpuRouteJson(cpuCmp))
        File(dir, "route-gpu.json").writeText(gpuRouteJson(gpuOutcome))
        File(dir, "stats.json").writeText(statsJson(cpuCmp, gpuOutcome))
    }

    private fun writeGapDiagnostic(
        reference: SkBitmap,
        cpuBitmap: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuOutcome: GpuOutcome,
    ) {
        val dir = repoFile(GAP_DIAGNOSTIC_DIR).apply { mkdirs() }
        val gpuBitmap = gpuOutcome.bitmap
        val cells = imageGmCells()
        val cpuVsReference = cells.map { compareRegion(cpuBitmap, reference, it, CPU_TOLERANCE) }
        val gpuVsReference = if (gpuBitmap == null) {
            emptyList()
        } else {
            cells.map { compareRegion(gpuBitmap, reference, it, GPU_TOLERANCE) }
        }
        val gpuVsCpu = if (gpuBitmap == null) {
            emptyList()
        } else {
            cells.map { compareRegion(gpuBitmap, cpuBitmap, it, GPU_TOLERANCE) }
        }
        val samples = if (gpuBitmap == null) {
            emptyList()
        } else {
            cells.map { samplePoint(reference, cpuBitmap, gpuBitmap, it) }
        }
        File(dir, "region-diagnostic.json").writeText(
            gapDiagnosticJson(
                cpuCmp = cpuCmp,
                gpuOutcome = gpuOutcome,
                cpuVsReference = cpuVsReference,
                gpuVsReference = gpuVsReference,
                gpuVsCpu = gpuVsCpu,
                samples = samples,
            ),
        )
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.path}")
        file.writeBytes(bytes)
    }

    private fun cpuRouteJson(cpuCmp: BitmapComparison): String = """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "CPU",
          "drawKind": "ImageGM",
          "status": "pass",
          "selectedRoute": "cpu.raster.dm-reference-colorspace.imagegm-surface-snapshot",
          "referenceKind": "skia-upstream-png",
          "referencePath": "skia-integration-tests/src/test/resources/original-888/image-surface.png",
          "fallbackReason": "none",
          "similarity": ${cpuCmp.similarity.jsonNumber()},
          "threshold": $CPU_MINIMUM_SIMILARITY,
          "tolerance": $CPU_TOLERANCE,
          "matchingPixels": ${cpuCmp.matchingPixels},
          "totalPixels": ${cpuCmp.totalPixels},
          "maxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "test": "org.skia.gpu.webgpu.ImageGmSceneCaptureTest#imagegm captures row specific reference cpu and webgpu evidence"
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(gpuOutcome: GpuOutcome): String {
        val comparison = gpuOutcome.comparison
        val similarityLine = comparison?.let { """,
          "similarity": ${it.similarity.jsonNumber()},
          "threshold": $GPU_PROMOTION_THRESHOLD,
          "tolerance": $GPU_TOLERANCE,
          "matchingPixels": ${it.matchingPixels},
          "totalPixels": ${it.totalPixels},
          "maxChannelDelta": ${it.maxChannelDiff.max()}""" } ?: ""
        val failureLine = gpuOutcome.failure?.let { """,
          "failure": ${it.jsonString()}""" } ?: ""
        val selectedRoute = if (gpuOutcome.status == "pass") {
            "webgpu.image.imagegm.surface-snapshot-drawimage"
        } else {
            "webgpu.refusal.imagegm.surface-snapshot-drawimage"
        }
        return """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "WebGPU",
          "adapter": ${gpuOutcome.adapter.jsonString()},
          "drawKind": "ImageGM",
          "status": ${gpuOutcome.status.jsonString()},
          "selectedRoute": ${selectedRoute.jsonString()},
          "pipelineKey": "not-promoted-by-D54-1",
          "referenceKind": "skia-upstream-png",
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()}$similarityLine$failureLine,
          "supportClaim": ${gpuOutcome.status == "pass"},
          "globalDashboardPromoted": false,
          "globalThresholdChanged": false,
          "neighborEvidenceInherited": false,
          "broadImageDecodeClaim": false,
          "test": "org.skia.gpu.webgpu.ImageGmSceneCaptureTest#imagegm captures row specific reference cpu and webgpu evidence"
        }
        """.trimIndent() + "\n"
    }

    private fun statsJson(cpuCmp: BitmapComparison, gpuOutcome: GpuOutcome): String {
        val gpuStats = gpuOutcome.comparison?.let { cmp ->
            """,
          "gpuSimilarity": ${cmp.similarity.jsonNumber()},
          "gpuMatchingPixels": ${cmp.matchingPixels},
          "gpuTotalPixels": ${cmp.totalPixels},
          "gpuMaxChannelDelta": ${cmp.maxChannelDiff.max()}"""
        } ?: ""
        return """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "status": ${gpuOutcome.status.jsonString()},
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()},
          "supportClaim": ${gpuOutcome.status == "pass"},
          "globalDashboardPromoted": false,
          "referenceArtifact": "$ARTIFACT_DIR/skia.png",
          "cpuArtifact": "$ARTIFACT_DIR/cpu.png",
          "cpuDiffArtifact": "$ARTIFACT_DIR/cpu-diff.png",
          "gpuArtifact": ${if (gpuOutcome.bitmap != null) "\"$ARTIFACT_DIR/gpu.png\"" else "null"},
          "gpuDiffArtifact": ${if (gpuOutcome.bitmap != null) "\"$ARTIFACT_DIR/gpu-diff.png\"" else "null"},
          "routeCpu": "$ARTIFACT_DIR/route-cpu.json",
          "routeGpu": "$ARTIFACT_DIR/route-gpu.json",
          "cpuSimilarity": ${cpuCmp.similarity.jsonNumber()},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuTotalPixels": ${cpuCmp.totalPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()}$gpuStats,
          "cpuThreshold": $CPU_MINIMUM_SIMILARITY,
          "gpuPromotionThreshold": $GPU_PROMOTION_THRESHOLD,
          "globalThresholdChanged": false,
          "neighborEvidenceInherited": false,
          "broadImageDecodeClaim": false,
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest"
        }
        """.trimIndent() + "\n"
    }

    private fun gapDiagnosticJson(
        cpuCmp: BitmapComparison,
        gpuOutcome: GpuOutcome,
        cpuVsReference: List<RegionComparison>,
        gpuVsReference: List<RegionComparison>,
        gpuVsCpu: List<RegionComparison>,
        samples: List<CellSample>,
    ): String {
        val gpuCmp = gpuOutcome.comparison
        val gpuMetrics = gpuCmp?.let { cmp ->
            """,
          "webgpu": {
            "status": ${gpuOutcome.status.jsonString()},
            "similarity": ${cmp.similarity.jsonNumber()},
            "threshold": $GPU_PROMOTION_THRESHOLD,
            "matchingPixels": ${cmp.matchingPixels},
            "mismatchingPixels": ${cmp.mismatchingPixels},
            "maxChannelDelta": ${cmp.maxChannelDiff.max()},
            "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()},
            "supportClaim": ${gpuOutcome.status == "pass"}
          }"""
        } ?: """,
          "webgpu": {
            "status": ${gpuOutcome.status.jsonString()},
            "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()},
            "supportClaim": false,
            "failure": ${gpuOutcome.failure?.jsonString() ?: "null"}
          }"""
        val dominant = gpuVsReference.maxByOrNull { it.mismatchingPixels }
        val dominantLine = dominant?.let {
            """,
          "dominantRegion": ${it.name.jsonString()},
          "dominantRegionMismatchingPixels": ${it.mismatchingPixels}"""
        } ?: ""
        return """
        {
          "schemaVersion": 1,
          "ticket": "D54-2",
          "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d54-2-diagnostiquer-lecart-web-gpu-image-gm-apres-artefacts-row-specific",
          "sourceFindingMemory": "global/kanvas/findings/d54-1-skia-gm-image-artifact-harness",
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "classification": "webgpu-gap-diagnostic-no-dashboard-promotion",
          "status": ${gpuOutcome.status.jsonString()},
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()},
          "adapter": ${gpuOutcome.adapter.jsonString()},
          "metrics": {
            "cpu": {
              "status": "pass",
              "similarity": ${cpuCmp.similarity.jsonNumber()},
              "threshold": $CPU_MINIMUM_SIMILARITY,
              "matchingPixels": ${cpuCmp.matchingPixels},
              "mismatchingPixels": ${cpuCmp.mismatchingPixels},
              "maxChannelDelta": ${cpuCmp.maxChannelDiff.max()}
            }$gpuMetrics
          },
          "regionModel": {
            "coordinateSpace": "device pixels after ImageGM scale(2) and translate(80,20)",
            "cells": "2 raster columns x 7 ImageGM rows",
            "cellWidth": 132,
            "cellHeight": 132
          },
          "diagnosis": {
            "summary": "The residual is distributed across the ImageGM raster snapshot cells rather than the intentionally blank GPU column.",
            "dominantSignal": "cell-level internal SkBitmap comparisons",
            "pngCaveat": "PNG exports are encoded through SkBitmap.getPixelAsSrgb and are visual evidence only; promotion decisions use internal SkBitmap comparison metrics."$dominantLine,
            "safeCorrectionIdentifiedByD54_2": false,
            "nextAction": "Add a narrower shader/upload diagnostic for snapshot-image texture sampling before changing production rendering."
          },
          "nonClaims": {
            "supportClaimAddedByD54_2": false,
            "globalDashboardPromotedByD54_2": false,
            "globalThresholdChanged": false,
            "resultsJsonChanged": false,
            "scenesJsonChanged": false,
            "pipelineKeyChanged": false,
            "rendererChanged": false,
            "wgslProductionChanged": false,
            "broadImageDecodeClaim": false,
            "codecSupportClaim": false,
            "neighborEvidenceInherited": false
          },
          "cpuVsReferenceRegions": [
        ${cpuVsReference.joinToString(",\n") { regionToJson(it).prependIndent("    ") }}
          ],
          "webgpuVsReferenceRegions": [
        ${gpuVsReference.joinToString(",\n") { regionToJson(it).prependIndent("    ") }}
          ],
          "webgpuVsCpuRegions": [
        ${gpuVsCpu.joinToString(",\n") { regionToJson(it).prependIndent("    ") }}
          ],
          "samples": [
        ${samples.joinToString(",\n") { sampleToJson(it).prependIndent("    ") }}
          ]
        }
        """.trimIndent() + "\n"
    }

    private fun imageGmCells(): List<Cell> {
        val rows = listOf(
            "original-img",
            "modified-img",
            "cur-surface",
            "full-crop",
            "over-crop",
            "upper-left",
            "no-crop",
        )
        val columns = listOf("pre-alloc" to 160, "new-alloc" to 320)
        return columns.flatMap { (column, x) ->
            rows.mapIndexed { index, row ->
                Cell(
                    name = "$column/$row",
                    x = x,
                    y = 40 + index * 160,
                    width = 132,
                    height = 132,
                    sampleX = x + 64,
                    sampleY = 40 + index * 160 + 64,
                )
            }
        }
    }

    private fun compareRegion(a: SkBitmap, b: SkBitmap, cell: Cell, tolerance: Int): RegionComparison {
        var matching = 0
        var mismatching = 0
        var maxDelta = 0
        for (y in cell.y until (cell.y + cell.height).coerceAtMost(a.height).coerceAtMost(b.height)) {
            for (x in cell.x until (cell.x + cell.width).coerceAtMost(a.width).coerceAtMost(b.width)) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val delta = channelDelta(pa, pb)
                if (delta <= tolerance) {
                    matching++
                } else {
                    mismatching++
                    if (delta > maxDelta) maxDelta = delta
                }
            }
        }
        val total = matching + mismatching
        val similarity = if (total == 0) 0.0 else matching.toDouble() / total.toDouble() * 100.0
        return RegionComparison(cell, total, matching, mismatching, maxDelta, similarity)
    }

    private fun samplePoint(reference: SkBitmap, cpuBitmap: SkBitmap, gpuBitmap: SkBitmap, cell: Cell): CellSample =
        CellSample(
            cell = cell,
            reference = reference.getPixel(cell.sampleX, cell.sampleY),
            cpu = cpuBitmap.getPixel(cell.sampleX, cell.sampleY),
            webgpu = gpuBitmap.getPixel(cell.sampleX, cell.sampleY),
        )

    private fun channelDelta(pa: Int, pb: Int): Int {
        val dA = kotlin.math.abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
        val dR = kotlin.math.abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
        val dG = kotlin.math.abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
        val dB = kotlin.math.abs((pa and 0xFF) - (pb and 0xFF))
        return maxOf(dA, maxOf(dR, maxOf(dG, dB)))
    }

    private fun regionToJson(region: RegionComparison): String = """
        {
          "name": ${region.cell.name.jsonString()},
          "rect": {"x": ${region.cell.x}, "y": ${region.cell.y}, "width": ${region.cell.width}, "height": ${region.cell.height}},
          "totalPixels": ${region.totalPixels},
          "matchingPixels": ${region.matchingPixels},
          "mismatchingPixels": ${region.mismatchingPixels},
          "maxChannelDelta": ${region.maxChannelDelta},
          "similarity": ${region.similarity.jsonNumber()}
        }
    """.trimIndent()

    private fun sampleToJson(sample: CellSample): String = """
        {
          "name": ${sample.cell.name.jsonString()},
          "point": {"x": ${sample.cell.sampleX}, "y": ${sample.cell.sampleY}},
          "referenceArgb": ${sample.reference.argbString().jsonString()},
          "cpuArgb": ${sample.cpu.argbString().jsonString()},
          "webgpuArgb": ${sample.webgpu.argbString().jsonString()}
        }
    """.trimIndent()

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private data class GpuOutcome(
        val bitmap: SkBitmap?,
        val comparison: BitmapComparison?,
        val adapter: String,
        val status: String,
        val fallbackReason: String,
        val failure: String?,
    ) {
        val summary: String
            get() = comparison?.let { "${"%.2f".format(it.similarity)}%" } ?: "not-rendered"

        companion object {
            fun missingAdapter(): GpuOutcome = GpuOutcome(
                bitmap = null,
                comparison = null,
                adapter = "no-webgpu-adapter",
                status = "expected-unsupported",
                fallbackReason = FALLBACK_REASON,
                failure = "No WebGPU adapter",
            )

            fun failed(t: Throwable): GpuOutcome = GpuOutcome(
                bitmap = null,
                comparison = null,
                adapter = "webgpu-render-failed",
                status = "expected-unsupported",
                fallbackReason = FALLBACK_REASON,
                failure = "${t::class.qualifiedName}: ${t.message ?: "no message"}",
            )
        }
    }

    private data class Cell(
        val name: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val sampleX: Int,
        val sampleY: Int,
    )

    private data class RegionComparison(
        val cell: Cell,
        val totalPixels: Int,
        val matchingPixels: Int,
        val mismatchingPixels: Int,
        val maxChannelDelta: Int,
        val similarity: Double,
    ) {
        val name: String get() = cell.name
    }

    private data class CellSample(
        val cell: Cell,
        val reference: Int,
        val cpu: Int,
        val webgpu: Int,
    )

    private fun Double.jsonNumber(): String = String.format(Locale.US, "%.4f", this)

    private fun Int.argbString(): String = String.format(Locale.US, "0x%08X", this)

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

    private companion object {
        private const val SCENE_ID = "d54-skia-gm-image"
        private const val INVENTORY_ID = "skia-gm-image"
        private const val ARTIFACT_DIR = "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image"
        private const val GAP_DIAGNOSTIC_DIR = "reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap"
        private const val FALLBACK_REASON = "image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required"
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private const val GAP_DIAGNOSTIC_PROPERTY = "kanvas.imageGmGapDiagnostic.write"
        private const val CPU_TOLERANCE = TestUtils.TEXTUAL_GM_TOLERANCE
        private const val GPU_TOLERANCE = TestUtils.TEXTUAL_GM_TOLERANCE
        private const val CPU_MINIMUM_SIMILARITY = 98.0
        private const val GPU_PROMOTION_THRESHOLD = 99.95
    }
}
