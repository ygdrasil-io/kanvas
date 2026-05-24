package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.SkColorFilterPriv
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkLumaColorFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize

/**
 * R-final.S — `SkColorFilterPriv.withWorkingFormat` consumer GM. Iso-aligned port of
 * upstream's `gm/lumafilter.cpp` `DEF_SIMPLE_GM(AlternateLuma, canvas, 384, 128)`.
 *
 * Upstream draws three panels (128×128 each) side-by-side from
 * `images/mandrill_128.png`:
 *
 * 1. Left — plain `SkLumaColorFilter` applied to the source image.
 * 2. Middle — the source image with no filter (reference).
 * 3. Right — a runtime SkSL filter (`inColor.ggga`) wrapped in
 *    `SkColorFilterPriv::WithWorkingFormat(&SkNamedTransferFn::kLinear,
 *    &SkNamedGamut::kXYZ, &kUnpremul_SkAlphaType)`.
 *    When RGB holds CIE XYZ co-ordinates, splatting the G (= Y)
 *    channel produces near-greyscale.
 *
 * `images/mandrill_128.png` is not available in the test classpath; a
 * synthetic 128×128 RGB gradient is used instead, so the reference similarity
 * is intentionally ratcheted from the current low baseline.
 */
public class AlternateLumaGM : GM() {

    override fun getName(): String = "AlternateLuma"
    override fun getISize(): SkISize = SkISize.Make(384, 128)

    /** Synthetic 128×128 stand-in for `images/mandrill_128.png`. */
    private val img by lazy {
        SkBitmap(128, 128).apply {
            for (y in 0 until 128) {
                for (x in 0 until 128) {
                    val r = (x * 255 / 127) and 0xFF
                    val g = (y * 255 / 127) and 0xFF
                    val b = ((x + y) * 255 / 254) and 0xFF
                    setPixel(x, y, SkColorSetARGB(0xFF, r, g, b))
                }
            }
        }.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Panel 1 — normal luma color filter.
        val lumaPaint = SkPaint().apply {
            colorFilter = SkLumaColorFilter.Make()
        }
        c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, lumaPaint)
        c.translate(128f, 0f)

        // Panel 2 — original image (no filter, reference).
        c.drawImage(img, 0f, 0f)
        c.translate(128f, 0f)

        // Panel 3 — G-channel splat in working format CIE XYZ / linear.
        // The inner SkSL filter replicates G into R, G, B (inColor.ggga).
        // Wrapped by WithWorkingFormat so it operates on linear XYZ pixels.
        val sksl = SkBuiltinColorFilterEffects.G_CHANNEL_SPLAT_SKSL
        val effect = SkRuntimeEffect.MakeForColorFilter(sksl).effect
            ?: error("Unable to compile G-channel-splat color filter")
        val innerFilter = effect.makeColorFilter(uniforms = null)
            ?: error("makeColorFilter returned null")
        val workingFilter = SkColorFilterPriv.withWorkingFormat(
            child = innerFilter,
            tf = SkNamedTransferFn.kLinear,
            gamut = SkNamedGamut.kXYZ,
            at = SkAlphaType.kUnpremul,
        )
        val workingPaint = SkPaint().apply { colorFilter = workingFilter }
        c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, workingPaint)
    }
}
