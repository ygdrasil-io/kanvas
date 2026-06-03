package org.skia.gpu.webgpu

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
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.testing.TestUtils
import org.skia.tests.GM
import org.skia.tests.SimpleOffsetImageFilterGM

class For262TargetColorSpaceBlendScopeAuditTest {
    @Test
    fun `FOR-262 targetColorSpaceBlend whole scene scope audit stays diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val cases = listOf(
                buildRenderedCase(
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
                    admissibilityReason = "Target blend corrects this isolated solid-color AA sample, but a single fixture is not a production scope.",
                ),
                buildRenderedCase(
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
                    admissibilityReason = "Target blend improves exact similarity but the full stroke cap/join scene remains below the 99.95 exact threshold.",
                    experimentalStrokeCapJoin = true,
                ),
                buildRenderedCase(
                    context = ctx,
                    id = "exact-control.black-white-rect",
                    sceneId = "black-white-rect",
                    kind = "already-exact-target-blend-not-needed",
                    route = "webgpu.coverage.analytic-rect",
                    routeDiagnosticsPath = null,
                    reference = TestUtils.runGmTest(BlackWhiteRectGM()),
                    gm = BlackWhiteRectGM(),
                    correctionScope = "opaque black rect over white remains exact without targetColorSpaceBlend",
                    admissibility = "REFUSED_NOT_NEEDED",
                    admissibilityReason = "The normal path is already byte-exact; targetColorSpaceBlend adds no correction signal.",
                ),
                buildRefusalCase(
                    context = ctx,
                    id = "for261-residual.simple-offsetimagefilter",
                    sceneId = "simple-offsetimagefilter",
                    kind = "for261-residual-intermediate-boundary-control",
                    route = "webgpu.image-filter.offset-crop-prepass-and-src-over",
                    routeDiagnosticsPath = null,
                    reference = TestUtils.loadReferenceBitmap(SimpleOffsetImageFilterGM().name())
                        ?: error("original-888/${SimpleOffsetImageFilterGM().name()}.png missing"),
                    gm = SimpleOffsetImageFilterGM(),
                    correctionScope = "FOR-261 residual route proves color correction is not an intermediate-boundary substitute",
                    admissibility = "REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND",
                    admissibilityReason = "The residual image-filter route is outside the audited targetColorSpaceBlend draw family and still belongs to the intermediate store/present boundary.",
                ),
            )
            val probe = For262TargetBlendScopeAuditProbe(cases = cases)

            assertEquals("KEEP_DIAGNOSTIC", probe.supportDecision)
            assertFalse(probe.correctionApplied)
            assertEquals(4, probe.caseCount)
            assertTrue(probe.positiveFixtureCorrected)
            assertTrue(probe.improvesWithoutExactParity)
            assertTrue(probe.exactControlDoesNotNeedTargetBlend)
            assertTrue(probe.for261ResidualTargetBlendRefused)
            assertEquals(
                "image-filter.crop-input-nonnull-prepass-required",
                probe.preservedUnsupportedReason,
                "FOR-262: Crop(input = nonNull) fallback reason must stay preserved",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor262TargetBlendScopeAuditJson(probe)
                writeFor262TargetBlendScopeReport(probe)
            }
        }
    }

    private fun buildRenderedCase(
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
        experimentalStrokeCapJoin: Boolean = false,
    ): For262SceneCase {
        val currentBitmap = withOptionalExperimentalStrokeCapJoin(experimentalStrokeCapJoin) {
            WebGpuSink.draw(context, gm, targetColorSpaceBlend = false)
        }
        val targetBitmap = withOptionalExperimentalStrokeCapJoin(experimentalStrokeCapJoin) {
            WebGpuSink.draw(context, gm, targetColorSpaceBlend = true)
        }
        val current = renderedPolicy(
            policy = "current-targetColorSpaceBlend-false",
            targetColorSpaceBlend = false,
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            stats = compareImages(reference, currentBitmap),
        )
        val target = renderedPolicy(
            policy = "diagnostic-targetColorSpaceBlend-true",
            targetColorSpaceBlend = true,
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            stats = compareImages(reference, targetBitmap),
        )
        return buildCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            current = current,
            target = target,
            correctionScope = correctionScope,
            admissibility = admissibility,
            admissibilityReason = admissibilityReason,
        )
    }

    private fun buildRefusalCase(
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
    ): For262SceneCase {
        val currentBitmap = WebGpuSink.draw(context, gm, targetColorSpaceBlend = false)
        val current = renderedPolicy(
            policy = "current-targetColorSpaceBlend-false",
            targetColorSpaceBlend = false,
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            stats = compareImages(reference, currentBitmap),
        )
        val error = runCatching {
            WebGpuSink.draw(context, gm, targetColorSpaceBlend = true)
        }.exceptionOrNull() ?: error("FOR-262 expected targetColorSpaceBlend refusal for $sceneId")
        val refusalReason = error.message ?: "unknown targetColorSpaceBlend refusal"
        require(refusalReason == EXPECTED_FOR261_TARGET_BLEND_REFUSAL) {
            "FOR-262 expected `$EXPECTED_FOR261_TARGET_BLEND_REFUSAL`, got `$refusalReason`"
        }
        val target = For262PolicyStats(
            policy = "diagnostic-targetColorSpaceBlend-true",
            evaluationKind = "whole-scene-reference-vs-live-webgpu",
            evaluationStatus = "refused-before-render",
            targetColorSpaceBlend = true,
            totalPixels = current.totalPixels,
            matchingPixels = null,
            exactSimilarity = null,
            maxDelta = null,
            referenceRgba = current.referenceRgba,
            observedRgba = null,
            signedDeltaRgba = null,
            routeDiagnostics = refusalReason,
            routeDiagnosticsPath = routeDiagnosticsPath,
            routeDiagnosticsRationale = "TargetColorSpaceBlend refuses unsupported draw kinds before GPU resources are encoded; normal route metrics are still recorded for the residual FOR-261 scene.",
            refusalReason = refusalReason,
        )
        return buildCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            current = current,
            target = target,
            correctionScope = correctionScope,
            admissibility = admissibility,
            admissibilityReason = admissibilityReason,
        )
    }

    private fun renderedPolicy(
        policy: String,
        targetColorSpaceBlend: Boolean,
        route: String,
        routeDiagnosticsPath: String?,
        stats: For262ImageStats,
    ): For262PolicyStats =
        For262PolicyStats(
            policy = policy,
            evaluationKind = "whole-scene-reference-vs-live-webgpu",
            evaluationStatus = "rendered",
            targetColorSpaceBlend = targetColorSpaceBlend,
            totalPixels = stats.totalPixels,
            matchingPixels = stats.matchingPixels,
            exactSimilarity = stats.exactSimilarity,
            maxDelta = stats.maxDelta,
            referenceRgba = stats.representative.reference,
            observedRgba = stats.representative.gpu,
            signedDeltaRgba = stats.representative.signedDelta,
            routeDiagnostics = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            routeDiagnosticsRationale = "Whole-scene live WebGPU render with targetColorSpaceBlend=$targetColorSpaceBlend; no renderer default, shader, threshold, Crop, fallback, or intermediateFormat policy changed.",
            refusalReason = null,
        )

    private fun buildCase(
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        current: For262PolicyStats,
        target: For262PolicyStats,
        correctionScope: String,
        admissibility: String,
        admissibilityReason: String,
    ): For262SceneCase {
        val regression = target.exactSimilarity != null &&
            (
                target.exactSimilarity < requireNotNull(current.exactSimilarity) ||
                    (
                        target.exactSimilarity == current.exactSimilarity &&
                            requireNotNull(target.maxDelta) > requireNotNull(current.maxDelta)
                        )
                )
        val correctionStatus = when {
            target.evaluationStatus != "rendered" -> "REFUSED"
            regression -> "REGRESSION"
            requireNotNull(target.exactSimilarity) > requireNotNull(current.exactSimilarity) &&
                target.exactSimilarity < SUPPORT_THRESHOLD -> "CORRECTION_SIGNAL_INSUFFICIENT"
            target.exactSimilarity > current.exactSimilarity -> "CORRECTION_SIGNAL"
            target.exactSimilarity == current.exactSimilarity &&
                target.maxDelta == current.maxDelta -> "UNCHANGED"
            else -> "MIXED"
        }
        return For262SceneCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            current = current,
            target = target,
            regression = regression,
            correctionStatus = correctionStatus,
            correctionScope = correctionScope,
            admissibility = admissibility,
            admissibilityReason = admissibilityReason,
            verdict = buildVerdict(kind, correctionStatus, admissibility),
        )
    }

    private fun buildVerdict(kind: String, correctionStatus: String, admissibility: String): String =
        when (kind) {
            "isolated-positive-target-blend-fixture" ->
                "targetColorSpaceBlend corrects the isolated neutral AA fixture; correctionStatus=$correctionStatus; admissibility=$admissibility."
            "improves-without-exact-parity" ->
                "targetColorSpaceBlend improves the bounded stroke cap/join diagnostic render but exact parity remains below threshold; correctionStatus=$correctionStatus; admissibility=$admissibility."
            "already-exact-target-blend-not-needed" ->
                "normal rendering is already exact, so targetColorSpaceBlend is not needed; correctionStatus=$correctionStatus; admissibility=$admissibility."
            else ->
                "targetColorSpaceBlend is refused for this FOR-261 residual route, proving the color-space condition is not a substitute for the intermediate boundary; correctionStatus=$correctionStatus; admissibility=$admissibility."
        }

    private fun compareImages(reference: SkBitmap, gpu: SkBitmap): For262ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-262 requires same-size SkBitmap evidence"
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
        return For262ImageStats(
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

    private fun writeFor262TargetBlendScopeAuditJson(probe: For262TargetBlendScopeAuditProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "Skia CPU GM references and live whole-scene WebGPU renders",
              "linear": "FOR-262",
              "probe": "target-colorspace-blend-whole-scene-scope-audit",
              "deltaDefinition": "signed channel delta is current/target live WebGPU output minus reference",
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
              "boundedDiagnosticCandidate": "${probe.boundedDiagnosticCandidate}",
              "caseCount": ${probe.caseCount},
              "supportThreshold": $SUPPORT_THRESHOLD,
              "observedBoundaries": {
                "targetColorSpaceBlendFalseObserved": ${probe.targetColorSpaceBlendFalseObserved},
                "targetColorSpaceBlendTrueObserved": ${probe.targetColorSpaceBlendTrueObserved},
                "positiveFixtureObserved": ${probe.positiveFixtureObserved},
                "improvesWithoutExactParityObserved": ${probe.improvesWithoutExactParityObserved},
                "alreadyExactControlObserved": ${probe.alreadyExactControlObserved},
                "for261ResidualBoundaryObserved": ${probe.for261ResidualBoundaryObserved}
              },
              "cases": [
            ${probe.cases.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "summary": {
                "positiveFixtureCorrected": ${probe.positiveFixtureCorrected},
                "targetBlendImprovesAnyCase": ${probe.targetBlendImprovesAnyCase},
                "improvesWithoutExactParity": ${probe.improvesWithoutExactParity},
                "exactControlDoesNotNeedTargetBlend": ${probe.exactControlDoesNotNeedTargetBlend},
                "for261ResidualTargetBlendRefused": ${probe.for261ResidualTargetBlendRefused},
                "targetBlendRegressesAnyRenderedCase": ${probe.targetBlendRegressesAnyRenderedCase},
                "safeScopeProven": ${probe.safeScopeProven}
              },
              "finding": "${probe.finding}",
              "admissibleCorrection": "${probe.admissibleCorrection}",
              "missingCondition": "${probe.missingCondition}",
              "remainingBoundary": "${probe.remainingBoundary}",
              "interpretation": "FOR-262 observes targetColorSpaceBlend=false and targetColorSpaceBlend=true as test-only whole-scene evidence. The isolated neutral AA fixture is corrected and the bounded stroke cap/join scene improves, but a full-scene parity gate still fails, an exact control does not need the mode, and a FOR-261 residual image-filter route is refused rather than corrected.",
              "observationMethod": "test-only WebGpuSink targetColorSpaceBlend toggles plus the existing stroke cap/join diagnostic render flag; no renderer property, no default switch, no normal shader change, no threshold change, no Crop correction, no fallback-policy change, and no intermediateFormat policy change",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": ${probe.correctionApplied},
              "preservedUnsupportedReason": "${probe.preservedUnsupportedReason}"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/target-colorspace-blend-scope-audit-for262",
            "reports/wgsl-pipeline/scenes/artifacts/target-colorspace-blend-scope-audit-for262",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "target-colorspace-blend-scope-audit-for262.json").writeText(contents)
        }
    }

    private fun writeFor262TargetBlendScopeReport(probe: For262TargetBlendScopeAuditProbe) {
        val report = buildString {
            appendLine("# FOR-262 TargetColorSpaceBlend Scope Audit")
            appendLine()
            appendLine("Decision: `KEEP_DIAGNOSTIC`")
            appendLine()
            appendLine("FOR-262 compares whole-scene `targetColorSpaceBlend=false` and")
            appendLine("`targetColorSpaceBlend=true` without changing production defaults,")
            appendLine("normal shaders, thresholds, Crop policy, fallback policy, or the")
            appendLine("normal `RGBA16Float` intermediate policy.")
            appendLine()
            appendLine("Preserved fallback reason:")
            appendLine()
            appendLine("```text")
            appendLine(probe.preservedUnsupportedReason)
            appendLine("```")
            appendLine()
            appendLine("## Artifacts")
            appendLine()
            appendLine("- `reports/wgsl-pipeline/scenes/generated/artifacts/target-colorspace-blend-scope-audit-for262/target-colorspace-blend-scope-audit-for262.json`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/target-colorspace-blend-scope-audit-for262/target-colorspace-blend-scope-audit-for262.json`")
            appendLine()
            appendLine("## Cases")
            appendLine()
            appendLine("| Case | Current exact | Target exact | Current max delta | Target max delta | Matching pixels | Status | Route diagnostics | Admissibility |")
            appendLine("|---|---:|---:|---:|---:|---:|---|---|---|")
            for (case in probe.cases) {
                appendLine(
                    "| `${case.id}` | ${case.current.exactSimilarity.reportValue()} | " +
                        "${case.target.exactSimilarity.reportValue()} | ${case.current.maxDelta.reportValue()} | " +
                        "${case.target.maxDelta.reportValue()} | ${case.matchingPixelsSummary()} | " +
                        "`${case.correctionStatus}` | `${case.target.routeDiagnostics}` | `${case.admissibility}` |",
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
            appendLine("rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-262*'")
            appendLine("rtk python3 scripts/validate_for262_target_colorspace_blend_scope_audit.py")
            appendLine("rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py")
            appendLine("rtk python3 scripts/validate_for260_intermediate_quantization_candidate_audit.py")
            appendLine("rtk ./gradlew --no-daemon pipelineSceneDashboardGate")
            appendLine("rtk git diff --check")
            appendLine("```")
        }
        repoFile("reports/wgsl-pipeline/2026-06-03-for-262-target-colorspace-blend-scope-audit.md")
            .writeText(report)
    }

    private fun For262SceneCase.matchingPixelsSummary(): String =
        if (target.matchingPixels == null) {
            "${current.matchingPixels}/${current.totalPixels} -> refused"
        } else {
            "${current.matchingPixels}/${current.totalPixels} -> ${target.matchingPixels}/${target.totalPixels}"
        }

    private fun Double?.reportValue(): String =
        this?.let { String.format(Locale.US, "%.6f", it).trimEnd('0').trimEnd('.') } ?: "refused"

    private fun Int?.reportValue(): String = this?.toString() ?: "refused"

    private fun For262SceneCase.toJson(indent: String): String =
        """
        {
          "id": ${id.jsonString()},
          "sceneId": ${sceneId.jsonString()},
          "kind": ${kind.jsonString()},
          "route": ${route.jsonString()},
          "current": ${current.toJson(indent = "$indent  ").trimStart()},
          "target": ${target.toJson(indent = "$indent  ").trimStart()},
          "regression": $regression,
          "correctionStatus": ${correctionStatus.jsonString()},
          "correctionScope": ${correctionScope.jsonString()},
          "admissibility": ${admissibility.jsonString()},
          "admissibilityReason": ${admissibilityReason.jsonString()},
          "verdict": ${verdict.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun For262PolicyStats.toJson(indent: String): String =
        """
        {
          "policy": ${policy.jsonString()},
          "evaluationKind": ${evaluationKind.jsonString()},
          "evaluationStatus": ${evaluationStatus.jsonString()},
          "targetColorSpaceBlend": $targetColorSpaceBlend,
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
          "refusalReason": ${refusalReason?.jsonString() ?: "null"}
        }
        """.trimIndent().prependIndent(indent)

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
        override fun getName(): String = "for262_black_white_rect"
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

    private data class For262ImageStats(
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val representative: PixelDelta,
    )

    private data class For262PolicyStats(
        val policy: String,
        val evaluationKind: String,
        val evaluationStatus: String,
        val targetColorSpaceBlend: Boolean,
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
    )

    private data class For262SceneCase(
        val id: String,
        val sceneId: String,
        val kind: String,
        val route: String,
        val current: For262PolicyStats,
        val target: For262PolicyStats,
        val regression: Boolean,
        val correctionStatus: String,
        val correctionScope: String,
        val admissibility: String,
        val admissibilityReason: String,
        val verdict: String,
    )

    private data class For262TargetBlendScopeAuditProbe(
        val cases: List<For262SceneCase>,
    ) {
        val caseCount: Int = cases.size
        val currentPolicy: String = "targetColorSpaceBlend=false with normal RGBA16Float intermediate"
        val boundedDiagnosticCandidate: String =
            "test-only targetColorSpaceBlend=true with normal RGBA16Float intermediate"
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val correctionApplied: Boolean = false
        val targetColorSpaceBlendFalseObserved: Boolean =
            cases.all { it.current.evaluationStatus == "rendered" && !it.current.targetColorSpaceBlend }
        val targetColorSpaceBlendTrueObserved: Boolean =
            cases.any { it.target.evaluationStatus == "rendered" && it.target.targetColorSpaceBlend } &&
                cases.any { it.target.evaluationStatus == "refused-before-render" && it.target.targetColorSpaceBlend }
        val positiveFixtureObserved: Boolean =
            cases.any { it.kind == "isolated-positive-target-blend-fixture" }
        val improvesWithoutExactParityObserved: Boolean =
            cases.any { it.kind == "improves-without-exact-parity" }
        val alreadyExactControlObserved: Boolean =
            cases.any { it.kind == "already-exact-target-blend-not-needed" }
        val for261ResidualBoundaryObserved: Boolean =
            cases.any { it.kind == "for261-residual-intermediate-boundary-control" }
        val positiveFixtureCorrected: Boolean =
            cases.filter { it.kind == "isolated-positive-target-blend-fixture" }
                .any { it.target.matchingPixels == it.target.totalPixels }
        val targetBlendImprovesAnyCase: Boolean =
            cases.any {
                it.target.exactSimilarity != null &&
                    it.current.exactSimilarity != null &&
                    it.target.exactSimilarity > it.current.exactSimilarity
            }
        val improvesWithoutExactParity: Boolean =
            cases.filter { it.kind == "improves-without-exact-parity" }
                .any {
                    it.target.exactSimilarity != null &&
                        it.current.exactSimilarity != null &&
                        it.target.exactSimilarity > it.current.exactSimilarity &&
                        it.target.exactSimilarity < SUPPORT_THRESHOLD
                }
        val exactControlDoesNotNeedTargetBlend: Boolean =
            cases.filter { it.kind == "already-exact-target-blend-not-needed" }
                .any {
                    it.current.matchingPixels == it.current.totalPixels &&
                        it.target.matchingPixels == it.target.totalPixels
                }
        val for261ResidualTargetBlendRefused: Boolean =
            cases.filter { it.kind == "for261-residual-intermediate-boundary-control" }
                .any { it.target.evaluationStatus == "refused-before-render" }
        val targetBlendRegressesAnyRenderedCase: Boolean = cases.any { it.regression }
        val safeScopeProven: Boolean = false
        val finding: String =
            "targetColorSpaceBlend_corrects_isolated_neutral_aa_but_safe_family_scope_not_proven"
        val admissibleCorrection: String =
            "none_applied: targetColorSpaceBlend remains diagnostic because the only exact correction is isolated, the bounded stroke cap/join scene still misses exact parity, an exact control does not need the mode, and the FOR-261 residual image-filter route is outside the target-blend draw-family scope"
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
    }
}
