package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.core.SkSurface
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's
 * [`gm/imagedither.cpp::DEF_SIMPLE_GM_CAN_FAIL(image_dither)`](https://github.com/google/skia/blob/main/gm/imagedither.cpp).
 *
 * Builds a shallow grey gradient (`0xFF555555` → `0xFF444444`) into
 * an F16 sub-surface, then renders 4 cells :
 *  1. Source image as-is (no dither).
 *  2. Image-shader path with dither flag enabled.
 *  3. drawImage path with dither flag enabled.
 *  4. The actual gradient with dither, for reference.
 *
 * Finally, a `stretch_colors_blender` blender is applied via
 * `drawPaint` to amplify whatever sub-LSB variation each cell
 * contains — this is what makes dithering visible.
 *
 * **Limitations** : our raster pipeline does not currently honour
 * `SkPaint.isDither`, so cells 2 / 3 / 4 produce identical output
 * to the non-dithered cell 1. The stretch blender then reveals
 * uniform colour bands rather than the ordered-dither speckle
 * upstream produces. Floor pinned at 0 % accordingly ; ratchet
 * still catches regressions. Implementing dither (Bayer matrix
 * or blue noise) is a separate slice.
 */
public class ImageDitherGM : GM() {

    override fun getName(): String = "image_dither"
    override fun getISize(): SkISize = SkISize.Make(425, 110)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Build the shallow grey gradient into an F16 sub-surface
        // (matches upstream's `kRGBA_F16_SkColorType`).
        val gradient: SkShader = SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(100f, 100f),
            colors = intArrayOf(0xFF555555.toInt(), 0xFF444444.toInt()),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val gradientPaint = SkPaint().apply { shader = gradient }

        val sourceSurface = SkSurface.MakeRaster(
            SkImageInfo.Make(100, 100, SkColorType.kRGBA_F16Norm, SkAlphaType.kPremul),
        )
        sourceSurface.canvas.drawPaint(gradientPaint)
        val image = sourceSurface.makeImageSnapshot()

        c.translate(5f, 5f)

        // Cell 1 : raw drawImage, no dither.
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, null)
        c.translate(105f, 0f)

        // Cell 2 : image-shader with dither flag (no-op for us).
        val imageShaderPaint = SkPaint().apply {
            shader = image.makeShader(SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default, SkMatrix.Identity)
            isDither = true
        }
        c.drawRect(SkRect.MakeWH(100f, 100f), imageShaderPaint)
        c.translate(105f, 0f)

        // Cell 3 : drawImage with dither flag (no-op for us).
        val drawImagePaint = SkPaint().apply { isDither = true }
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, drawImagePaint)
        c.translate(105f, 0f)

        // Cell 4 : the actual gradient with dither (no-op for us).
        gradientPaint.isDither = true
        c.drawRect(SkRect.MakeWH(100f, 100f), gradientPaint)

        // Apply the stretch_colors_blender via drawPaint.
        val effect = SkRuntimeEffect.MakeForBlender(SkBuiltinSpecialisedEffects.STRETCH_COLORS_BLENDER_SKSL).effect
            ?: error("Failed to compile stretch_colors_blender")
        val colorStretchPaint = SkPaint().apply {
            blender = effect.makeBlender(uniforms = null)
        }
        c.drawPaint(colorStretchPaint)
    }
}
