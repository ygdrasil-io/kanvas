package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.GPUTextureFormat
import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.testing.TestUtils
import org.skia.tests.GM

class For267RoundCapJoinCoverageEquivalenceTest {
    @Test
    fun `FOR-267 round cap join boundary cells keep diagnostic until CPU GPU coverage equivalence is proven`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BoundedStrokeCapJoinGM()
            val reference = TestUtils.runGmTest(gm)
            val productionRefusal = assertThrows(IllegalStateException::class.java) {
                WebGpuSink.draw(ctx, gm)
            }
            val gpu = withExperimentalStrokeCapJoinRender {
                renderBitmap(ctx, gm)
            }
            val cells = CELL_SPECS.map { inspectCell(reference, gpu, it) }
            val probe = For267CoverageProbe(
                adapter = ctx.adapterInfo ?: "unknown-adapter",
                productionRefusal = productionRefusal.message ?: "unknown stroke cap/join refusal",
                cells = cells,
            )

            assertEquals("KEEP_DIAGNOSTIC", probe.supportDecision)
            assertEquals("REFUSED_BOUNDARY_COVERAGE_EQUIVALENCE_NOT_PROVEN", probe.supportDecisionReason)
            assertFalse(probe.correctionApplied)
            assertFalse(probe.safeBoundedCoverageCorrectionProven)
            assertTrue(probe.cells.any { it.cellDecision == "NOT_EQUIVALENT" })
            assertTrue(probe.cells.any { it.geometricClassification == "round-cap-boundary" })
            assertTrue(probe.productionRefusal.contains(FALLBACK_REASON))
            assertEquals(CROP_FALLBACK_REASON, probe.preservedUnsupportedReason)

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor267CoverageAuditJson(probe)
                writeFor267CoverageAuditReport(probe)
            }
        }
    }

    private fun renderBitmap(context: WebGpuContext, gm: GM): SkBitmap {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = true,
            targetColorSpaceBlend = true,
            intermediateFormat = GPUTextureFormat.RGBA16Float,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            return rgbaBytesToBitmap(device.flush(), size.width, size.height)
        }
    }

    private fun inspectCell(reference: SkBitmap, gpu: SkBitmap, spec: CellSpec): CellInspection {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-267 requires same-size CPU/WebGPU cell evidence"
        }
        val samples = mutableListOf<CellSample>()
        var matchingPixels = 0
        var maxDelta = 0
        var coverageExpectedSum = 0.0
        var coverageObservedSum = 0.0
        var maxCoverageDelta = 0.0
        val left = (spec.centerX - spec.radius).coerceAtLeast(0)
        val top = (spec.centerY - spec.radius).coerceAtLeast(0)
        val right = (spec.centerX + spec.radius).coerceAtMost(reference.width - 1)
        val bottom = (spec.centerY + spec.radius).coerceAtMost(reference.height - 1)
        for (y in top..bottom) {
            for (x in left..right) {
                val expected = rgbaAt(reference, x, y)
                val observed = rgbaAt(gpu, x, y)
                val delta = PixelDelta(x = x, y = y, reference = expected, gpu = observed)
                val expectedCoverage = coverageProxy(expected, spec.sourceRgba)
                val observedCoverage = coverageProxy(observed, spec.sourceRgba)
                val coverageDelta = observedCoverage - expectedCoverage
                if (delta.maxChannelDelta == 0) matchingPixels += 1
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
                coverageExpectedSum += expectedCoverage
                coverageObservedSum += observedCoverage
                maxCoverageDelta = maxOf(maxCoverageDelta, kotlin.math.abs(coverageDelta))
                if (delta.maxChannelDelta > 0 || (x == spec.centerX && y == spec.centerY)) {
                    samples += CellSample(
                        x = x,
                        y = y,
                        referenceRgba = expected,
                        gpuRgba = observed,
                        signedDeltaRgba = delta.signedDelta,
                        maxChannelDelta = delta.maxChannelDelta,
                        coverageExpected = expectedCoverage,
                        coverageObserved = observedCoverage,
                        coverageDelta = coverageDelta,
                    )
                }
            }
        }
        val totalPixels = (right - left + 1) * (bottom - top + 1)
        val representative = samples
            .sortedWith(compareByDescending<CellSample> { it.maxChannelDelta }.thenBy { it.y }.thenBy { it.x })
            .take(8)
        val averageExpected = coverageExpectedSum / totalPixels
        val averageObserved = coverageObservedSum / totalPixels
        val averageDelta = averageObserved - averageExpected
        val decision = when {
            maxDelta == 0 -> "EQUIVALENT_BYTE_EXACT"
            maxDelta <= 1 && kotlin.math.abs(averageDelta) <= 0.01 -> "EQUIVALENT_WITH_BYTE_ROUNDING"
            else -> "NOT_EQUIVALENT"
        }
        return CellInspection(
            id = spec.id,
            description = spec.description,
            centerX = spec.centerX,
            centerY = spec.centerY,
            radius = spec.radius,
            bounds = CellBounds(left, top, right, bottom),
            regionalClassification = spec.regionalClassification,
            geometricClassification = spec.geometricClassification,
            sourceRgba = spec.sourceRgba,
            totalPixels = totalPixels,
            matchingPixels = matchingPixels,
            mismatchPixels = totalPixels - matchingPixels,
            maxChannelDelta = maxDelta,
            averageCoverageExpected = averageExpected,
            averageCoverageObserved = averageObserved,
            averageCoverageDelta = averageDelta,
            maxCoverageDelta = maxCoverageDelta,
            representativeSamples = representative,
            cellDecision = decision,
            nextMissingCondition = MISSING_CONDITION.takeIf { decision == "NOT_EQUIVALENT" },
        )
    }

    private fun coverageProxy(rgba: IntArray, sourceRgba: IntArray): Double {
        val background = TARGET_WHITE_RGBA
        val estimates = mutableListOf<Double>()
        for (channel in 0 until 3) {
            val denom = (background[channel] - sourceRgba[channel]).toDouble()
            if (kotlin.math.abs(denom) > 1.0) {
                estimates += ((background[channel] - rgba[channel]) / denom).coerceIn(0.0, 1.0)
            }
        }
        return estimates.average()
    }

    private fun rgbaAt(bitmap: SkBitmap, x: Int, y: Int): IntArray {
        val pixel = bitmap.getPixel(x, y)
        return intArrayOf(
            (pixel ushr 16) and 0xFF,
            (pixel ushr 8) and 0xFF,
            pixel and 0xFF,
            (pixel ushr 24) and 0xFF,
        )
    }

    private fun rgbaBytesToBitmap(rgba: ByteArray, width: Int, height: Int): SkBitmap {
        require(rgba.size == width * height * 4) {
            "RGBA buffer size mismatch: expected ${width * height * 4} bytes, got ${rgba.size}"
        }
        val bitmap = SkBitmap(width, height, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until width * height) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }

    private fun <T> withExperimentalStrokeCapJoinRender(block: () -> T): T {
        val previous = System.getProperty(EXPERIMENTAL_RENDER_PROPERTY)
        System.setProperty(EXPERIMENTAL_RENDER_PROPERTY, "true")
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(EXPERIMENTAL_RENDER_PROPERTY)
            } else {
                System.setProperty(EXPERIMENTAL_RENDER_PROPERTY, previous)
            }
        }
    }

    private fun writeFor267CoverageAuditJson(probe: For267CoverageProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "Kanvas :kanvas-skia CPU GM oracle via TestUtils.runGmTest",
              "linear": "FOR-267",
              "parent": "FOR-241",
              "probe": "round-cap-join-boundary-cell-coverage-equivalence",
              "scene": {
                "sceneId": "m60-bounded-stroke-cap-join",
                "source": "BoundedStrokeCapJoinGM",
                "width": 192,
                "height": 128,
                "strokeWidth": 10.0,
                "capJoinMatrix": ["butt-bevel", "round-round", "square-bevel"]
              },
              "colorPolicy": {
                "applyColorspaceTransform": true,
                "targetColorSpaceBlend": true,
                "targetColorSpaceBlendGloballyEnabled": false,
                "defaultTargetColorSpaceBlend": false,
                "comparisonColorSpace": "DM_REFERENCE_COLOR_SPACE",
                "coverageProxy": "normalized byte progression from target white to the expected target-space source color; diagnostic proxy only, not a raw coverage plane"
              },
              "intermediateFormat": "RGBA16Float",
              "routeDiagnostics": {
                "diagnosticRoute": "webgpu.coverage.stroke-cap-join.experimental-render",
                "normalRoute": "webgpu.coverage.refuse",
                "fallbackReason": "$FALLBACK_REASON",
                "productionRefusal": ${probe.productionRefusal.jsonString()}
              },
              "newRendererProperty": "none",
              "defaultEnabled": false,
              "runtimeSnapshotsEnabled": false,
              "normalRenderingChanged": false,
              "normalShadersChanged": false,
              "normalThresholdsChanged": false,
              "cropPolicyChanged": false,
              "fallbackPolicyChanged": false,
              "intermediateFormatPolicyChanged": false,
              "targetColorSpaceBlendGloballyEnabled": false,
              "supportThreshold": $SUPPORT_THRESHOLD,
              "adapter": ${probe.adapter.jsonString()},
              "cellsInspected": [
            ${probe.cells.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "coverageStatistics": {
                "cellCount": ${probe.cellCount},
                "notEquivalentCells": ${probe.notEquivalentCells},
                "byteExactCells": ${probe.byteExactCells},
                "byteRoundingEquivalentCells": ${probe.byteRoundingEquivalentCells},
                "maxCellDelta": ${probe.maxCellDelta},
                "maxCoverageProxyDelta": ${probe.maxCoverageProxyDelta.format6()},
                "roundCapCellsObserved": ${probe.roundCapCellsObserved},
                "roundJoinCellsObserved": ${probe.roundJoinCellsObserved},
                "overlapHighDeltaCellObserved": ${probe.overlapHighDeltaCellObserved},
                "safeBoundedCoverageCorrectionProven": ${probe.safeBoundedCoverageCorrectionProven}
              },
              "representativeSamples": [
            ${probe.representativeSamples.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "supportDecision": "${probe.supportDecision}",
              "supportDecisionReason": "${probe.supportDecisionReason}",
              "boundedCoverageCorrectionStatus": "${probe.boundedCoverageCorrectionStatus}",
              "admissibleCorrection": "${probe.admissibleCorrection}",
              "nextMissingCondition": "${probe.nextMissingCondition}",
              "remainingBoundary": "${probe.remainingBoundary}",
              "finding": "${probe.finding}",
              "interpretation": "FOR-267 inspects representative targetColorSpaceBlend=true RGBA16Float boundary cells from m60-bounded-stroke-cap-join. The true round-join cell is byte-exact, but a true round-cap boundary cell and the FOR-266 high-delta overlap cell are not byte-equivalent, and the audit only observes byte-derived coverage proxies rather than raw CPU/GPU coverage planes.",
              "observationMethod": "test-only SkWebGpuDevice construction with kanvas.webgpu.strokeCapJoin.experimentalRender scoped to this test, targetColorSpaceBlend=true, intermediateFormat=RGBA16Float, and CPU oracle from :kanvas-skia GM execution; no renderer default, shader, threshold, Crop, fallback policy, quantization policy, or production stroke support changed",
              "correctionApplied": ${probe.correctionApplied},
              "preservedUnsupportedReason": "${probe.preservedUnsupportedReason}"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/round-cap-join-coverage-equivalence-for267",
            "reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "round-cap-join-coverage-equivalence-for267.json").writeText(contents)
        }
    }

    private fun writeFor267CoverageAuditReport(probe: For267CoverageProbe) {
        val report = buildString {
            appendLine("# FOR-267 Round Cap/Join Coverage Equivalence Audit")
            appendLine()
            appendLine("Date: 2026-06-03")
            appendLine()
            appendLine("Decision: `${probe.supportDecision}`")
            appendLine()
            appendLine("FOR-267 compares the `:kanvas-skia` CPU oracle and the test-only")
            appendLine("WebGPU stroke cap/join experimental route on representative boundary")
            appendLine("cells from `m60-bounded-stroke-cap-join`. It keeps")
            appendLine("`targetColorSpaceBlend=false` as the production default, uses normal")
            appendLine("`RGBA16Float` intermediate storage, leaves thresholds unchanged, and")
            appendLine("does not change Crop, quantization, fallback policy, or production")
            appendLine("stroke/cap/join support.")
            appendLine()
            appendLine("Preserved production refusal:")
            appendLine()
            appendLine("```text")
            appendLine(probe.productionRefusal)
            appendLine("```")
            appendLine()
            appendLine("Preserved Crop fallback reason: `${probe.preservedUnsupportedReason}`.")
            appendLine()
            appendLine("## Artifacts")
            appendLine()
            appendLine("- `reports/wgsl-pipeline/scenes/generated/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json`")
            appendLine()
            appendLine("## Cell Results")
            appendLine()
            appendLine("| Cell | Classification | Bounds | Matching | Max delta | Avg CPU coverage proxy | Avg GPU coverage proxy | Decision |")
            appendLine("|---|---|---|---:|---:|---:|---:|---|")
            for (cell in probe.cells) {
                appendLine(
                    "| `${cell.id}` | `${cell.geometricClassification}` | `${cell.bounds.reportValue()}` | " +
                        "${cell.matchingPixels}/${cell.totalPixels} | ${cell.maxChannelDelta} | " +
                        "${cell.averageCoverageExpected.format6()} | ${cell.averageCoverageObserved.format6()} | " +
                        "`${cell.cellDecision}` |",
                )
            }
            appendLine()
            appendLine("## Conclusion")
            appendLine()
            appendLine("Correction de couverture bornée: refusée pour ce ticket.")
            appendLine()
            appendLine(probe.admissibleCorrection)
            appendLine()
            appendLine("Missing condition: `${probe.nextMissingCondition}`.")
            appendLine()
            appendLine("Remaining boundary: `${probe.remainingBoundary}`.")
            appendLine()
            appendLine("## Validation")
            appendLine()
            appendLine("```text")
            appendLine("rtk python3 scripts/validate_for267_round_cap_join_coverage_equivalence.py")
            appendLine("rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-267*'")
            appendLine("rtk python3 scripts/validate_for266_stroke_cap_join_aa_residual.py")
            appendLine("rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py")
            appendLine("rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py")
            appendLine("rtk ./gradlew --no-daemon pipelineSceneDashboardGate")
            appendLine("rtk git diff --check")
            appendLine("```")
        }
        repoFile("reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md")
            .writeText(report)
    }

    private fun CellInspection.toJson(indent: String): String =
        """
        {
          "id": ${id.jsonString()},
          "description": ${description.jsonString()},
          "center": {"x": $centerX, "y": $centerY},
          "radius": $radius,
          "bounds": ${bounds.toJson()},
          "regionalClassification": ${regionalClassification.jsonString()},
          "geometricClassification": ${geometricClassification.jsonString()},
          "sourceRgba": ${jsonArray(sourceRgba)},
          "totalPixels": $totalPixels,
          "matchingPixels": $matchingPixels,
          "mismatchPixels": $mismatchPixels,
          "maxChannelDelta": $maxChannelDelta,
          "averageCoverageExpected": ${averageCoverageExpected.format6()},
          "averageCoverageObserved": ${averageCoverageObserved.format6()},
          "averageCoverageDelta": ${averageCoverageDelta.format6()},
          "maxCoverageDelta": ${maxCoverageDelta.format6()},
          "representativeSamples": [
        ${representativeSamples.joinToString(",\n") { it.toJson(indent = "$indent    ") }}
          ],
          "cellDecision": ${cellDecision.jsonString()},
          "nextMissingCondition": ${nextMissingCondition?.jsonString() ?: "null"}
        }
        """.trimIndent().prependIndent(indent)

    private fun CellSample.toJson(indent: String): String =
        """
        {
          "x": $x,
          "y": $y,
          "referenceRgba": ${jsonArray(referenceRgba)},
          "gpuRgba": ${jsonArray(gpuRgba)},
          "signedDeltaRgba": ${jsonArray(signedDeltaRgba)},
          "maxChannelDelta": $maxChannelDelta,
          "coverageExpected": ${coverageExpected.format6()},
          "coverageObserved": ${coverageObserved.format6()},
          "coverageDelta": ${coverageDelta.format6()}
        }
        """.trimIndent().prependIndent(indent)

    private fun CellBounds.toJson(): String =
        """{"left": $left, "top": $top, "right": $right, "bottom": $bottom}"""

    private fun CellBounds.reportValue(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun Double.format6(): String =
        String.format(Locale.US, "%.6f", this).trimEnd('0').trimEnd('.')

    private fun jsonArray(values: IntArray): String = values.joinToString(prefix = "[", postfix = "]")

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
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
                c.drawRect(SkRect.MakeXYWH(10f, 28f, 88f, 64f), outlinePaint)
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

    private data class CellSpec(
        val id: String,
        val description: String,
        val centerX: Int,
        val centerY: Int,
        val radius: Int,
        val regionalClassification: String,
        val geometricClassification: String,
        val sourceRgba: IntArray,
    )

    private data class CellBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private data class PixelDelta(
        val x: Int,
        val y: Int,
        val reference: IntArray,
        val gpu: IntArray,
    ) {
        val signedDelta: IntArray = IntArray(4) { channel -> gpu[channel] - reference[channel] }
        val maxChannelDelta: Int = signedDelta.maxOf { kotlin.math.abs(it) }
    }

    private data class CellSample(
        val x: Int,
        val y: Int,
        val referenceRgba: IntArray,
        val gpuRgba: IntArray,
        val signedDeltaRgba: IntArray,
        val maxChannelDelta: Int,
        val coverageExpected: Double,
        val coverageObserved: Double,
        val coverageDelta: Double,
    )

    private data class CellInspection(
        val id: String,
        val description: String,
        val centerX: Int,
        val centerY: Int,
        val radius: Int,
        val bounds: CellBounds,
        val regionalClassification: String,
        val geometricClassification: String,
        val sourceRgba: IntArray,
        val totalPixels: Int,
        val matchingPixels: Int,
        val mismatchPixels: Int,
        val maxChannelDelta: Int,
        val averageCoverageExpected: Double,
        val averageCoverageObserved: Double,
        val averageCoverageDelta: Double,
        val maxCoverageDelta: Double,
        val representativeSamples: List<CellSample>,
        val cellDecision: String,
        val nextMissingCondition: String?,
    )

    private data class For267CoverageProbe(
        val adapter: String,
        val productionRefusal: String,
        val cells: List<CellInspection>,
    ) {
        val cellCount: Int = cells.size
        val notEquivalentCells: Int = cells.count { it.cellDecision == "NOT_EQUIVALENT" }
        val byteExactCells: Int = cells.count { it.cellDecision == "EQUIVALENT_BYTE_EXACT" }
        val byteRoundingEquivalentCells: Int = cells.count { it.cellDecision == "EQUIVALENT_WITH_BYTE_ROUNDING" }
        val maxCellDelta: Int = cells.maxOf { it.maxChannelDelta }
        val maxCoverageProxyDelta: Double = cells.maxOf { kotlin.math.abs(it.averageCoverageDelta) }
        val roundCapCellsObserved: Boolean = cells.any { it.geometricClassification == "round-cap-boundary" }
        val roundJoinCellsObserved: Boolean = cells.any { it.geometricClassification == "round-join-boundary" }
        val overlapHighDeltaCellObserved: Boolean =
            cells.any { it.geometricClassification == "round-region-overlap-with-butt-cap-boundary" }
        val safeBoundedCoverageCorrectionProven: Boolean = false
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val supportDecisionReason: String = "REFUSED_BOUNDARY_COVERAGE_EQUIVALENCE_NOT_PROVEN"
        val boundedCoverageCorrectionStatus: String = "REFUSED"
        val admissibleCorrection: String =
            "none_applied: FOR-267 observes byte-derived coverage proxies for bounded round cap/join cells, but at least one true round-cap boundary cell is not byte-equivalent and the FOR-266 high-delta cell is geometrically ambiguous; raw CPU/GPU coverage-plane equivalence is not proven"
        val nextMissingCondition: String = MISSING_CONDITION
        val remainingBoundary: String = "coverage.stroke-cap-join-aa-residual"
        val finding: String =
            "round_cap_join_boundary_cell_equivalence_not_proven_keep_stroke_cap_join_refusal"
        val correctionApplied: Boolean = false
        val preservedUnsupportedReason: String = CROP_FALLBACK_REASON
        val representativeSamples: List<CellSample> = cells
            .flatMap { it.representativeSamples }
            .sortedWith(compareByDescending<CellSample> { it.maxChannelDelta }.thenBy { it.y }.thenBy { it.x })
            .take(12)
    }

    private companion object {
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
        const val EXPERIMENTAL_RENDER_PROPERTY: String = "kanvas.webgpu.strokeCapJoin.experimentalRender"
        const val SUPPORT_THRESHOLD: Double = 99.95
        const val FALLBACK_REASON: String = "coverage.stroke-cap-join-visual-parity-below-threshold"
        const val CROP_FALLBACK_REASON: String = "image-filter.crop-input-nonnull-prepass-required"
        const val MISSING_CONDITION: String =
            "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells"
        val TARGET_WHITE_RGBA: IntArray = intArrayOf(255, 255, 255, 255)
        val TARGET_BLUE_RGBA: IntArray = intArrayOf(59, 86, 189, 255)
        val TARGET_GREEN_RGBA: IntArray = intArrayOf(68, 121, 68, 255)
        val CELL_SPECS: List<CellSpec> = listOf(
            CellSpec(
                id = "round-left-cap-boundary",
                description = "True round-cap lower-left antialias boundary around the shifted round/round stroke.",
                centerX = 66,
                centerY = 78,
                radius = 4,
                regionalClassification = "round-round",
                geometricClassification = "round-cap-boundary",
                sourceRgba = TARGET_GREEN_RGBA,
            ),
            CellSpec(
                id = "round-join-apex",
                description = "True round-join apex cell around the shifted round/round stroke vertex.",
                centerX = 102,
                centerY = 42,
                radius = 3,
                regionalClassification = "round-round",
                geometricClassification = "round-join-boundary",
                sourceRgba = TARGET_GREEN_RGBA,
            ),
            CellSpec(
                id = "round-right-cap-boundary",
                description = "True round-cap right antialias boundary around the shifted round/round stroke.",
                centerX = 138,
                centerY = 78,
                radius = 4,
                regionalClassification = "round-round",
                geometricClassification = "round-cap-boundary",
                sourceRgba = TARGET_GREEN_RGBA,
            ),
            CellSpec(
                id = "for266-high-delta-round-bin-overlap",
                description = "FOR-266 high-delta cell inside the round-round x-band; byte colors identify overlap with the neighboring butt-cap boundary rather than a clean round-cap plane.",
                centerX = 90,
                centerY = 77,
                radius = 3,
                regionalClassification = "round-round",
                geometricClassification = "round-region-overlap-with-butt-cap-boundary",
                sourceRgba = TARGET_BLUE_RGBA,
            ),
        )
    }
}
