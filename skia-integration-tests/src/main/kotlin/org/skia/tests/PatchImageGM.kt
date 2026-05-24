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
 * Port of Skia's `gm/patch.cpp::patch_image`
 * (`DEF_SIMPLE_GM(patch_image, canvas, 1500, 1100)`).
 *
 * Same 4-column × 3-row `drawPatch` sweep as [PatchPrimitiveGM], but the
 * shader in columns 2 and 3 is an image shader built from
 * `images/mandrill_128.png` (no local matrix). The tex-coord corners are
 * set to the image's natural `w × h` footprint so the mandrill fills the
 * patch exactly.
 *
 * ```cpp
 * DEF_SIMPLE_GM(patch_image, canvas, 1500, 1100) {
 *     const SkColor colors[SkPatchUtils::kNumCorners] = {
 *         SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN
 *     };
 *     dopatch(canvas, colors, ToolUtils::GetResourceAsImage("images/mandrill_128.png"), nullptr);
 * }
 * ```
 *
 * When the image resource is unavailable the GM falls back to the
 * gradient shader (same as [PatchPrimitiveGM]), which still exercises
 * the patch tessellator, so the test is not a hard failure on that path.
 *
 * **Out of scope** : `draw_control_points` (see [PatchPrimitiveGM]).
 */
public class PatchImageGM : GM() {

    override fun getName(): String = "patch_image"
    override fun getISize(): SkISize = SkISize.Make(1500, 1100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN)
        val img: SkImage? = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
        val modes = arrayOf(SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kColorDodge)
        val paint = SkPaint().apply { color = SK_ColorGREEN }

        // When the image is available use its dimensions for tex-coords;
        // otherwise fall back to the canonical 100×100 UV square.
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
                localMatrix = SkMatrix.Identity,
            ) to tex
        } else {
            // Fallback — no image available; use gradient + unit UV square.
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
