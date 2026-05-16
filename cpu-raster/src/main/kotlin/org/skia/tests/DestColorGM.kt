package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/destcolor.cpp::DEF_SIMPLE_GM(destcolor)`.
 *
 * Draws an image, then applies an "invert RGB" blender to a
 * lower-right oval. The blender is a runtime effect : reads
 * `dst` (the source image pixel), returns `1 - dst.rgb`, alpha = 1.
 *
 * **Adaptation** : upstream loads `images/mandrill_512.png` ;
 * we substitute a 512×512 synthetic gradient (matches the pattern
 * used by `RuntimeColorFilterGM` etc.). Iso-pixel parity vs
 * upstream's mandrill is therefore impossible — similarity
 * reflects the blender's RGB-invert math, not the underlying pixels.
 *
 * **Phase D2.4.d** — depends on D2.0 (SkBlender plumbing) for
 * `paint.blender` ; the SkSL is registered by
 * [SkBuiltinSpecialisedEffects].
 */
public class DestColorGM : GM() {

    override fun getName(): String = "destcolor"
    override fun getISize(): SkISize = SkISize.Make(640, 640)

    private val image: SkImage by lazy {
        val w = 512
        val h = 512
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

        // Draw the source image.
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, null)

        // Invert blender on the lower-right oval.
        val effect = SkRuntimeEffect.MakeForBlender(SkBuiltinSpecialisedEffects.INVERT_BLENDER_SKSL).effect
            ?: error("Failed to compile invert blender")
        val invertPaint = SkPaint().apply {
            isAntiAlias = true
            blender = effect.makeBlender(uniforms = null)
        }
        c.drawOval(SkRect.MakeLTRB(128f, 128f, 640f, 640f), invertPaint)
    }
}
