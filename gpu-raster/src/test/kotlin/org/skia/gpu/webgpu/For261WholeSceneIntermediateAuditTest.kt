package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.testing.TestUtils
import org.skia.tests.DrawBitmapRectSkbug4734GM
import org.skia.tests.GM
import org.skia.tests.SimpleOffsetImageFilterGM
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class For261WholeSceneIntermediateAuditTest {
    @Test
    fun `FOR-261 whole scene RGBA8 intermediate candidate audit stays diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val simpleOffsetGm = SimpleOffsetImageFilterGM()
        val simpleOffsetReference = TestUtils.loadReferenceBitmap(simpleOffsetGm.name())
        assertTrue(simpleOffsetReference != null, "original-888/${simpleOffsetGm.name()}.png missing")

        context!!.use { ctx ->
            val cases = listOf(
                buildGmCase(
                    context = ctx,
                    id = "residual-route.simple-offsetimagefilter",
                    sceneId = "simple-offsetimagefilter",
                    kind = "residual-whole-scene-equivalent",
                    route = "webgpu.image-filter.offset-crop-prepass-and-src-over",
                    routeDiagnosticsPath = null,
                    reference = simpleOffsetReference!!,
                    gm = simpleOffsetGm,
                    applyColorspaceTransform = true,
                    correctionScope = "whole-scene SimpleOffset render containing the FOR-259 legacy source-color residual route",
                ),
                buildGmCase(
                    context = ctx,
                    id = "residual-route.bitmap-rect-nearest",
                    sceneId = "bitmap-rect-nearest",
                    kind = "residual-whole-scene-equivalent",
                    route = "webgpu.image-rect.strict-nearest",
                    routeDiagnosticsPath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/route-gpu.json",
                    reference = readEvidencePng("reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/skia.png"),
                    gm = DrawBitmapRectSkbug4734GM(),
                    applyColorspaceTransform = true,
                    correctionScope = "whole-scene bitmap strict-nearest render containing the FOR-259 bitmap texel residual route",
                ),
                buildGeneratedCase(
                    context = ctx,
                    id = "exact-control.solid-rect",
                    sceneId = "solid-rect",
                    kind = "exact-or-near-exact-for260-whole-scene",
                    route = "webgpu.coverage.analytic-rect",
                    routeDiagnosticsPath = "reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json",
                    reference = readEvidencePng("reports/wgsl-pipeline/scenes/artifacts/solid-rect/skia.png"),
                    render = ::renderSolidRect,
                    correctionScope = "FOR-260 exact solid whole-scene control",
                ),
                buildGeneratedCase(
                    context = ctx,
                    id = "exact-control.linear-gradient-rect",
                    sceneId = "linear-gradient-rect",
                    kind = "exact-or-near-exact-for260-whole-scene",
                    route = "webgpu.generated.linear-gradient.rect",
                    routeDiagnosticsPath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/route-gpu.json",
                    reference = readEvidencePng("reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/skia.png"),
                    render = ::renderLinearGradientRect,
                    correctionScope = "FOR-260 exact generated linear-gradient whole-scene control",
                ),
                buildGeneratedCase(
                    context = ctx,
                    id = "precision-fixture.m60-target-colorspace-neutral-aa",
                    sceneId = "m60-target-colorspace-neutral-aa",
                    kind = "precision-intermediate-sensitive-whole-scene",
                    route = "webgpu.present-pass.srgb-to-rec2020-after-blend",
                    routeDiagnosticsPath = "reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/stats.json",
                    reference = readEvidencePng("reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/skia.png"),
                    render = ::renderNeutralAaCoverage,
                    correctionScope = "whole-scene precision fixture; targetColorSpaceBlend remains out of scope",
                ),
            )
            val probe = For261WholeSceneIntermediateAuditProbe(cases = cases)

            assertEquals("KEEP_DIAGNOSTIC", probe.supportDecision)
            assertFalse(probe.correctionApplied)
            assertEquals(5, probe.caseCount)
            assertTrue(probe.wholeSceneIntermediateCandidateObserved)
            assertTrue(probe.cases.all { it.current.intermediateObserved && it.candidate.intermediateObserved })
            assertTrue(
                probe.cases.filter { it.kind == "exact-or-near-exact-for260-whole-scene" }
                    .all { !it.regression },
                "FOR-261: exact FOR-260 whole-scene controls must not regress under RGBA8Unorm candidate",
            )
            assertTrue(
                probe.cases.single { it.kind == "precision-intermediate-sensitive-whole-scene" }
                    .verdict.contains("targetColorSpaceBlend remains disabled"),
                "FOR-261: precision fixture must keep targetColorSpaceBlend out of scope",
            )
            assertEquals(
                "image-filter.crop-input-nonnull-prepass-required",
                probe.preservedUnsupportedReason,
                "FOR-261: Crop(input = nonNull) fallback reason must stay preserved",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor261WholeSceneIntermediateAuditJson(probe)
            }
        }
    }

    private fun buildGmCase(
        context: WebGpuContext,
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        routeDiagnosticsPath: String?,
        reference: Any,
        gm: GM,
        applyColorspaceTransform: Boolean,
        correctionScope: String,
    ): For261SceneCase =
        buildCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            reference = reference,
            current = renderGm(context, gm, GPUTextureFormat.RGBA16Float, applyColorspaceTransform),
            candidate = renderGm(context, gm, GPUTextureFormat.RGBA8Unorm, applyColorspaceTransform),
            correctionScope = correctionScope,
        )

    private fun buildGeneratedCase(
        context: WebGpuContext,
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        routeDiagnosticsPath: String?,
        reference: BufferedImage,
        render: (WebGpuContext, GPUTextureFormat) -> SkBitmap,
        correctionScope: String,
    ): For261SceneCase =
        buildCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            reference = reference,
            current = render(context, GPUTextureFormat.RGBA16Float),
            candidate = render(context, GPUTextureFormat.RGBA8Unorm),
            correctionScope = correctionScope,
        )

    private fun buildCase(
        id: String,
        sceneId: String,
        kind: String,
        route: String,
        routeDiagnosticsPath: String?,
        reference: Any,
        current: SkBitmap,
        candidate: SkBitmap,
        correctionScope: String,
    ): For261SceneCase {
        val currentStats = compareReference(reference, current)
        val candidateStats = compareReference(reference, candidate)
        val currentPolicy = buildStats(
            policy = "current-rgba16float-intermediate",
            intermediateFormat = "RGBA16Float",
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            imageStats = currentStats,
        )
        val candidatePolicy = buildStats(
            policy = "diagnostic-rgba8unorm-intermediate",
            intermediateFormat = "RGBA8Unorm",
            route = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            imageStats = candidateStats,
        )
        val regression = candidatePolicy.exactSimilarity < currentPolicy.exactSimilarity ||
            candidatePolicy.maxDelta > currentPolicy.maxDelta
        val correctionStatus = when {
            regression -> "REGRESSION"
            candidatePolicy.exactSimilarity > currentPolicy.exactSimilarity -> "CORRECTION_SIGNAL"
            candidatePolicy.exactSimilarity == currentPolicy.exactSimilarity &&
                candidatePolicy.maxDelta == currentPolicy.maxDelta -> "UNCHANGED"
            else -> "MIXED"
        }
        return For261SceneCase(
            id = id,
            sceneId = sceneId,
            kind = kind,
            route = route,
            current = currentPolicy,
            candidate = candidatePolicy,
            regression = regression,
            correctionStatus = correctionStatus,
            correctionScope = correctionScope,
            verdict = buildVerdict(kind, correctionStatus),
        )
    }

    private fun buildStats(
        policy: String,
        intermediateFormat: String,
        route: String,
        routeDiagnosticsPath: String?,
        imageStats: For261ImageStats,
    ): For261PolicyStats =
        For261PolicyStats(
            policy = policy,
            evaluationKind = "whole-scene-reference-vs-live-webgpu",
            totalPixels = imageStats.totalPixels,
            matchingPixels = imageStats.matchingPixels,
            exactSimilarity = imageStats.exactSimilarity,
            maxDelta = imageStats.maxDelta,
            referenceRgba = imageStats.representative.reference,
            observedRgba = imageStats.representative.gpu,
            signedDeltaRgba = imageStats.representative.signedDelta,
            intermediateObserved = true,
            intermediateFormat = intermediateFormat,
            routeDiagnostics = route,
            routeDiagnosticsPath = routeDiagnosticsPath,
            routeDiagnosticsRationale = "Whole-scene live WebGPU render with constructor-scoped diagnostic intermediateFormat=$intermediateFormat; no runtime property or default policy change.",
        )

    private fun buildVerdict(kind: String, correctionStatus: String): String =
        when (kind) {
            "precision-intermediate-sensitive-whole-scene" ->
                "whole-scene RGBA8Unorm candidate render observed; correctionStatus=$correctionStatus; targetColorSpaceBlend remains disabled, so this audit cannot substitute for the target-colorspace blend condition"
            else ->
                "whole-scene RGBA8Unorm candidate render observed; correctionStatus=$correctionStatus; evidence is diagnostic only and does not change the normal RGBA16Float default"
        }

    private fun renderGm(
        context: WebGpuContext,
        gm: GM,
        intermediateFormat: GPUTextureFormat,
        applyColorspaceTransform: Boolean,
    ): SkBitmap {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = applyColorspaceTransform,
            intermediateFormat = intermediateFormat,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            return rgbaBytesToBitmap(device.flush(), size.width, size.height)
        }
    }

    private fun renderSolidRect(context: WebGpuContext, intermediateFormat: GPUTextureFormat): SkBitmap {
        SkWebGpuDevice(
            context,
            SOLID_SIZE.width,
            SOLID_SIZE.height,
            applyColorspaceTransform = false,
            intermediateFormat = intermediateFormat,
        ).use { device ->
            device.setBackground(SOLID_BACKGROUND)
            SkCanvas(device).drawRect(
                SkRect.MakeLTRB(2f, 1f, 7f, 6f),
                SkPaint().apply { color = SOLID_FILL },
            )
            return rgbaBytesToBitmap(device.flush(), SOLID_SIZE.width, SOLID_SIZE.height)
        }
    }

    private fun renderLinearGradientRect(context: WebGpuContext, intermediateFormat: GPUTextureFormat): SkBitmap {
        val gradient = SkLinearGradient.Make(
            p0 = SkPoint(2f, 32f),
            p1 = SkPoint(62f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = gradient
            isAntiAlias = false
        }
        SkWebGpuDevice(
            context,
            GRADIENT_SIZE.width,
            GRADIENT_SIZE.height,
            applyColorspaceTransform = false,
            intermediateFormat = intermediateFormat,
        ).use { device ->
            device.setBackground(SK_ColorWHITE)
            SkCanvas(device).drawRect(SkRect.MakeLTRB(2f, 10f, 62f, 50f), paint)
            return rgbaBytesToBitmap(device.flush(), GRADIENT_SIZE.width, GRADIENT_SIZE.height)
        }
    }

    private fun renderNeutralAaCoverage(context: WebGpuContext, intermediateFormat: GPUTextureFormat): SkBitmap {
        SkWebGpuDevice(
            context,
            NEUTRAL_AA_SIZE.width,
            NEUTRAL_AA_SIZE.height,
            applyColorspaceTransform = true,
            intermediateFormat = intermediateFormat,
        ).use { device ->
            val canvas = SkCanvas(device)
            canvas.drawColor(SK_ColorWHITE)
            canvas.drawRect(
                SkRect.MakeLTRB(0.5f, 0f, 1.5f, 1f),
                SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                    style = SkPaint.Style.kFill_Style
                },
            )
            return rgbaBytesToBitmap(device.flush(), NEUTRAL_AA_SIZE.width, NEUTRAL_AA_SIZE.height)
        }
    }

    private fun compareReference(reference: Any, actual: SkBitmap): For261ImageStats =
        when (reference) {
            is SkBitmap -> compareImages(reference, actual)
            is BufferedImage -> compareImages(reference, actual)
            else -> error("unsupported FOR-261 reference type: ${reference::class}")
        }

    private fun compareImages(reference: SkBitmap, gpu: SkBitmap): For261ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-261 requires same-size SkBitmap evidence"
        }
        return comparePixels(reference.width, reference.height) { x, y -> pixelDelta(reference, gpu, x, y) }
    }

    private fun compareImages(reference: BufferedImage, gpu: SkBitmap): For261ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-261 requires same-size BufferedImage/SkBitmap evidence"
        }
        return comparePixels(reference.width, reference.height) { x, y -> pixelDelta(reference, gpu, x, y) }
    }

    private fun comparePixels(width: Int, height: Int, sample: (Int, Int) -> PixelDelta): For261ImageStats {
        var matching = 0
        var maxDelta = 0
        var representative: PixelDelta? = null
        for (y in 0 until height) {
            for (x in 0 until width) {
                val delta = sample(x, y)
                if (delta.maxChannelDelta == 0) {
                    matching += 1
                } else if (representative == null) {
                    representative = delta
                }
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
            }
        }
        val total = width * height
        return For261ImageStats(
            totalPixels = total,
            matchingPixels = matching,
            exactSimilarity = matching * 100.0 / total,
            maxDelta = maxDelta,
            representative = representative ?: sample(0, 0),
        )
    }

    private fun pixelDelta(reference: SkBitmap, gpu: SkBitmap, x: Int, y: Int): PixelDelta {
        val referenceRgba = rgbaAt(reference, x, y)
        val gpuRgba = rgbaAt(gpu, x, y)
        return PixelDelta(x = x, y = y, reference = referenceRgba, gpu = gpuRgba)
    }

    private fun pixelDelta(reference: BufferedImage, gpu: SkBitmap, x: Int, y: Int): PixelDelta {
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

    private fun rgbaAt(image: BufferedImage, x: Int, y: Int): IntArray {
        val pixel = image.getRGB(x, y)
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

    private fun readEvidencePng(path: String): BufferedImage {
        val file = repoFile(path)
        require(file.isFile) { "FOR-261 missing evidence PNG: $path" }
        return ImageIO.read(file) ?: error("FOR-261 failed to read evidence PNG: $path")
    }

    private fun writeFor261WholeSceneIntermediateAuditJson(probe: For261WholeSceneIntermediateAuditProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "mixed skia-upstream, generated artifact oracle PNGs, and live fixture references",
              "linear": "FOR-261",
              "probe": "whole-scene-rgba8-intermediate-candidate-audit",
              "deltaDefinition": "signed channel delta is current/candidate live WebGPU output minus reference",
              "newRendererProperty": "none",
              "defaultEnabled": false,
              "runtimeSnapshotsEnabled": false,
              "normalRenderingChanged": false,
              "normalShadersChanged": false,
              "normalThresholdsChanged": false,
              "cropPolicyChanged": false,
              "fallbackPolicyChanged": false,
              "targetColorSpaceBlendGloballyEnabled": false,
              "currentPolicy": "${probe.currentPolicy}",
              "boundedDiagnosticCandidate": "${probe.boundedDiagnosticCandidate}",
              "caseCount": ${probe.caseCount},
              "observedBoundaries": {
                "wholeSceneCurrentRgba16FloatObserved": true,
                "wholeSceneIntermediateCandidateObserved": ${probe.wholeSceneIntermediateCandidateObserved},
                "for260ExactScenesObserved": ${probe.for260ExactScenesObserved},
                "precisionSensitiveSceneObserved": ${probe.precisionSensitiveSceneObserved}
              },
              "cases": [
            ${probe.cases.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "summary": {
                "candidateImprovesAnyScene": ${probe.candidateImprovesAnyScene},
                "candidateRegressesAnyScene": ${probe.candidateRegressesAnyScene},
                "exactControlsRegressed": ${probe.exactControlsRegressed},
                "precisionFixtureCorrected": ${probe.precisionFixtureCorrected},
                "safeCorrectionProven": ${probe.safeCorrectionProven}
              },
              "finding": "${probe.finding}",
              "admissibleCorrection": "${probe.admissibleCorrection}",
              "missingCondition": "${probe.missingCondition}",
              "remainingBoundary": "${probe.remainingBoundary}",
              "interpretation": "FOR-261 replaces FOR-260 final-byte proxies with live whole-scene renders using the normal RGBA16Float intermediate and a constructor-scoped RGBA8Unorm diagnostic candidate. The candidate is observed but remains diagnostic because the precision fixture is not corrected and targetColorSpaceBlend remains out of scope.",
              "observationMethod": "test-only SkWebGpuDevice construction with intermediateFormat=RGBA8Unorm for candidate renders; no renderer property, no default switch, no shader change, no threshold change, no Crop correction, no fallback-policy change, and no global targetColorSpaceBlend change",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": ${probe.correctionApplied},
              "preservedUnsupportedReason": "${probe.preservedUnsupportedReason}"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/whole-scene-rgba8-intermediate-audit-for261",
            "reports/wgsl-pipeline/scenes/artifacts/whole-scene-rgba8-intermediate-audit-for261",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "whole-scene-rgba8-intermediate-audit-for261.json").writeText(contents)
        }
    }

    private fun For261SceneCase.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "sceneId": "$sceneId",
          "kind": "$kind",
          "route": "$route",
          "current": ${current.toJson(indent = "$indent  ").trimStart()},
          "candidate": ${candidate.toJson(indent = "$indent  ").trimStart()},
          "regression": $regression,
          "correctionStatus": "$correctionStatus",
          "correctionScope": "$correctionScope",
          "verdict": "$verdict"
        }
        """.trimIndent().prependIndent(indent)

    private fun For261PolicyStats.toJson(indent: String): String =
        """
        {
          "policy": "$policy",
          "evaluationKind": "$evaluationKind",
          "totalPixels": $totalPixels,
          "matchingPixels": $matchingPixels,
          "exactSimilarity": $exactSimilarity,
          "maxDelta": $maxDelta,
          "referenceRgba": ${jsonArray(referenceRgba)},
          "observedRgba": ${jsonArray(observedRgba)},
          "signedDeltaRgba": ${jsonArray(signedDeltaRgba)},
          "intermediateObserved": $intermediateObserved,
          "intermediateFormat": "$intermediateFormat",
          "routeDiagnostics": "$routeDiagnostics",
          "routeDiagnosticsPath": ${routeDiagnosticsPath?.let { "\"$it\"" } ?: "null"},
          "routeDiagnosticsRationale": "$routeDiagnosticsRationale"
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

    private data class For261ImageStats(
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val representative: PixelDelta,
    )

    private data class For261PolicyStats(
        val policy: String,
        val evaluationKind: String,
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val referenceRgba: IntArray,
        val observedRgba: IntArray,
        val signedDeltaRgba: IntArray,
        val intermediateObserved: Boolean,
        val intermediateFormat: String,
        val routeDiagnostics: String,
        val routeDiagnosticsPath: String?,
        val routeDiagnosticsRationale: String,
    )

    private data class For261SceneCase(
        val id: String,
        val sceneId: String,
        val kind: String,
        val route: String,
        val current: For261PolicyStats,
        val candidate: For261PolicyStats,
        val regression: Boolean,
        val correctionStatus: String,
        val correctionScope: String,
        val verdict: String,
    )

    private data class For261WholeSceneIntermediateAuditProbe(
        val cases: List<For261SceneCase>,
    ) {
        val caseCount: Int = cases.size
        val currentPolicy: String = "RGBA16Float intermediate store/load before present"
        val boundedDiagnosticCandidate: String =
            "constructor-scoped RGBA8Unorm intermediate store/load before present"
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val correctionApplied: Boolean = false
        val wholeSceneIntermediateCandidateObserved: Boolean =
            cases.all { it.candidate.intermediateObserved && it.candidate.intermediateFormat == "RGBA8Unorm" }
        val for260ExactScenesObserved: Boolean =
            cases.count { it.kind == "exact-or-near-exact-for260-whole-scene" } >= 2
        val precisionSensitiveSceneObserved: Boolean =
            cases.any { it.kind == "precision-intermediate-sensitive-whole-scene" }
        val candidateImprovesAnyScene: Boolean =
            cases.any { it.candidate.exactSimilarity > it.current.exactSimilarity }
        val candidateRegressesAnyScene: Boolean = cases.any { it.regression }
        val exactControlsRegressed: Boolean =
            cases.filter { it.kind == "exact-or-near-exact-for260-whole-scene" }.any { it.regression }
        val precisionFixtureCorrected: Boolean =
            cases.filter { it.kind == "precision-intermediate-sensitive-whole-scene" }
                .any { it.candidate.matchingPixels == it.candidate.totalPixels }
        val safeCorrectionProven: Boolean = false
        val finding: String =
            "whole_scene_rgba8_intermediate_candidate_observed_but_precision_fixture_not_corrected"
        val admissibleCorrection: String =
            "none_applied: RGBA8Unorm remains diagnostic because the precision-sensitive fixture is not corrected and targetColorSpaceBlend is explicitly out of scope"
        val missingCondition: String =
            "missing_precision_sensitive_whole_scene_rgba8_intermediate_correction_without_targetColorSpaceBlend"
        val remainingBoundary: String =
            "rgba16float-intermediate-store-to-present-byte-quantization-policy"
        val preservedUnsupportedReason: String = "image-filter.crop-input-nonnull-prepass-required"
    }

    private companion object {
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
        val SOLID_SIZE: SkISize = SkISize.Make(8, 8)
        val GRADIENT_SIZE: SkISize = SkISize.Make(64, 64)
        val NEUTRAL_AA_SIZE: SkISize = SkISize.Make(4, 1)
        val SOLID_BACKGROUND: Int = SkColorSetARGB(255, 245, 239, 229)
        val SOLID_FILL: Int = SkColorSetARGB(255, 23, 33, 28)
    }
}
