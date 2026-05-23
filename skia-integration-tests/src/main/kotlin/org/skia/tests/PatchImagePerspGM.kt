package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/patch.cpp::patch_image_persp`
 * (`DEF_SIMPLE_GM(patch_image_persp, canvas, 1500, 1100)`).
 *
 * Same as [PatchImageGM] except the image shader is built with a
 * perspective local matrix — `localM[kMPersp0] = 0.00001f` (index 6 in
 * upstream's 9-element row-major array). This exercises the perspective
 * sampling path inside the `SkBitmapShader` / raster device.
 *
 * ```cpp
 * DEF_SIMPLE_GM(patch_image_persp, canvas, 1500, 1100) {
 *     const SkColor colors[SkPatchUtils::kNumCorners] = {
 *         SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN
 *     };
 *     SkMatrix localM;
 *     localM.reset();
 *     localM[6] = 0.00001f;    // force perspective
 *     dopatch(canvas, colors,
 *             ToolUtils::GetResourceAsImage("images/mandrill_128.png"),
 *             &localM);
 * }
 * ```
 *
 * In kanvas-skia's [SkMatrix], `localM[6]` corresponds to the `persp0`
 * field (`kMPersp0`). We construct the matrix via `copy(persp0 = 0.00001f)`
 * on the identity.
 *
 * [org.skia.foundation.SkBitmapShader] handles perspective local matrices
 * via per-pixel homogeneous mapping when `hasPerspective()` is `true`, so the
 * texture warp is fully functional.
 *
 * **Out of scope** : `draw_control_points` (see [PatchPrimitiveGM]).
 */
public class PatchImagePerspGM : GM() {

    override fun getName(): String = "patch_image_persp"
    override fun getISize(): SkISize = SkISize.Make(1500, 1100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN)
        val img: SkImage? = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
        val modes = arrayOf(SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kColorDodge)
        val paint = SkPaint().apply { color = SK_ColorGREEN }

        // Perspective local matrix: identity + persp0 = 0.00001f.
        // Mirrors upstream's `localM[6] = 0.00001f` (kMPersp0).
        val perspMatrix = SkMatrix.Identity.copy(persp0 = 0.00001f)

        val (shader, texCoords) = if (img != null) {
            val w = img.width.toFloat()
            val h = img.height.toFloat()
            val tex = arrayOf(
                SkPoint(0f, 0f), SkPoint(w, 0f), SkPoint(w, h), SkPoint(0f, h),
            )
            img.makeShader(
                tileX = SkTileMode.kClamp,
                tileY = SkTileMode.kClamp,
                sampling = SkSamplingOptions.Default,
                localMatrix = perspMatrix,
            ) to tex
        } else {
            // Fallback — no image available; use unit UV square, no shader.
            null to arrayOf(
                SkPoint(0f, 0f), SkPoint(100f, 0f), SkPoint(100f, 100f), SkPoint(0f, 100f),
            )
        }

        c.save()
        for (y in 0 until 3) {
            for (x in 0 until 4) {
                c.save()
                c.translate(x * 350f, y * 350f)
                when (x) {
                    0 -> c.drawPatch(GCubics, null, null, modes[y], paint)
                    1 -> c.drawPatch(GCubics, colors, null, modes[y], paint)
                    2 -> {
                        paint.shader = shader
                        c.drawPatch(GCubics, null, texCoords, modes[y], paint)
                        paint.shader = null
                    }
                    3 -> {
                        paint.shader = shader
                        c.drawPatch(GCubics, colors, texCoords, modes[y], paint)
                        paint.shader = null
                    }
                }
                c.restore()
            }
        }
        c.restore()
    }

    private companion object {
        // Same 12-control-point cubic chain as upstream's `gCubics` (gm/patch.cpp:81).
        private val GCubics: Array<SkPoint> = arrayOf(
            SkPoint(100f, 100f), SkPoint(150f, 50f), SkPoint(250f, 150f), SkPoint(300f, 100f),
            SkPoint(250f, 150f), SkPoint(350f, 250f),
            SkPoint(300f, 300f), SkPoint(250f, 250f), SkPoint(150f, 350f), SkPoint(100f, 300f),
            SkPoint(50f, 250f), SkPoint(150f, 150f),
        )
    }
}
