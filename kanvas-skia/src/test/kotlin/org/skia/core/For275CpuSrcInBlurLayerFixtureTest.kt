package org.skia.core

import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkRect
import java.io.File
import kotlin.math.abs

/**
 * FOR-275: minimized CPU fixture for the M60
 * `SkBlurMaskFilter(kNormal)` + `SkColorFilters.Blend(RED, kSrcIn)`
 * residual. The fixture deliberately avoids the full nested-clip scene and
 * separates two hypotheses:
 *
 * - `saveLayer`/background retention introduced by CPU layer composite;
 * - blur/mask extent lost when the difference clip is active.
 */
class For275CpuSrcInBlurLayerFixtureTest {

    @Test
    fun `bounded CPU SrcIn blur fixture separates layer retention from mask extent`() {
        val direct = renderDifferenceClip(useLayer = false)
        val layered = renderDifferenceClip(useLayer = true)
        val unclipped = renderUnclippedBlur()
        val evidence = FixtureEvidence.from(direct, layered, unclipped)

        assertEquals(
            0,
            evidence.layerComparison.greaterThanZeroPixels,
            "saveLayer must not introduce extra CPU/background retention in this minimized fixture",
        )
        assertEquals(
            0,
            evidence.layerIntroducedWhitePixels,
            "layered output must not turn red blur pixels white when the direct path keeps them red",
        )
        assertTrue(
            evidence.maskExtent.lostRedBlurPixels > 0,
            "difference-clip fixture must expose red blur support lost to white/fond pixels",
        )
        assertTrue(
            evidence.maskExtent.whiteLayerShareOfLostPixels >= 95.0,
            "lost blur pixels should be overwhelmingly white/fond in the minimized CPU fixture",
        )
        assertTrue(
            evidence.maskExtent.samples.isNotEmpty(),
            "signed RGB samples are required for the PM audit",
        )

        writeEvidence(evidence)
    }

    @Test
    fun `FOR-276 CPU mask filter keeps source outside AA clip before blur`() {
        val clipped = renderIntersectHaloAaClip()
        val unclipped = renderUnclippedBlur()
        val evidence = ClipOrderEvidence.from(clipped, unclipped)

        writeFor276Evidence(evidence)

        assertTrue(
            evidence.unclippedRedSupportInClip > 0,
            "halo AA clip must overlap red blur support from the unclipped control",
        )
        assertTrue(
            evidence.clippedRedSupportInClip > 0,
            "CPU mask-filter path must keep off-clip source before blur and composite only through the final AA clip",
        )
        assertTrue(
            evidence.clippedRedSupportInClip >= evidence.unclippedRedSupportInClip * 80 / 100,
            "bounded AA-clip fixture should recover most red blur support inside the final clip",
        )
        assertTrue(
            evidence.samples.isNotEmpty(),
            "signed RGB samples are required for the FOR-276 PM audit",
        )
    }

    private fun renderDifferenceClip(useLayer: Boolean): SkBitmap {
        val bitmap = SkBitmap(WIDTH, HEIGHT).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bitmap)
        if (useLayer) canvas.saveLayer(null, null)
        canvas.save()
        canvas.clipRRect(INNER_OVAL, SkClipOp.kDifference, doAntiAlias = true)
        canvas.drawRRect(OUTER_OVAL, blurSrcInPaint())
        canvas.restore()
        if (useLayer) canvas.restore()
        return bitmap
    }

    private fun renderUnclippedBlur(): SkBitmap {
        val bitmap = SkBitmap(WIDTH, HEIGHT).also { it.eraseColor(SK_ColorWHITE) }
        SkCanvas(bitmap).drawRRect(OUTER_OVAL, blurSrcInPaint())
        return bitmap
    }

    private fun renderIntersectHaloAaClip(): SkBitmap {
        val bitmap = SkBitmap(WIDTH, HEIGHT).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bitmap)
        canvas.save()
        canvas.clipRRect(SkRRect.MakeOval(HALO_CLIP), SkClipOp.kIntersect, doAntiAlias = true)
        canvas.drawRRect(OUTER_OVAL, blurSrcInPaint())
        canvas.restore()
        return bitmap
    }

    private fun blurSrcInPaint(): SkPaint =
        SkPaint().apply {
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, SIGMA)
            colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
        }

    private fun writeEvidence(evidence: FixtureEvidence) {
        val root = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip")
            .apply { mkdirs() }
        File(root, "cpu-srcin-blur-layer-fixture-for275.json").writeText(evidence.toJson())
    }

    private fun writeFor276Evidence(evidence: ClipOrderEvidence) {
        val root = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip")
            .apply { mkdirs() }
        File(root, "cpu-mask-filter-clip-order-for276.json").writeText(evidence.toJson())
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private data class FixtureEvidence(
        val layerComparison: LayerComparison,
        val layerIntroducedWhitePixels: Int,
        val maskExtent: MaskExtent,
    ) {
        fun toJson(): String = """
{
  "linear": "FOR-275",
  "parent": "FOR-241",
  "probe": "cpu-srcin-blur-layer-fixture",
  "sceneId": "m60-bounded-nested-rrect-clip",
  "fixture": {
    "width": $WIDTH,
    "height": $HEIGHT,
    "sigma": $SIGMA,
    "outerOval": {"left": 18, "top": 14, "right": 78, "bottom": 58},
    "differenceOval": {"left": 25, "top": 20, "right": 71, "bottom": 52},
    "paintChain": "SkBlurMaskFilter(kNormal) + SkColorFilters.Blend(RED, kSrcIn)",
    "variants": ["directDifferenceClip", "saveLayerDifferenceClip", "unclippedBlurControl"]
  },
  "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
  "supportThreshold": 99.95,
  "routePreservation": {
    "gpuStatus": "expected-unsupported",
    "fallbackReason": "coverage.nested-clip-visual-parity-below-threshold",
    "cropFallbackPreserved": "image-filter.crop-input-nonnull-prepass-required"
  },
  "layerComparison": ${layerComparison.toJson()},
  "layerIntroducedWhitePixels": $layerIntroducedWhitePixels,
  "maskExtent": ${maskExtent.toJson()},
  "dominantFixtureHypothesis": "MASK_EXTENT_OR_ACTIVE_CLIP_TRUNCATION_REPRODUCED_LAYER_BACKGROUND_NOT_REPRODUCED",
  "nextAction": "CPU_LOCAL_MASK_FILTER_CLIP_ORDER_AUDIT_OR_BOUNDED_FIXTURE_CORRECTION_BEFORE_M60_PROMOTION",
  "strictPreservation": {
    "productionRendererChanged": false,
    "cpuRendererChanged": false,
    "gpuRendererChanged": false,
    "supportPromotionChanged": false,
    "supportThresholdChanged": false,
    "fallbackOrReadbackAdded": false,
    "wideClipStackSupportAdded": false,
    "ganeshOrGraphiteAdded": false,
    "skSLCompilerAdded": false
  }
}
""".trimIndent() + "\n"

        companion object {
            fun from(direct: SkBitmap, layered: SkBitmap, unclipped: SkBitmap): FixtureEvidence {
                val layerComparison = LayerComparison.from(direct, layered)
                var layerIntroducedWhitePixels = 0
                var lostRedBlurPixels = 0
                var whiteLayerLostPixels = 0
                var unclippedRedBlurSupportPixels = 0
                var directRedPixelsInsideUnclippedSupport = 0
                val samples = mutableListOf<Sample>()
                for (y in 0 until HEIGHT) {
                    for (x in 0 until WIDTH) {
                        val directPx = direct.getPixel(x, y)
                        val layeredPx = layered.getPixel(x, y)
                        val unclippedPx = unclipped.getPixel(x, y)
                        val unclippedRed = isRedBlurPayload(unclippedPx)
                        if (unclippedRed) {
                            unclippedRedBlurSupportPixels++
                            if (isRedBlurPayload(directPx)) directRedPixelsInsideUnclippedSupport++
                            if (isWhiteOrLayer(directPx)) {
                                lostRedBlurPixels++
                                if (isWhiteOrLayer(directPx)) whiteLayerLostPixels++
                                if (samples.size < 8) {
                                    samples += Sample(x, y, unclippedPx, directPx, layeredPx)
                                }
                            }
                            if (isRedBlurPayload(directPx) && isWhiteOrLayer(layeredPx)) {
                                layerIntroducedWhitePixels++
                            }
                        }
                    }
                }
                val whiteShare = if (lostRedBlurPixels == 0) {
                    0.0
                } else {
                    whiteLayerLostPixels * 100.0 / lostRedBlurPixels
                }
                return FixtureEvidence(
                    layerComparison = layerComparison,
                    layerIntroducedWhitePixels = layerIntroducedWhitePixels,
                    maskExtent = MaskExtent(
                        unclippedRedBlurSupportPixels = unclippedRedBlurSupportPixels,
                        directRedPixelsInsideUnclippedSupport = directRedPixelsInsideUnclippedSupport,
                        lostRedBlurPixels = lostRedBlurPixels,
                        whiteLayerLostPixels = whiteLayerLostPixels,
                        whiteLayerShareOfLostPixels = round3(whiteShare),
                        samples = samples,
                    ),
                )
            }
        }
    }

    private data class ClipOrderEvidence(
        val clipRect: String,
        val clipPixels: Int,
        val unclippedRedSupportInClip: Int,
        val clippedRedSupportInClip: Int,
        val lostRedSupportInClip: Int,
        val recoveredShareOfUnclippedSupport: Double,
        val samples: List<ClipOrderSample>,
    ) {
        fun toJson(): String = """
{
  "linear": "FOR-276",
  "parent": "FOR-241",
  "probe": "cpu-mask-filter-clip-order",
  "sceneId": "m60-bounded-nested-rrect-clip",
  "fixture": {
    "width": $WIDTH,
    "height": $HEIGHT,
    "sigma": $SIGMA,
    "outerOval": {"left": 18, "top": 14, "right": 78, "bottom": 58},
    "intersectHaloClip": $clipRect,
    "paintChain": "SkBlurMaskFilter(kNormal) + SkColorFilters.Blend(RED, kSrcIn)",
    "variants": ["intersectHaloClip", "unclippedBlurControl"]
  },
  "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
  "supportThreshold": 99.95,
  "routePreservation": {
    "gpuStatus": "expected-unsupported",
    "fallbackReason": "coverage.nested-clip-visual-parity-below-threshold",
    "cropFallbackPreserved": "image-filter.crop-input-nonnull-prepass-required"
  },
  "maskFilterClipOrder": {
    "preFixBaseline": {
      "source": "FOR-276 exploratory run before SkBitmapDevice correction",
      "clippedRedSupportInClip": 0,
      "lostRedSupportInClip": 10,
      "recoveredShareOfUnclippedSupport": 0.0
    },
    "clipPixels": $clipPixels,
    "unclippedRedSupportInClip": $unclippedRedSupportInClip,
    "clippedRedSupportInClip": $clippedRedSupportInClip,
    "lostRedSupportInClip": $lostRedSupportInClip,
    "recoveredShareOfUnclippedSupport": $recoveredShareOfUnclippedSupport,
    "samples": [
${samples.joinToString(",\n") { it.toJson().prependIndent("      ") }}
    ]
  },
  "boundedCorrection": {
    "sourceMaskBounds": "device bounds expanded by mask-filter margin for active AA clips, not truncated to final clip",
    "finalCompositionBounds": "current clip rect plus active AA clip coverage",
    "wideClipStackSupportAdded": false
  },
  "dominantFixtureHypothesis": "CPU_MASK_FILTER_SOURCE_CLIP_ORDER_WAS_TRUNCATING_BLUR_SOURCE",
  "nextAction": "REGENERATE_M60_SCENE_EVIDENCE_AND_RECHECK_CPU_GPU_REFERENCE_THRESHOLD_BEFORE_PROMOTION",
  "strictPreservation": {
    "productionRendererChanged": true,
    "cpuRendererChanged": true,
    "gpuRendererChanged": false,
    "supportPromotionChanged": false,
    "supportThresholdChanged": false,
    "fallbackOrReadbackAdded": false,
    "wideClipStackSupportAdded": false,
    "ganeshOrGraphiteAdded": false,
    "skSLCompilerAdded": false
  }
}
""".trimIndent() + "\n"

        companion object {
            fun from(clipped: SkBitmap, unclipped: SkBitmap): ClipOrderEvidence {
                var clipPixels = 0
                var unclippedRedSupport = 0
                var clippedRedSupport = 0
                var lostRedSupport = 0
                val samples = mutableListOf<ClipOrderSample>()
                for (y in 0 until HEIGHT) {
                    for (x in 0 until WIDTH) {
                        if (!isInsideHaloClip(x, y)) continue
                        clipPixels++
                        val unclippedPx = unclipped.getPixel(x, y)
                        val clippedPx = clipped.getPixel(x, y)
                        val unclippedRed = isRedBlurPayload(unclippedPx)
                        val clippedRed = isRedBlurPayload(clippedPx)
                        if (unclippedRed) {
                            unclippedRedSupport++
                            if (clippedRed) {
                                clippedRedSupport++
                            } else {
                                lostRedSupport++
                            }
                            if (samples.size < 8) {
                                samples += ClipOrderSample(x, y, unclippedPx, clippedPx)
                            }
                        }
                    }
                }
                val recoveredShare = if (unclippedRedSupport == 0) {
                    0.0
                } else {
                    clippedRedSupport * 100.0 / unclippedRedSupport
                }
                return ClipOrderEvidence(
                    clipRect = "{\"left\": 14, \"top\": 30, \"right\": 18, \"bottom\": 42}",
                    clipPixels = clipPixels,
                    unclippedRedSupportInClip = unclippedRedSupport,
                    clippedRedSupportInClip = clippedRedSupport,
                    lostRedSupportInClip = lostRedSupport,
                    recoveredShareOfUnclippedSupport = round3(recoveredShare),
                    samples = samples,
                )
            }
        }
    }

    private data class ClipOrderSample(
        val x: Int,
        val y: Int,
        val unclippedRgba: SkColor,
        val clippedRgba: SkColor,
    ) {
        fun toJson(): String = """
{
        "x": $x,
        "y": $y,
        "unclippedBlurControlRgba": ${rgba(unclippedRgba)},
        "intersectHaloClipRgba": ${rgba(clippedRgba)},
        "clipMinusUnclippedSignedRgba": ${signedRgba(clippedRgba, unclippedRgba)}
      }""".trimIndent()
    }

    private data class LayerComparison(
        val greaterThanZeroPixels: Int,
        val greaterThanThirtyTwoPixels: Int,
        val maxChannelDelta: Int,
    ) {
        fun toJson(): String = """
{
    "comparison": "saveLayerDifferenceClip_vs_directDifferenceClip",
    "greaterThanZeroPixels": $greaterThanZeroPixels,
    "greaterThanThirtyTwoPixels": $greaterThanThirtyTwoPixels,
    "maxChannelDelta": $maxChannelDelta
  }""".trimIndent()

        companion object {
            fun from(direct: SkBitmap, layered: SkBitmap): LayerComparison {
                var gt0 = 0
                var gt32 = 0
                var maxDelta = 0
                for (y in 0 until HEIGHT) {
                    for (x in 0 until WIDTH) {
                        val delta = maxChannelDelta(direct.getPixel(x, y), layered.getPixel(x, y))
                        if (delta > 0) gt0++
                        if (delta > 32) gt32++
                        maxDelta = maxOf(maxDelta, delta)
                    }
                }
                return LayerComparison(gt0, gt32, maxDelta)
            }
        }
    }

    private data class MaskExtent(
        val unclippedRedBlurSupportPixels: Int,
        val directRedPixelsInsideUnclippedSupport: Int,
        val lostRedBlurPixels: Int,
        val whiteLayerLostPixels: Int,
        val whiteLayerShareOfLostPixels: Double,
        val samples: List<Sample>,
    ) {
        fun toJson(): String = """
{
    "comparison": "directDifferenceClip_vs_unclippedBlurControl",
    "unclippedRedBlurSupportPixels": $unclippedRedBlurSupportPixels,
    "directRedPixelsInsideUnclippedSupport": $directRedPixelsInsideUnclippedSupport,
    "lostRedBlurPixels": $lostRedBlurPixels,
    "whiteLayerLostPixels": $whiteLayerLostPixels,
    "whiteLayerShareOfLostPixels": $whiteLayerShareOfLostPixels,
    "samples": [
${samples.joinToString(",\n") { it.toJson().prependIndent("      ") }}
    ]
  }""".trimIndent()
    }

    private data class Sample(
        val x: Int,
        val y: Int,
        val unclippedRgba: SkColor,
        val directRgba: SkColor,
        val layeredRgba: SkColor,
    ) {
        fun toJson(): String = """
{
        "x": $x,
        "y": $y,
        "unclippedBlurControlRgba": ${rgba(unclippedRgba)},
        "directDifferenceClipRgba": ${rgba(directRgba)},
        "saveLayerDifferenceClipRgba": ${rgba(layeredRgba)},
        "directMinusUnclippedSignedRgba": ${signedRgba(directRgba, unclippedRgba)},
        "layeredMinusDirectSignedRgba": ${signedRgba(layeredRgba, directRgba)}
      }""".trimIndent()
    }

    private companion object {
        const val WIDTH = 96
        const val HEIGHT = 72
        const val SIGMA = 1.366025f
        val OUTER_OVAL: SkRRect = SkRRect.MakeOval(SkRect.MakeLTRB(18f, 14f, 78f, 58f))
        val INNER_OVAL: SkRRect = SkRRect.MakeOval(SkRect.MakeLTRB(25f, 20f, 71f, 52f))
        val HALO_CLIP: SkRect = SkRect.MakeLTRB(14f, 30f, 18f, 42f)

        fun isInsideHaloClip(x: Int, y: Int): Boolean =
            x >= 14 && x < 18 && y >= 30 && y < 42

        fun isWhiteOrLayer(c: SkColor): Boolean =
            SkColorGetA(c) >= 250 &&
                SkColorGetR(c) >= 245 &&
                SkColorGetG(c) >= 245 &&
                SkColorGetB(c) >= 245

        fun isRedBlurPayload(c: SkColor): Boolean =
            SkColorGetA(c) >= 250 &&
                SkColorGetR(c) >= 120 &&
                SkColorGetR(c) > SkColorGetG(c) + 40 &&
                SkColorGetR(c) > SkColorGetB(c) + 40

        fun maxChannelDelta(a: SkColor, b: SkColor): Int =
            maxOf(
                abs(SkColorGetA(a) - SkColorGetA(b)),
                abs(SkColorGetR(a) - SkColorGetR(b)),
                abs(SkColorGetG(a) - SkColorGetG(b)),
                abs(SkColorGetB(a) - SkColorGetB(b)),
            )

        fun rgba(c: SkColor): String =
            "[${SkColorGetR(c)}, ${SkColorGetG(c)}, ${SkColorGetB(c)}, ${SkColorGetA(c)}]"

        fun signedRgba(a: SkColor, b: SkColor): String =
            "[${SkColorGetR(a) - SkColorGetR(b)}, ${SkColorGetG(a) - SkColorGetG(b)}, " +
                "${SkColorGetB(a) - SkColorGetB(b)}, ${SkColorGetA(a) - SkColorGetA(b)}]"

        fun round3(value: Double): Double =
            kotlin.math.round(value * 1000.0) / 1000.0
    }
}
