package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

/**
 * Port of Skia's `gm/patch.cpp::patch_alpha`
 * (`DEF_SIMPLE_GM(patch_alpha, canvas, 1500, 1100)`).
 *
 * Identical to `patch_primitive` (see [PatchPrimitiveGM]) except the
 * four corner colours include transparent entries:
 *
 * ```cpp
 * const SkColor colors[SkPatchUtils::kNumCorners] = {
 *     SK_ColorRED, 0x0000FF00, SK_ColorBLUE, 0x00FF00FF,
 * };
 * ```
 *
 * Two of the four corners are fully transparent (`alpha = 0`), so the
 * patch exercises alpha-blending within the Coons-patch tessellation
 * and verifies that the `kSrc` / `kDst` / `kColorDodge` blend modes
 * interact correctly with a varying alpha field.
 *
 * **Out of scope** : `draw_control_points` (see [PatchPrimitiveGM]).
 */
public class PatchAlphaGM : GM() {

    override fun getName(): String = "patch_alpha"
    override fun getISize(): SkISize = SkISize.Make(1500, 1100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Two corners are fully transparent (alpha = 0x00).
        val colors = intArrayOf(
            SK_ColorRED,
            0x0000FF00,          // transparent green
            SK_ColorBLUE,
            0x00FF00FF,          // transparent magenta
        )
        val texCoords = arrayOf(
            SkPoint(0f, 0f), SkPoint(100f, 0f), SkPoint(100f, 100f), SkPoint(0f, 100f),
        )
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(100f / 4f, 0f),
            p1 = SkPoint(3f * 100f / 4f, 100f),
            colors = intArrayOf(
                SK_ColorRED, SK_ColorCYAN, SK_ColorGREEN, SK_ColorWHITE, SK_ColorMAGENTA,
                SK_ColorBLUE, SK_ColorYELLOW,
            ),
            positions = null,
            tileMode = SkTileMode.kMirror,
        )
        val modes = arrayOf(SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kColorDodge)
        val paint = SkPaint().apply { color = SK_ColorGREEN }

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
