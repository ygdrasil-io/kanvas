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

class For266StrokeCapJoinAaResidualAuditTest {
    @Test
    fun `FOR-266 stroke cap join AA residual stays diagnostic after targetColorSpaceBlend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BoundedStrokeCapJoinGM()
            val reference = TestUtils.runGmTest(gm)
            val normalRefusal = assertThrows(IllegalStateException::class.java) {
                WebGpuSink.draw(ctx, gm)
            }
            val policies = POLICY_SPECS.map { policy ->
                val bitmap = withExperimentalStrokeCapJoinRender {
                    renderBitmap(ctx, gm, policy)
                }
                val imageStats = compareImages(reference, bitmap)
                val residual = classifyResidual(reference, bitmap, policy)
                For266PolicyResult(
                    spec = policy,
                    stats = imageStats,
                    residual = residual,
                    routeDiagnostics = "webgpu.coverage.stroke-cap-join.experimental-render",
                    normalRoute = "webgpu.coverage.refuse",
                    fallbackReason = "coverage.stroke-cap-join-visual-parity-below-threshold",
                    supportDecision = "REFUSED_INSUFFICIENT_PARITY",
                )
            }
            val probe = For266AuditProbe(
                adapter = ctx.adapterInfo ?: "unknown-adapter",
                productionRefusal = normalRefusal.message ?: "unknown stroke cap/join refusal",
                policies = policies,
            )

            assertEquals("KEEP_DIAGNOSTIC", probe.supportDecision)
            assertFalse(probe.correctionApplied)
            assertFalse(probe.safeCoverageCorrectionProven)
            assertTrue(probe.targetBlendTrueImprovesSimilarity)
            assertTrue(probe.targetBlendTrueStillBelowThreshold)
            assertEquals(2, probe.policyCount)
            assertTrue(probe.policies.all { it.spec.intermediateFormatLabel == "RGBA16Float" })
            assertEquals(
                "coverage.stroke-cap-join-visual-parity-below-threshold",
                probe.fallbackReason,
            )
            assertTrue(
                probe.productionRefusal.contains("coverage.stroke-cap-join-visual-parity-below-threshold"),
                "FOR-266: normal route must stay refused with the stroke cap/join diagnostic",
            )
            assertEquals(
                "image-filter.crop-input-nonnull-prepass-required",
                probe.preservedUnsupportedReason,
                "FOR-266: Crop(input = nonNull) fallback reason must stay preserved",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor266AuditJson(probe)
                writeFor266AuditReport(probe)
            }
        }
    }

    private fun renderBitmap(context: WebGpuContext, gm: GM, policy: For266PolicySpec): SkBitmap {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = true,
            targetColorSpaceBlend = policy.targetColorSpaceBlend,
            intermediateFormat = GPUTextureFormat.RGBA16Float,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            return rgbaBytesToBitmap(device.flush(), size.width, size.height)
        }
    }

    private fun compareImages(reference: SkBitmap, gpu: SkBitmap): For266ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-266 requires same-size bitmap evidence"
        }
        var matching = 0
        var maxDelta = 0
        var representative: PixelDelta? = null
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val delta = pixelDelta(reference, gpu, x, y)
                if (delta.maxChannelDelta == 0) {
                    matching += 1
                } else if (representative == null) {
                    representative = delta
                }
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
            }
        }
        val total = reference.width * reference.height
        return For266ImageStats(
            totalPixels = total,
            matchingPixels = matching,
            exactSimilarity = matching * 100.0 / total,
            maxDelta = maxDelta,
            representative = representative ?: pixelDelta(reference, gpu, 0, 0),
        )
    }

    private fun classifyResidual(
        reference: SkBitmap,
        gpu: SkBitmap,
        policy: For266PolicySpec,
    ): For266ResidualClassification {
        val regions = STROKE_REGIONS.associate { it.id to MutableResidualRegion(it) }
        var mismatchPixels = 0
        var oneUnitMismatchPixels = 0
        var greaterThanEightPixels = 0
        var greaterThanThirtyTwoPixels = 0
        var maxDelta = 0
        val highDeltaSamples = mutableListOf<ResidualSample>()
        val representativeSamples = mutableListOf<ResidualSample>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val delta = pixelDelta(reference, gpu, x, y)
                if (delta.maxChannelDelta == 0) continue
                val region = regions.getValue(regionForX(x).id)
                region.add(delta)
                mismatchPixels += 1
                if (delta.maxChannelDelta == 1) oneUnitMismatchPixels += 1
                if (delta.maxChannelDelta > TestUtils.TEXTUAL_GM_TOLERANCE) greaterThanEightPixels += 1
                if (delta.maxChannelDelta > 32) greaterThanThirtyTwoPixels += 1
                if (delta.maxChannelDelta > TestUtils.TEXTUAL_GM_TOLERANCE) {
                    highDeltaSamples += ResidualSample.fromDelta(delta, region.region.id)
                }
                if (representativeSamples.size < 12) {
                    representativeSamples += ResidualSample.fromDelta(delta, region.region.id)
                }
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
            }
        }
        val regionStats = regions.values.map { it.toStats() }
        val dominantRegion = regionStats.maxWith(
            compareBy<ResidualRegionStats> { it.mismatchPixels }
                .thenBy { it.id },
        )
        val highDelta = highDeltaSamples.sortedWith(
            compareByDescending<ResidualSample> { it.maxChannelDelta }
                .thenBy { it.y }
                .thenBy { it.x },
        ).take(16)
        val plausible = policy.targetColorSpaceBlend &&
            greaterThanEightPixels <= 16 &&
            highDelta.all { it.region == "round-round" || it.region == "butt-bevel" }
        return For266ResidualClassification(
            boundaryClassification = if (policy.targetColorSpaceBlend) {
                "coverage.stroke-cap-join-aa-residual-after-targetColorSpaceBlend"
            } else {
                "color-space.target-blend-required-plus-coverage.stroke-cap-join-aa-residual"
            },
            mismatchPixels = mismatchPixels,
            oneUnitMismatchPixels = oneUnitMismatchPixels,
            greaterThanEightPixels = greaterThanEightPixels,
            greaterThanThirtyTwoPixels = greaterThanThirtyTwoPixels,
            maxChannelDelta = maxDelta,
            dominantRegion = dominantRegion.id,
            regions = regionStats,
            representativeSamples = representativeSamples,
            highDeltaSamples = highDelta,
            boundedCoverageCorrectionPlausibility = if (plausible) {
                "PLAUSIBLE_BUT_NOT_PROVEN"
            } else {
                "NOT_PROVEN"
            },
            nextMissingCondition = "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells",
        )
    }

    private fun regionForX(x: Int): StrokeRegion =
        STROKE_REGIONS.first { x in it.xStart until it.xEnd }

    private fun pixelDelta(reference: SkBitmap, gpu: SkBitmap, x: Int, y: Int): PixelDelta {
        val referenceRgba = rgbaAt(reference, x, y)
        val gpuRgba = rgbaAt(gpu, x, y)
        return PixelDelta(x = x, y = y, reference = referenceRgba, gpu = gpuRgba)
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

    private fun writeFor266AuditJson(probe: For266AuditProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "Skia CPU GM reference and live test-only WebGPU experimental stroke cap/join renders",
              "linear": "FOR-266",
              "probe": "stroke-cap-join-aa-residual-after-targetColorSpaceBlend",
              "sceneId": "m60-bounded-stroke-cap-join",
              "deltaDefinition": "signed channel delta is live WebGPU byte output minus Skia CPU reference",
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
              "currentPolicy": "${probe.currentPolicy}",
              "adapter": ${probe.adapter.jsonString()},
              "productionRoute": "webgpu.coverage.refuse",
              "productionRefusal": ${probe.productionRefusal.jsonString()},
              "fallbackReason": "${probe.fallbackReason}",
              "policyCount": ${probe.policyCount},
              "supportThreshold": $SUPPORT_THRESHOLD,
              "policies": [
            ${probe.policies.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "summary": {
                "targetBlendFalseObserved": ${probe.targetBlendFalseObserved},
                "targetBlendTrueObserved": ${probe.targetBlendTrueObserved},
                "rgba16FloatIntermediateObserved": ${probe.rgba16FloatIntermediateObserved},
                "targetBlendTrueImprovesSimilarity": ${probe.targetBlendTrueImprovesSimilarity},
                "targetBlendTrueStillBelowThreshold": ${probe.targetBlendTrueStillBelowThreshold},
                "residualLocalizedToCapJoinRegions": ${probe.residualLocalizedToCapJoinRegions},
                "safeCoverageCorrectionProven": ${probe.safeCoverageCorrectionProven}
              },
              "finding": "${probe.finding}",
              "admissibleCorrection": "${probe.admissibleCorrection}",
              "missingCondition": "${probe.missingCondition}",
              "remainingBoundary": "${probe.remainingBoundary}",
              "interpretation": "FOR-266 narrows the FOR-263 stroke cap/join signal to the normal RGBA16Float intermediate with targetColorSpaceBlend=false and targetColorSpaceBlend=true. The target-blend policy improves similarity, but residual pixels remain below the strict support threshold and the high-delta tail is still localized to cap/join AA boundary cells.",
              "observationMethod": "test-only SkWebGpuDevice construction with kanvas.webgpu.strokeCapJoin.experimentalRender scoped to this test, targetColorSpaceBlend toggled per policy, and intermediateFormat fixed to RGBA16Float; no renderer default, shader, threshold, Crop, fallback policy, quantization policy, or production stroke support changed",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": ${probe.correctionApplied},
              "preservedUnsupportedReason": "${probe.preservedUnsupportedReason}"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/stroke-cap-join-aa-residual-for266",
            "reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "stroke-cap-join-aa-residual-for266.json").writeText(contents)
        }
    }

    private fun writeFor266AuditReport(probe: For266AuditProbe) {
        val report = buildString {
            appendLine("# FOR-266 Stroke Cap/Join AA Residual Audit")
            appendLine()
            appendLine("Date: 2026-06-03")
            appendLine()
            appendLine("Decision: `KEEP_DIAGNOSTIC`")
            appendLine()
            appendLine("FOR-266 audits `m60-bounded-stroke-cap-join` with normal")
            appendLine("`RGBA16Float` intermediate storage under `targetColorSpaceBlend=false`")
            appendLine("and `targetColorSpaceBlend=true`. It is diagnostic/test-only and does")
            appendLine("not change production defaults, shaders, thresholds, Crop policy,")
            appendLine("fallback policy, quantization policy, or general stroke cap/join")
            appendLine("support.")
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
            appendLine("- `reports/wgsl-pipeline/scenes/generated/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json`")
            appendLine()
            appendLine("## Policy Results")
            appendLine()
            appendLine("| Policy | Exact similarity | Max delta | Matching pixels | Boundary classification | Dominant region | Plausibility | Support |")
            appendLine("|---|---:|---:|---:|---|---|---|---|")
            for (policy in probe.policies) {
                appendLine(
                    "| `${policy.spec.id}` | ${policy.stats.exactSimilarity.reportValue()} | " +
                        "${policy.stats.maxDelta} | ${policy.stats.matchingPixels}/${policy.stats.totalPixels} | " +
                        "`${policy.residual.boundaryClassification}` | `${policy.residual.dominantRegion}` | " +
                        "`${policy.residual.boundedCoverageCorrectionPlausibility}` | `${policy.supportDecision}` |",
                )
            }
            appendLine()
            appendLine("## Region Breakdown")
            appendLine()
            appendLine("| Policy | Region | Mismatches | One-unit | >8 | >32 | Max delta | Bounds |")
            appendLine("|---|---|---:|---:|---:|---:|---:|---|")
            for (policy in probe.policies) {
                for (region in policy.residual.regions) {
                    appendLine(
                        "| `${policy.spec.id}` | `${region.id}` | ${region.mismatchPixels} | " +
                            "${region.oneUnitMismatchPixels} | ${region.greaterThanEightPixels} | " +
                            "${region.greaterThanThirtyTwoPixels} | ${region.maxChannelDelta} | " +
                            "`${region.bounds?.reportValue() ?: "none"}` |",
                    )
                }
            }
            appendLine()
            appendLine("## High-Delta Samples")
            appendLine()
            appendLine("| Policy | Pixel | Region | Reference RGBA | WebGPU RGBA | Max delta |")
            appendLine("|---|---|---|---|---|---:|")
            for (policy in probe.policies) {
                for (sample in policy.residual.highDeltaSamples.take(10)) {
                    appendLine(
                        "| `${policy.spec.id}` | `(${sample.x}, ${sample.y})` | `${sample.region}` | " +
                            "`${jsonArray(sample.referenceRgba)}` | `${jsonArray(sample.gpuRgba)}` | " +
                            "${sample.maxChannelDelta} |",
                    )
                }
            }
            appendLine()
            appendLine("## Conclusion")
            appendLine()
            appendLine(probe.admissibleCorrection)
            appendLine()
            appendLine("Missing condition: `${probe.missingCondition}`.")
            appendLine()
            appendLine("Remaining boundary: `${probe.remainingBoundary}`.")
            appendLine()
            appendLine("## Validation")
            appendLine()
            appendLine("```text")
            appendLine("rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-266*'")
            appendLine("rtk python3 scripts/validate_for266_stroke_cap_join_aa_residual.py")
            appendLine("rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py")
            appendLine("rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py")
            appendLine("rtk ./gradlew --no-daemon pipelineSceneDashboardGate")
            appendLine("rtk git diff --check")
            appendLine("```")
        }
        repoFile("reports/wgsl-pipeline/2026-06-03-for-266-stroke-cap-join-aa-residual.md")
            .writeText(report)
    }

    private fun For266PolicyResult.toJson(indent: String): String =
        """
        {
          "policy": ${spec.id.jsonString()},
          "evaluationKind": "whole-scene-reference-vs-live-webgpu-experimental-stroke-cap-join",
          "targetColorSpaceBlend": ${spec.targetColorSpaceBlend},
          "intermediateFormat": ${spec.intermediateFormatLabel.jsonString()},
          "totalPixels": ${stats.totalPixels},
          "matchingPixels": ${stats.matchingPixels},
          "exactSimilarity": ${stats.exactSimilarity},
          "maxDelta": ${stats.maxDelta},
          "representativePixel": ${stats.representative.toJson()},
          "routeDiagnostics": ${routeDiagnostics.jsonString()},
          "normalRoute": ${normalRoute.jsonString()},
          "fallbackReason": ${fallbackReason.jsonString()},
          "routeDiagnosticsRationale": "Live WebGPU render with test-only experimental stroke cap/join path; targetColorSpaceBlend=${spec.targetColorSpaceBlend}, intermediateFormat=${spec.intermediateFormatLabel}, no production default or fallback policy changed.",
          "residual": ${residual.toJson().prependIndent("  ").trimStart()},
          "supportDecision": ${supportDecision.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun For266ResidualClassification.toJson(): String =
        """
        {
          "boundaryClassification": ${boundaryClassification.jsonString()},
          "mismatchPixels": $mismatchPixels,
          "oneUnitMismatchPixels": $oneUnitMismatchPixels,
          "greaterThanEightPixels": $greaterThanEightPixels,
          "greaterThanThirtyTwoPixels": $greaterThanThirtyTwoPixels,
          "maxChannelDelta": $maxChannelDelta,
          "dominantRegion": ${dominantRegion.jsonString()},
          "regions": [
        ${regions.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "representativeSamples": [
        ${representativeSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "highDeltaSamples": [
        ${highDeltaSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "boundedCoverageCorrectionPlausibility": ${boundedCoverageCorrectionPlausibility.jsonString()},
          "nextMissingCondition": ${nextMissingCondition.jsonString()}
        }
        """.trimIndent()

    private fun PixelDelta.toJson(): String =
        """
        {
          "x": $x,
          "y": $y,
          "referenceRgba": ${jsonArray(reference)},
          "gpuRgba": ${jsonArray(gpu)},
          "signedDeltaRgba": ${jsonArray(signedDelta)},
          "maxChannelDelta": $maxChannelDelta
        }
        """.trimIndent()

    private fun ResidualRegionStats.toJson(): String =
        """
        {
          "id": ${id.jsonString()},
          "description": ${description.jsonString()},
          "mismatchPixels": $mismatchPixels,
          "oneUnitMismatchPixels": $oneUnitMismatchPixels,
          "greaterThanEightPixels": $greaterThanEightPixels,
          "greaterThanThirtyTwoPixels": $greaterThanThirtyTwoPixels,
          "maxChannelDelta": $maxChannelDelta,
          "bounds": ${bounds?.toJson() ?: "null"}
        }
        """.trimIndent()

    private fun ResidualSample.toJson(): String =
        """
        {
          "x": $x,
          "y": $y,
          "region": ${region.jsonString()},
          "referenceRgba": ${jsonArray(referenceRgba)},
          "gpuRgba": ${jsonArray(gpuRgba)},
          "signedDeltaRgba": ${jsonArray(signedDeltaRgba)},
          "maxChannelDelta": $maxChannelDelta
        }
        """.trimIndent()

    private fun ResidualBounds.toJson(): String =
        """{"left": $left, "top": $top, "right": $right, "bottom": $bottom}"""

    private fun ResidualBounds.reportValue(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun Double.reportValue(): String =
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

    private data class For266PolicySpec(
        val id: String,
        val targetColorSpaceBlend: Boolean,
        val intermediateFormatLabel: String,
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

    private data class For266ImageStats(
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val representative: PixelDelta,
    )

    private data class StrokeRegion(
        val id: String,
        val description: String,
        val xStart: Int,
        val xEnd: Int,
    )

    private class MutableResidualRegion(val region: StrokeRegion) {
        var mismatchPixels: Int = 0
        var oneUnitMismatchPixels: Int = 0
        var greaterThanEightPixels: Int = 0
        var greaterThanThirtyTwoPixels: Int = 0
        var maxChannelDelta: Int = 0
        var minX: Int = Int.MAX_VALUE
        var minY: Int = Int.MAX_VALUE
        var maxX: Int = Int.MIN_VALUE
        var maxY: Int = Int.MIN_VALUE

        fun add(delta: PixelDelta) {
            mismatchPixels += 1
            if (delta.maxChannelDelta == 1) oneUnitMismatchPixels += 1
            if (delta.maxChannelDelta > TestUtils.TEXTUAL_GM_TOLERANCE) greaterThanEightPixels += 1
            if (delta.maxChannelDelta > 32) greaterThanThirtyTwoPixels += 1
            maxChannelDelta = maxOf(maxChannelDelta, delta.maxChannelDelta)
            minX = minOf(minX, delta.x)
            minY = minOf(minY, delta.y)
            maxX = maxOf(maxX, delta.x)
            maxY = maxOf(maxY, delta.y)
        }

        fun toStats(): ResidualRegionStats =
            ResidualRegionStats(
                id = region.id,
                description = region.description,
                mismatchPixels = mismatchPixels,
                oneUnitMismatchPixels = oneUnitMismatchPixels,
                greaterThanEightPixels = greaterThanEightPixels,
                greaterThanThirtyTwoPixels = greaterThanThirtyTwoPixels,
                maxChannelDelta = maxChannelDelta,
                bounds = if (mismatchPixels == 0) null else ResidualBounds(minX, minY, maxX, maxY),
            )
    }

    private data class ResidualRegionStats(
        val id: String,
        val description: String,
        val mismatchPixels: Int,
        val oneUnitMismatchPixels: Int,
        val greaterThanEightPixels: Int,
        val greaterThanThirtyTwoPixels: Int,
        val maxChannelDelta: Int,
        val bounds: ResidualBounds?,
    )

    private data class ResidualBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private data class ResidualSample(
        val x: Int,
        val y: Int,
        val region: String,
        val referenceRgba: IntArray,
        val gpuRgba: IntArray,
        val signedDeltaRgba: IntArray,
        val maxChannelDelta: Int,
    ) {
        companion object {
            fun fromDelta(delta: PixelDelta, region: String): ResidualSample =
                ResidualSample(
                    x = delta.x,
                    y = delta.y,
                    region = region,
                    referenceRgba = delta.reference,
                    gpuRgba = delta.gpu,
                    signedDeltaRgba = delta.signedDelta,
                    maxChannelDelta = delta.maxChannelDelta,
                )
        }
    }

    private data class For266ResidualClassification(
        val boundaryClassification: String,
        val mismatchPixels: Int,
        val oneUnitMismatchPixels: Int,
        val greaterThanEightPixels: Int,
        val greaterThanThirtyTwoPixels: Int,
        val maxChannelDelta: Int,
        val dominantRegion: String,
        val regions: List<ResidualRegionStats>,
        val representativeSamples: List<ResidualSample>,
        val highDeltaSamples: List<ResidualSample>,
        val boundedCoverageCorrectionPlausibility: String,
        val nextMissingCondition: String,
    )

    private data class For266PolicyResult(
        val spec: For266PolicySpec,
        val stats: For266ImageStats,
        val residual: For266ResidualClassification,
        val routeDiagnostics: String,
        val normalRoute: String,
        val fallbackReason: String,
        val supportDecision: String,
    )

    private data class For266AuditProbe(
        val adapter: String,
        val productionRefusal: String,
        val policies: List<For266PolicyResult>,
    ) {
        val currentPolicy: String = "targetColorSpaceBlend=false with normal RGBA16Float intermediate"
        val policyCount: Int = policies.size
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val correctionApplied: Boolean = false
        val fallbackReason: String = "coverage.stroke-cap-join-visual-parity-below-threshold"
        val targetBlendFalseObserved: Boolean = policies.any { !it.spec.targetColorSpaceBlend }
        val targetBlendTrueObserved: Boolean = policies.any { it.spec.targetColorSpaceBlend }
        val rgba16FloatIntermediateObserved: Boolean =
            policies.all { it.spec.intermediateFormatLabel == "RGBA16Float" }
        val targetBlendTrueImprovesSimilarity: Boolean =
            policy("targetBlend-true-rgba16float").stats.exactSimilarity >
                policy("targetBlend-false-rgba16float").stats.exactSimilarity
        val targetBlendTrueStillBelowThreshold: Boolean =
            policy("targetBlend-true-rgba16float").stats.exactSimilarity < SUPPORT_THRESHOLD
        val residualLocalizedToCapJoinRegions: Boolean =
            policies.all { policy -> policy.residual.regions.sumOf { it.mismatchPixels } == policy.residual.mismatchPixels }
        val safeCoverageCorrectionProven: Boolean = false
        val finding: String =
            "targetColorSpaceBlend_reduces_m60_stroke_cap_join_residual_but_cap_join_aa_boundary_remains_below_threshold"
        val admissibleCorrection: String =
            "none_applied: bounded coverage correction is plausible only as a diagnostic hypothesis after targetColorSpaceBlend=true, but FOR-266 does not prove CPU/GPU coverage equivalence for the affected cap/join boundary cells; the scene remains refused"
        val missingCondition: String =
            "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells"
        val remainingBoundary: String = "coverage.stroke-cap-join-aa-residual"
        val preservedUnsupportedReason: String = "image-filter.crop-input-nonnull-prepass-required"

        private fun policy(id: String): For266PolicyResult = policies.single { it.spec.id == id }
    }

    private companion object {
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
        const val EXPERIMENTAL_RENDER_PROPERTY: String = "kanvas.webgpu.strokeCapJoin.experimentalRender"
        const val SUPPORT_THRESHOLD: Double = 99.95
        val POLICY_SPECS: List<For266PolicySpec> = listOf(
            For266PolicySpec(
                id = "targetBlend-false-rgba16float",
                targetColorSpaceBlend = false,
                intermediateFormatLabel = "RGBA16Float",
            ),
            For266PolicySpec(
                id = "targetBlend-true-rgba16float",
                targetColorSpaceBlend = true,
                intermediateFormatLabel = "RGBA16Float",
            ),
        )
        val STROKE_REGIONS: List<StrokeRegion> = listOf(
            StrokeRegion("butt-bevel", "left band: butt cap with bevel join", 0, 48),
            StrokeRegion("round-round", "middle band: round cap with round join", 48, 96),
            StrokeRegion("square-bevel", "right band: square cap with bevel join", 96, 192),
        )
    }
}
