package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.math.SkMatrix
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.ceil
import kotlin.math.min

/**
 * Port of Skia's
 * [`gm/kawase_blur_rt.cpp`](https://github.com/google/skia/blob/main/gm/kawase_blur_rt.cpp).
 *
 * Implements the Kawase iterative blur filter via two SkSL
 * runtime shaders (registered by
 * [SkBuiltinSpecialisedEffects.KawaseBlurShaderImpl] and
 * [SkBuiltinSpecialisedEffects.KawaseMixShaderImpl]).
 *
 * **Algorithm** :
 *  1. Downsample the source image by 1/4 into a working surface.
 *  2. Apply N passes of the 5-tap Kawase blur shader, ping-ponging
 *     between two surfaces. Each pass uses a progressively wider
 *     blur offset.
 *  3. Cross-fade the blurred result with the original via the mix
 *     shader.
 *
 * **Adaptation vs upstream** :
 *  - **No mandrill** : we use a synthetic 256×256 RGB-gradient
 *    image (consistent with `RuntimeColorFilterGM` etc.).
 *  - **Simplified layout** : we omit the per-pass DEBUG stage
 *    draws upstream emits (which produce most of the visible
 *    structure of `kawase_blur_rt.png`). Our output is just
 *    `[source | blur(radius=45) | blur(radius=55)]` in a single
 *    row at the top of the canvas. Iso-pixel parity vs the
 *    reference PNG is therefore impossible ; floor 0 % stays.
 *    Validating the SkSL machinery is the goal of this slice ;
 *    rendering the full DEBUG layout is a follow-up cosmetic
 *    polish.
 *
 * **Phase D2.4.d**.
 */
public class KawaseBlurRtGM : GM() {

    override fun getName(): String = "kawase_blur_rt"
    override fun getISize(): SkISize = SkISize.Make(1280, 768)

    private val mandrill: SkImage by lazy {
        val w = 256
        val h = 256
        SkBitmap(w, h).apply {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r = (x * 255 / (w - 1)) and 0xFF
                    val g = (y * 255 / (h - 1)) and 0xFF
                    val b = ((x + y) * 255 / (w + h - 2)) and 0xFF
                    setPixel(x, y, SkColorSetARGB(0xFF, r, g, b))
                }
            }
        }.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Cell 1 — source.
        c.drawImage(mandrill, 0f, 0f, SkSamplingOptions.Default, null)

        // Cell 2 — blur(radius = 45).
        val blurred45 = applyKawaseBlur(mandrill, blurRadius = 45)
        c.drawImage(blurred45, 256f, 0f, SkSamplingOptions.Default, null)

        // Cell 3 — blur(radius = 55), shifted to align with the
        // upstream layout's "blur(55) final mix" cell at x = 1024.
        val blurred55 = applyKawaseBlur(mandrill, blurRadius = 55)
        c.drawImage(blurred55, 1024f, 0f, SkSamplingOptions.Default, null)
    }

    /**
     * Run the Kawase iterative blur on [input] with the supplied
     * pixel radius. Returns a new [SkImage] sized identically to
     * the input (up-sampled from the working downscaled surface
     * via the mix shader).
     */
    private fun applyKawaseBlur(input: SkImage, blurRadius: Int): SkImage {
        // Copied verbatim from upstream (KawaseBlurFilter::draw) :
        // tmpRadius = blurRadius / 6 ; numberOfPasses = ceil(tmpRadius)
        // capped at 4 ; radiusByPasses = tmpRadius / numberOfPasses.
        val kInputScale = 0.25f
        val kInverseInputScale = 1f / kInputScale
        val kMaxPasses = 4
        val kMaxCrossFadeRadius = 30f

        val tmpRadius = blurRadius / 6f
        val numberOfPasses = min(kMaxPasses, ceil(tmpRadius).toInt())
        val radiusByPasses = tmpRadius / numberOfPasses
        val stepX = radiusByPasses
        val stepY = radiusByPasses

        val scaledW = (input.width * kInputScale).toInt()
        val scaledH = (input.height * kInputScale).toInt()
        val scaledInfo = SkImageInfo.Make(scaledW, scaledH, SkColorType.kRGBA_8888, SkAlphaType.kPremul)

        // First blur pass — downsample + sample at kInverseInputScale.
        val blurEffect = SkRuntimeEffect.MakeForShader(
            SkBuiltinSpecialisedEffects.KAWASE_BLUR_SHADER_SKSL,
        ).effect ?: error("Failed to compile kawase blur shader")

        var drawSurface: SkSurface = SkSurface.MakeRaster(scaledInfo)
        run {
            val builder = SkRuntimeEffectBuilder(blurEffect)
            builder.uniform("in_inverseScale").set(kInverseInputScale)
            builder.uniform("in_blurOffset").set(
                floatArrayOf(stepX * kInverseInputScale, stepY * kInverseInputScale),
            )
            builder.child("src").set(
                input.makeShader(SkTileMode.kClamp, SkTileMode.kClamp,
                    SkSamplingOptions(SkFilterMode.kLinear), SkMatrix.Identity),
            )
            val paint = SkPaint().apply { shader = builder.makeShader() }
            drawSurface.canvas.drawRect(SkRect.MakeWH(scaledW.toFloat(), scaledH.toFloat()), paint)
        }

        // Ping-pong subsequent passes.
        var lastDrawTarget: SkSurface = drawSurface
        if (numberOfPasses > 1) {
            var readSurface = drawSurface
            drawSurface = SkSurface.MakeRaster(scaledInfo)

            for (i in 1 until numberOfPasses) {
                val stepScale = i.toFloat() * kInputScale

                val builder = SkRuntimeEffectBuilder(blurEffect)
                builder.uniform("in_inverseScale").set(1f)
                builder.uniform("in_blurOffset").set(
                    floatArrayOf(stepX * stepScale, stepY * stepScale),
                )
                builder.child("src").set(
                    readSurface.makeImageSnapshot().makeShader(
                        SkTileMode.kClamp, SkTileMode.kClamp,
                        SkSamplingOptions(SkFilterMode.kLinear), SkMatrix.Identity,
                    ),
                )
                val paint = SkPaint().apply { shader = builder.makeShader() }
                drawSurface.canvas.drawRect(
                    SkRect.MakeWH(scaledW.toFloat(), scaledH.toFloat()), paint,
                )

                // Swap.
                val tmp = drawSurface
                drawSurface = readSurface
                readSurface = tmp
            }
            lastDrawTarget = readSurface
        }

        // Mix shader — upsample blurred + cross-fade with original.
        val mixEffect = SkRuntimeEffect.MakeForShader(
            SkBuiltinSpecialisedEffects.KAWASE_MIX_SHADER_SKSL,
        ).effect ?: error("Failed to compile kawase mix shader")

        val finalInfo = SkImageInfo.Make(input.width, input.height, SkColorType.kRGBA_8888, SkAlphaType.kPremul)
        val finalSurface = SkSurface.MakeRaster(finalInfo)
        val mixBuilder = SkRuntimeEffectBuilder(mixEffect)
        mixBuilder.uniform("in_inverseScale").set(kInputScale)
        mixBuilder.uniform("in_mix").set(min(1f, blurRadius.toFloat() / kMaxCrossFadeRadius))
        mixBuilder.child("in_blur").set(
            lastDrawTarget.makeImageSnapshot().makeShader(
                SkTileMode.kClamp, SkTileMode.kClamp,
                SkSamplingOptions(SkFilterMode.kLinear), SkMatrix.Identity,
            ),
        )
        mixBuilder.child("in_original").set(
            input.makeShader(SkTileMode.kClamp, SkTileMode.kClamp,
                SkSamplingOptions(SkFilterMode.kLinear), SkMatrix.Identity),
        )
        val mixPaint = SkPaint().apply { shader = mixBuilder.makeShader() }
        finalSurface.canvas.drawRect(
            SkRect.MakeWH(input.width.toFloat(), input.height.toFloat()), mixPaint,
        )

        return finalSurface.makeImageSnapshot()
    }
}
