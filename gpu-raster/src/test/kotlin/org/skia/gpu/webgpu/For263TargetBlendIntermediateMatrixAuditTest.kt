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
import org.skia.tests.SimpleOffsetImageFilterGM

class For263TargetBlendIntermediateMatrixAuditTest {
    @Test
    fun `FOR-263 targetColorSpaceBlend intermediateFormat matrix audit stays diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val simpleOffsetGm = SimpleOffsetImageFilterGM()
            val simpleOffsetReference = TestUtils.loadReferenceBitmap(simpleOffsetGm.name())
                ?: error("original-888/${simpleOffsetGm.name()}.png missing")
            val cases = listOf(
                buildRenderedMatrixCase(
                    context = ctx,
                    id = "positive-fixture.m60-target-colorspace-neutral-aa",
                    sceneId = "m60-target-colorspace-neutral-aa",
                    kind = "isolated-positive-target-blend-fixture",
                    route = "webgpu.target-colorspace-blend.solid-coverage",
                    routeDiagnosticsPath = "reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/stats.json",
                    reference = TestUtils.runGmTest(NeutralAaCoverageGM()),
                    gm = NeutralAaCoverageGM(),
                    correctionScope = "isolated solid-color AA coverage sample",
                    admissibility = "ADMISSIBLE_DIAGNOSTIC_ONLY",
                    admissibilityReason = "TargetColorSpaceBlend corrects this isolated solid-color AA sample for both intermediate formats, but the family scope is not production-safe.",
                    dimensionResponsible = "targetColorSpaceBlend",
                ),
                buildRenderedMatrixCase(
                    context = ctx,
                    id = "insufficient-scope.m60-bounded-stroke-cap-join",
                    sceneId = "m60-bounded-stroke-cap-join",
                    kind = "improves-without-exact-parity",
                    route = "webgpu.coverage.stroke-cap-join.experimental-render",
                    routeDiagnosticsPath = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/experimental-gpu-diagnostic.json",
                    reference = TestUtils.runGmTest(BoundedStrokeCapJoinGM()),
                    gm = BoundedStrokeCapJoinGM(),
                    correctionScope = "bounded stroke cap/join diagnostic render",
                    admissibility = "REFUSED_INSUFFICIENT_PARITY",
                    admissibilityReason = "TargetColorSpaceBlend improves exact similarity but the full stroke cap/join scene remains below the 99.95 exact threshold.",
                    dimensionResponsible = "targetColorSpaceBlend-improves-but-insufficient-parity",
                    experimentalStrokeCapJoin = true,
                ),
                buildRenderedMatrixCase(
                    context = ctx,
                    id = "exact-control.black-white-rect",
                    sceneId = "black-white-rect",
                    kind = "already-exact-no-dimension-needed",
                    route = "webgpu.coverage.analytic-rect",
                    routeDiagnosticsPath = null,
                    reference = TestUtils.runGmTest(BlackWhiteRectGM()),
                    gm = BlackWhiteRectGM(),
                    correctionScope = "opaque black rect over white remains exact under every matrix policy",
                    admissibility = "REFUSED_NOT_NEEDED",
                    admissibilityReason = "The normal path is already byte-exact and every matrix policy stays exact.",
                    dimensionResponsible = "none-needed",
                ),
                buildResidualRefusalMatrixCase(
                    context = ctx,
                    id = "for261-residual.simple-offsetimagefilter",
                    sceneId = "simple-offsetimagefilter",
                    kind = "for261-residual-intermediate-boundary-control",
                    route = "webgpu.image-filter.offset-crop-prepass-and-src-over",
                    routeDiagnosticsPath = null,
                    reference = simpleOffsetReference,
                    gm = simpleOffsetGm,
                    correctionScope = "FOR-261 residual route separates intermediate store/present correction from targetColorSpaceBlend",
                    admissibility = "REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND",
                    admissibilityReason = "TargetColorSpaceBlend refuses the residual image-filter route; the admissible diagnostic signal remains the intermediateFormat axis only.",
                    dimensionResponsible = "intermediateFormat-when-targetColorSpaceBlend-refused",
                ),
            )
            val probe = For263MatrixAuditProbe(cases = cases)

            assertEquals("KEEP_DIAGNOSTIC", probe.supportDecision)
            assertFalse(probe.correctionApplied)
            assertEquals(4, probe.caseCount)
            assertEquals(4, probe.matrixPolicyCount)
            assertTrue(probe.allMatrixPoliciesObserved)
            assertTrue(probe.positiveFixtureCorrectedByTargetBlend)
            assertTrue(probe.residualImprovedByRgba8WhenTargetBlendFalse)
            assertTrue(probe.for261ResidualTargetBlendRefusedForBothFormats)
            assertTrue(probe.exactControlNoDimensionNeeded)
            assertFalse(probe.safeScopeProven)
            assertEquals(
                "image-filter.crop-input-nonnull-prepass-required",
                probe.preservedUnsupportedReason,
                "FOR-263: Crop(input = nonNull) fallback reason must stay preserved",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor263MatrixAuditJson(probe)
                writeFor263MatrixAuditReport(probe)
            }
        }
    }

    private fun buildRenderedMatrixCase(
        context: WebGpuContext,
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        routeDiagnosticsPath: String?,
        reference: SkBitmap,
        gm: GM,
        correctionScope: String,
        admissibility: String,
        admissibilityReason: String,
        dimensionResponsible: String,
        experimentalStrokeCapJoin: Boolean = false,
    ): For263SceneCase {
        val policies = MATRIX_POLICIES.map { policy ->
            withOptionalExperimentalStrokeCapJoin(experimentalStrokeCapJoin) {
                renderMatrixPolicy(
                    context = context,
                    gm = gm,
                    reference = reference,
                    policy = policy,
                    route = route,
                    routeDiagnosticsPath = routeDiagnosticsPath,
                    dimensionResponsible = dimensionResponsible,
                )
            }
        }
        return buildCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            policies = policies,
            correctionScope = correctionScope,
            admissibility = admissibility,
            admissibilityReason = admissibilityReason,
            dimensionResponsible = dimensionResponsible,
        )
    }

    private fun buildResidualRefusalMatrixCase(
        context: WebGpuContext,
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        routeDiagnosticsPath: String?,
        reference: SkBitmap,
        gm: GM,
        correctionScope: String,
        admissibility: String,
        admissibilityReason: String,
        dimensionResponsible: String,
    ): For263SceneCase {
        val rendered = MATRIX_POLICIES.filter { !it.targetColorSpaceBlend }.map { policy ->
            renderMatrixPolicy(
                context = context,
                gm = gm,
                reference = reference,
                policy = policy,
                route = route,
                routeDiagnosticsPath = routeDiagnosticsPath,
                dimensionResponsible = dimensionResponsible,
            )
        }
        val referenceRgba = rendered.first().referenceRgba
        val refusals = MATRIX_POLICIES.filter { it.targetColorSpaceBlend }.map { policy ->
            val error = runCatching {
                renderBitmap(context, gm, policy)
            }.exceptionOrNull() ?: error("FOR-263 expected targetColorSpaceBlend refusal for $sceneId ${policy.id}")
            val refusalReason = error.message ?: "unknown targetColorSpaceBlend refusal"
            require(refusalReason == EXPECTED_FOR261_TARGET_BLEND_REFUSAL) {
                "FOR-263 expected `$EXPECTED_FOR261_TARGET_BLEND_REFUSAL`, got `$refusalReason`"
            }
            For263PolicyStats(
                policy = policy.id,
                evaluationKind = "whole-scene-reference-vs-live-webgpu",
                evaluationStatus = "refused-before-render",
                targetColorSpaceBlend = policy.targetColorSpaceBlend,
                intermediateFormat = policy.intermediateFormatLabel,
                totalPixels = reference.width * reference.height,
                matchingPixels = null,
                exactSimilarity = null,
                maxDelta = null,
                referenceRgba = referenceRgba,
                observedRgba = null,
                signedDeltaRgba = null,
                routeDiagnostics = refusalReason,
                routeDiagnosticsPath = routeDiagnosticsPath,
                routeDiagnosticsRationale = "TargetColorSpaceBlend refuses this unsupported draw kind before GPU resources are encoded; the intermediateFormat=${policy.intermediateFormatLabel} axis is recorded but not rendered for the refused policy.",
                refusalReason = refusalReason,
                responsibilityClassification = dimensionResponsible,
            )
        }
        val policies = MATRIX_POLICIES.map { policy ->
            (rendered + refusals).single { it.policy == policy.id }
        }
        return buildCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            policies = policies,
            correctionScope = correctionScope,
            admissibility = admissibility,
            admissibilityReason = admissibilityReason,
            dimensionResponsible = dimensionResponsible,
        )
    }

    private fun renderMatrixPolicy(
        context: WebGpuContext,
        gm: GM,
        reference: SkBitmap,
        policy: MatrixPolicy,
        route: String,
        routeDiagnosticsPath: String?,
        dimensionResponsible: String,
    ): For263PolicyStats {
        val bitmap = renderBitmap(context, gm, policy)
        val stats = compareImages(reference, bitmap)
        return For263PolicyStats(
            policy = policy.id,
            evaluationKind = "whole-scene-reference-vs-live-webgpu",
            evaluationStatus = "rendered",
            targetColorSpaceBlend = policy.targetColorSpaceBlend,
            intermediateFormat = policy.intermediateFormatLabel,
            totalPixels = stats.totalPixels,
            matchingPixels = stats.matchingPixels,
            exactSimilarity = stats.exactSimilarity,
            maxDelta = stats.maxDelta,
            referenceRgba = stats.representative.reference,
            observedRgba = stats.representative.gpu,
            signedDeltaRgba = stats.representative.signedDelta,
            routeDiagnostics = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            routeDiagnosticsRationale = "Whole-scene live WebGPU render with test-only targetColorSpaceBlend=${policy.targetColorSpaceBlend} and constructor-scoped intermediateFormat=${policy.intermediateFormatLabel}; no renderer default, shader, threshold, Crop, or fallback policy changed.",
            refusalReason = null,
            responsibilityClassification = dimensionResponsible,
        )
    }

    private fun renderBitmap(
        context: WebGpuContext,
        gm: GM,
        policy: MatrixPolicy,
    ): SkBitmap {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = true,
            targetColorSpaceBlend = policy.targetColorSpaceBlend,
            intermediateFormat = policy.intermediateFormat,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            return rgbaBytesToBitmap(device.flush(), size.width, size.height)
        }
    }

    private fun buildCase(
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        policies: List<For263PolicyStats>,
        correctionScope: String,
        admissibility: String,
        admissibilityReason: String,
        dimensionResponsible: String,
    ): For263SceneCase {
        val baseline = policies.single { it.policy == "targetBlend-false-rgba16float" }
        val bestRendered = policies
            .filter { it.evaluationStatus == "rendered" }
            .maxWith(compareBy<For263PolicyStats> { requireNotNull(it.exactSimilarity) }
                .thenByDescending { requireNotNull(it.maxDelta) * -1 })
        val regression = policies.any { policy ->
            policy.evaluationStatus == "rendered" &&
                (
                    requireNotNull(policy.exactSimilarity) < requireNotNull(baseline.exactSimilarity) ||
                        (
                            policy.exactSimilarity == baseline.exactSimilarity &&
                                requireNotNull(policy.maxDelta) > requireNotNull(baseline.maxDelta)
                            )
                    )
        }
        val correctionStatus = when {
            policies.any { it.evaluationStatus == "refused-before-render" } -> "REFUSED_FOR_TARGET_BLEND_POLICIES"
            regression -> "REGRESSION"
            requireNotNull(bestRendered.exactSimilarity) > requireNotNull(baseline.exactSimilarity) &&
                bestRendered.exactSimilarity < SUPPORT_THRESHOLD -> "CORRECTION_SIGNAL_INSUFFICIENT"
            bestRendered.exactSimilarity > baseline.exactSimilarity -> "CORRECTION_SIGNAL"
            policies.filter { it.evaluationStatus == "rendered" }.all {
                it.exactSimilarity == baseline.exactSimilarity && it.maxDelta == baseline.maxDelta
            } -> "UNCHANGED"
            else -> "MIXED"
        }
        return For263SceneCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            policies = policies,
            baselinePolicy = baseline.policy,
            bestRenderedPolicy = bestRendered.policy,
            regression = regression,
            correctionStatus = correctionStatus,
            correctionScope = correctionScope,
            admissibility = admissibility,
            admissibilityReason = admissibilityReason,
            dimensionResponsible = dimensionResponsible,
            verdict = buildVerdict(kind, correctionStatus, dimensionResponsible),
        )
    }

    private fun buildVerdict(kind: String, correctionStatus: String, dimensionResponsible: String): String =
        when (kind) {
            "isolated-positive-target-blend-fixture" ->
                "targetColorSpaceBlend is the responsible dimension for the isolated neutral AA fixture; correctionStatus=$correctionStatus."
            "improves-without-exact-parity" ->
                "targetColorSpaceBlend improves the bounded stroke cap/join diagnostic, but exact parity remains below threshold; dimensionResponsible=$dimensionResponsible; correctionStatus=$correctionStatus."
            "already-exact-no-dimension-needed" ->
                "all matrix policies remain exact, so no dimension is needed; correctionStatus=$correctionStatus."
            else ->
                "targetColorSpaceBlend is refused for the FOR-261 residual route while RGBA8Unorm improves the targetBlend=false path; dimensionResponsible=$dimensionResponsible; correctionStatus=$correctionStatus."
        }

    private fun compareImages(reference: SkBitmap, gpu: SkBitmap): For263ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-263 requires same-size SkBitmap evidence"
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
        return For263ImageStats(
            totalPixels = total,
            matchingPixels = matching,
            exactSimilarity = matching * 100.0 / total,
            maxDelta = maxDelta,
            representative = representative ?: pixelDelta(reference, gpu, x = 0, y = 0),
        )
    }

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

    private fun <T> withOptionalExperimentalStrokeCapJoin(enabled: Boolean, block: () -> T): T {
        if (!enabled) return block()
        val previous = System.getProperty(EXPERIMENTAL_STROKE_CAP_JOIN_PROPERTY)
        System.setProperty(EXPERIMENTAL_STROKE_CAP_JOIN_PROPERTY, "true")
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(EXPERIMENTAL_STROKE_CAP_JOIN_PROPERTY)
            } else {
                System.setProperty(EXPERIMENTAL_STROKE_CAP_JOIN_PROPERTY, previous)
            }
        }
    }

    private fun writeFor263MatrixAuditJson(probe: For263MatrixAuditProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "Skia CPU GM references and live whole-scene WebGPU renders",
              "linear": "FOR-263",
              "probe": "target-blend-intermediate-format-matrix-audit",
              "deltaDefinition": "signed channel delta is matrix-policy live WebGPU output minus reference",
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
              "matrixPolicyCount": ${probe.matrixPolicyCount},
              "matrixPolicies": [
                "targetBlend-false-rgba16float",
                "targetBlend-false-rgba8unorm",
                "targetBlend-true-rgba16float",
                "targetBlend-true-rgba8unorm"
              ],
              "caseCount": ${probe.caseCount},
              "supportThreshold": $SUPPORT_THRESHOLD,
              "observedBoundaries": {
                "targetColorSpaceBlendFalseObserved": ${probe.targetColorSpaceBlendFalseObserved},
                "targetColorSpaceBlendTrueObserved": ${probe.targetColorSpaceBlendTrueObserved},
                "rgba16FloatIntermediateObserved": ${probe.rgba16FloatIntermediateObserved},
                "rgba8UnormIntermediateObserved": ${probe.rgba8UnormIntermediateObserved},
                "allMatrixPoliciesObserved": ${probe.allMatrixPoliciesObserved},
                "for261ResidualBoundaryObserved": ${probe.for261ResidualBoundaryObserved}
              },
              "cases": [
            ${probe.cases.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "summary": {
                "positiveFixtureCorrectedByTargetBlend": ${probe.positiveFixtureCorrectedByTargetBlend},
                "boundedStrokeImprovesWithoutExactParity": ${probe.boundedStrokeImprovesWithoutExactParity},
                "exactControlNoDimensionNeeded": ${probe.exactControlNoDimensionNeeded},
                "residualImprovedByRgba8WhenTargetBlendFalse": ${probe.residualImprovedByRgba8WhenTargetBlendFalse},
                "for261ResidualTargetBlendRefusedForBothFormats": ${probe.for261ResidualTargetBlendRefusedForBothFormats},
                "matrixRegressesAnyRenderedCase": ${probe.matrixRegressesAnyRenderedCase},
                "safeScopeProven": ${probe.safeScopeProven}
              },
              "finding": "${probe.finding}",
              "admissibleCorrection": "${probe.admissibleCorrection}",
              "missingCondition": "${probe.missingCondition}",
              "remainingBoundary": "${probe.remainingBoundary}",
              "interpretation": "FOR-263 observes targetColorSpaceBlend x intermediateFormat as a test-only matrix. TargetColorSpaceBlend corrects the isolated neutral AA fixture, RGBA8Unorm corrects or improves FOR-261 intermediate-boundary residuals only when targetColorSpaceBlend is false, bounded stroke cap/join remains below parity, and the image-filter residual refuses targetColorSpaceBlend for both intermediate formats.",
              "observationMethod": "test-only SkWebGpuDevice construction with targetColorSpaceBlend and intermediateFormat parameters; no renderer property, no default switch, no normal shader change, no threshold change, no Crop correction, and no fallback-policy change",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": ${probe.correctionApplied},
              "preservedUnsupportedReason": "${probe.preservedUnsupportedReason}"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/target-blend-intermediate-matrix-audit-for263",
            "reports/wgsl-pipeline/scenes/artifacts/target-blend-intermediate-matrix-audit-for263",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "target-blend-intermediate-matrix-audit-for263.json").writeText(contents)
        }
    }

    private fun writeFor263MatrixAuditReport(probe: For263MatrixAuditProbe) {
        val report = buildString {
            appendLine("# FOR-263 TargetColorSpaceBlend x IntermediateFormat Matrix Audit")
            appendLine()
            appendLine("Decision: `KEEP_DIAGNOSTIC`")
            appendLine()
            appendLine("FOR-263 compares the test-only matrix of `targetColorSpaceBlend` and")
            appendLine("`intermediateFormat` without changing production defaults, shaders,")
            appendLine("thresholds, Crop policy, fallback policy, or global renderer properties.")
            appendLine()
            appendLine("Preserved fallback reason:")
            appendLine()
            appendLine("```text")
            appendLine(probe.preservedUnsupportedReason)
            appendLine("```")
            appendLine()
            appendLine("## Artifacts")
            appendLine()
            appendLine("- `reports/wgsl-pipeline/scenes/generated/artifacts/target-blend-intermediate-matrix-audit-for263/target-blend-intermediate-matrix-audit-for263.json`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/target-blend-intermediate-matrix-audit-for263/target-blend-intermediate-matrix-audit-for263.json`")
            appendLine()
            appendLine("## Cases")
            appendLine()
            appendLine("| Case | Policy | Status | Exact similarity | Max delta | Matching pixels | Route diagnostics | Classification |")
            appendLine("|---|---|---|---:|---:|---:|---|---|")
            for (case in probe.cases) {
                for (policy in case.policies) {
                    appendLine(
                        "| `${case.id}` | `${policy.policy}` | `${policy.evaluationStatus}` | " +
                            "${policy.exactSimilarity.reportValue()} | ${policy.maxDelta.reportValue()} | " +
                            "${policy.matchingPixelsSummary()} | `${policy.routeDiagnostics}` | " +
                            "`${policy.responsibilityClassification}` |",
                    )
                }
            }
            appendLine()
            appendLine("## Case Decisions")
            appendLine()
            appendLine("| Case | Correction status | Best rendered policy | Admissibility | Dimension responsible |")
            appendLine("|---|---|---|---|---|")
            for (case in probe.cases) {
                appendLine(
                    "| `${case.id}` | `${case.correctionStatus}` | `${case.bestRenderedPolicy}` | " +
                        "`${case.admissibility}` | `${case.dimensionResponsible}` |",
                )
            }
            appendLine()
            appendLine("## Conclusion")
            appendLine()
            appendLine(probe.admissibleCorrection)
            appendLine()
            appendLine("Missing condition: `${probe.missingCondition}`.")
            appendLine()
            appendLine("The remaining FOR-261 boundary stays `${probe.remainingBoundary}`.")
            appendLine()
            appendLine("## Validation")
            appendLine()
            appendLine("```text")
            appendLine("rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-263*'")
            appendLine("rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py")
            appendLine("rtk python3 scripts/validate_for262_target_colorspace_blend_scope_audit.py")
            appendLine("rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py")
            appendLine("rtk ./gradlew --no-daemon pipelineSceneDashboardGate")
            appendLine("rtk git diff --check")
            appendLine("```")
        }
        repoFile("reports/wgsl-pipeline/2026-06-03-for-263-target-blend-intermediate-matrix-audit.md")
            .writeText(report)
    }

    private fun For263SceneCase.toJson(indent: String): String =
        """
        {
          "id": ${id.jsonString()},
          "sceneId": ${sceneId.jsonString()},
          "kind": ${kind.jsonString()},
          "route": ${route.jsonString()},
          "policies": [
        ${policies.joinToString(",\n") { it.toJson(indent = "$indent      ") }}
          ],
          "baselinePolicy": ${baselinePolicy.jsonString()},
          "bestRenderedPolicy": ${bestRenderedPolicy.jsonString()},
          "regression": $regression,
          "correctionStatus": ${correctionStatus.jsonString()},
          "correctionScope": ${correctionScope.jsonString()},
          "admissibility": ${admissibility.jsonString()},
          "admissibilityReason": ${admissibilityReason.jsonString()},
          "dimensionResponsible": ${dimensionResponsible.jsonString()},
          "verdict": ${verdict.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun For263PolicyStats.toJson(indent: String): String =
        """
        {
          "policy": ${policy.jsonString()},
          "evaluationKind": ${evaluationKind.jsonString()},
          "evaluationStatus": ${evaluationStatus.jsonString()},
          "targetColorSpaceBlend": $targetColorSpaceBlend,
          "intermediateFormat": ${intermediateFormat.jsonString()},
          "totalPixels": $totalPixels,
          "matchingPixels": ${matchingPixels?.toString() ?: "null"},
          "exactSimilarity": ${exactSimilarity?.toString() ?: "null"},
          "maxDelta": ${maxDelta?.toString() ?: "null"},
          "referenceRgba": ${jsonArray(referenceRgba)},
          "observedRgba": ${observedRgba?.let { jsonArray(it) } ?: "null"},
          "signedDeltaRgba": ${signedDeltaRgba?.let { jsonArray(it) } ?: "null"},
          "routeDiagnostics": ${routeDiagnostics.jsonString()},
          "routeDiagnosticsPath": ${routeDiagnosticsPath?.jsonString() ?: "null"},
          "routeDiagnosticsRationale": ${routeDiagnosticsRationale.jsonString()},
          "refusalReason": ${refusalReason?.jsonString() ?: "null"},
          "responsibilityClassification": ${responsibilityClassification.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun For263PolicyStats.matchingPixelsSummary(): String =
        matchingPixels?.let { "$it/$totalPixels" } ?: "refused/$totalPixels"

    private fun Double?.reportValue(): String =
        this?.let { String.format(Locale.US, "%.6f", it).trimEnd('0').trimEnd('.') } ?: "refused"

    private fun Int?.reportValue(): String = this?.toString() ?: "refused"

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

    private class NeutralAaCoverageGM : GM() {
        override fun getName(): String = "m60_neutral_aa_coverage"
        override fun getISize(): SkISize = SkISize.Make(4, 1)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            c.drawRect(
                SkRect.MakeLTRB(0.5f, 0f, 1.5f, 1f),
                SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                    style = SkPaint.Style.kFill_Style
                },
            )
        }
    }

    private class BlackWhiteRectGM : GM() {
        override fun getName(): String = "for263_black_white_rect"
        override fun getISize(): SkISize = SkISize.Make(8, 8)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            c.drawRect(
                SkRect.MakeLTRB(2f, 1f, 7f, 6f),
                SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = false
                    style = SkPaint.Style.kFill_Style
                },
            )
        }
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

    private data class MatrixPolicy(
        val id: String,
        val targetColorSpaceBlend: Boolean,
        val intermediateFormat: GPUTextureFormat,
        val intermediateFormatLabel: String,
    )

    private data class PixelDelta(
        val x: Int,
        val y: Int,
        val reference: IntArray,
        val gpu: IntArray,
    ) {
        val delta: IntArray = IntArray(4) { channel -> kotlin.math.abs(reference[channel] - gpu[channel]) }
        val signedDelta: IntArray = IntArray(4) { channel -> gpu[channel] - reference[channel] }
        val maxChannelDelta: Int = delta.maxOrNull() ?: 0
    }

    private data class For263ImageStats(
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val representative: PixelDelta,
    )

    private data class For263PolicyStats(
        val policy: String,
        val evaluationKind: String,
        val evaluationStatus: String,
        val targetColorSpaceBlend: Boolean,
        val intermediateFormat: String,
        val totalPixels: Int,
        val matchingPixels: Int?,
        val exactSimilarity: Double?,
        val maxDelta: Int?,
        val referenceRgba: IntArray,
        val observedRgba: IntArray?,
        val signedDeltaRgba: IntArray?,
        val routeDiagnostics: String,
        val routeDiagnosticsPath: String?,
        val routeDiagnosticsRationale: String,
        val refusalReason: String?,
        val responsibilityClassification: String,
    )

    private data class For263SceneCase(
        val id: String,
        val sceneId: String,
        val kind: String,
        val route: String,
        val policies: List<For263PolicyStats>,
        val baselinePolicy: String,
        val bestRenderedPolicy: String,
        val regression: Boolean,
        val correctionStatus: String,
        val correctionScope: String,
        val admissibility: String,
        val admissibilityReason: String,
        val dimensionResponsible: String,
        val verdict: String,
    )

    private data class For263MatrixAuditProbe(
        val cases: List<For263SceneCase>,
    ) {
        val caseCount: Int = cases.size
        val matrixPolicyCount: Int = MATRIX_POLICIES.size
        val currentPolicy: String = "targetColorSpaceBlend=false with normal RGBA16Float intermediate"
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val correctionApplied: Boolean = false
        val targetColorSpaceBlendFalseObserved: Boolean =
            cases.all { case -> case.policies.any { !it.targetColorSpaceBlend } }
        val targetColorSpaceBlendTrueObserved: Boolean =
            cases.all { case -> case.policies.any { it.targetColorSpaceBlend } }
        val rgba16FloatIntermediateObserved: Boolean =
            cases.all { case -> case.policies.any { it.intermediateFormat == "RGBA16Float" } }
        val rgba8UnormIntermediateObserved: Boolean =
            cases.all { case -> case.policies.any { it.intermediateFormat == "RGBA8Unorm" } }
        val allMatrixPoliciesObserved: Boolean =
            cases.all { case -> MATRIX_POLICIES.all { policy -> case.policies.any { it.policy == policy.id } } }
        val for261ResidualBoundaryObserved: Boolean =
            cases.any { it.kind == "for261-residual-intermediate-boundary-control" }
        val positiveFixtureCorrectedByTargetBlend: Boolean =
            cases.filter { it.kind == "isolated-positive-target-blend-fixture" }
                .flatMap { it.policies }
                .filter { it.targetColorSpaceBlend && it.evaluationStatus == "rendered" }
                .all { it.matchingPixels == it.totalPixels }
        val boundedStrokeImprovesWithoutExactParity: Boolean =
            cases.filter { it.kind == "improves-without-exact-parity" }.any { case ->
                val baseline = case.policies.single { it.policy == "targetBlend-false-rgba16float" }
                case.policies.any {
                    it.targetColorSpaceBlend &&
                        it.exactSimilarity != null &&
                        it.exactSimilarity > requireNotNull(baseline.exactSimilarity) &&
                        it.exactSimilarity < SUPPORT_THRESHOLD
                }
            }
        val exactControlNoDimensionNeeded: Boolean =
            cases.filter { it.kind == "already-exact-no-dimension-needed" }
                .any { case -> case.policies.all { it.matchingPixels == it.totalPixels } }
        val residualImprovedByRgba8WhenTargetBlendFalse: Boolean =
            cases.filter { it.kind == "for261-residual-intermediate-boundary-control" }.any { case ->
                val baseline = case.policies.single { it.policy == "targetBlend-false-rgba16float" }
                val rgba8 = case.policies.single { it.policy == "targetBlend-false-rgba8unorm" }
                requireNotNull(rgba8.exactSimilarity) > requireNotNull(baseline.exactSimilarity)
            }
        val for261ResidualTargetBlendRefusedForBothFormats: Boolean =
            cases.filter { it.kind == "for261-residual-intermediate-boundary-control" }.any { case ->
                case.policies.filter { it.targetColorSpaceBlend }
                    .all { it.evaluationStatus == "refused-before-render" && it.refusalReason == EXPECTED_FOR261_TARGET_BLEND_REFUSAL }
            }
        val matrixRegressesAnyRenderedCase: Boolean = cases.any { it.regression }
        val safeScopeProven: Boolean = false
        val finding: String =
            "targetColorSpaceBlend_and_intermediateFormat_correct_different_boundaries_without_safe_shared_scope"
        val admissibleCorrection: String =
            "none_applied: targetColorSpaceBlend remains diagnostic for an isolated and insufficient-parity family scope, RGBA8Unorm remains diagnostic for FOR-261 residual intermediate-boundary cases, and targetColorSpaceBlend refuses the image-filter residual route for both intermediate formats"
        val missingCondition: String =
            "missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation"
        val remainingBoundary: String =
            "rgba16float-intermediate-store-to-present-byte-quantization-policy"
        val preservedUnsupportedReason: String = "image-filter.crop-input-nonnull-prepass-required"
    }

    private companion object {
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
        const val EXPERIMENTAL_STROKE_CAP_JOIN_PROPERTY: String =
            "kanvas.webgpu.strokeCapJoin.experimentalRender"
        const val SUPPORT_THRESHOLD: Double = 99.95
        const val EXPECTED_FOR261_TARGET_BLEND_REFUSAL: String =
            "color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw"
        val MATRIX_POLICIES: List<MatrixPolicy> = listOf(
            MatrixPolicy(
                id = "targetBlend-false-rgba16float",
                targetColorSpaceBlend = false,
                intermediateFormat = GPUTextureFormat.RGBA16Float,
                intermediateFormatLabel = "RGBA16Float",
            ),
            MatrixPolicy(
                id = "targetBlend-false-rgba8unorm",
                targetColorSpaceBlend = false,
                intermediateFormat = GPUTextureFormat.RGBA8Unorm,
                intermediateFormatLabel = "RGBA8Unorm",
            ),
            MatrixPolicy(
                id = "targetBlend-true-rgba16float",
                targetColorSpaceBlend = true,
                intermediateFormat = GPUTextureFormat.RGBA16Float,
                intermediateFormatLabel = "RGBA16Float",
            ),
            MatrixPolicy(
                id = "targetBlend-true-rgba8unorm",
                targetColorSpaceBlend = true,
                intermediateFormat = GPUTextureFormat.RGBA8Unorm,
                intermediateFormatLabel = "RGBA8Unorm",
            ),
        )
    }
}
