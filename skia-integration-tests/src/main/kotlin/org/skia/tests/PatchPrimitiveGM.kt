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
 * Port of Skia's `gm/patch.cpp::patch_primitive`
 * (`DEF_SIMPLE_GM(patch_primitive, canvas, 1500, 1100)`).
 *
 * Sweep test for `drawPatch` over a 4-column × 3-row matrix :
 *  - **3 rows** : blend mode kSrc / kDst / kColorDodge (vertex-blend
 *    arg passed through to drawVertices) ;
 *  - **4 columns** : (no colours, no texCoords) / (colours, no
 *    texCoords) / (no colours, texCoords + shader) / (colours +
 *    texCoords + shader).
 *
 * The 12-control-point `gCubics` is the same one upstream uses (see
 * gm/patch.cpp:81). For the texCoords variants the four corner UVs
 * span a 100 × 100 patch tied to a 7-stop linear gradient (same
 * stops as upstream's `make_shader`).
 *
 * **Out of scope** : the `draw_control_points` helper upstream calls
 * after each patch — it relies on `SkPatchUtils::Get*Cubic` to slice
 * the 12-point array, which we don't carry. Skipping it makes the
 * cells drop the cubic-control overlay (red / blue / cyan / yellow /
 * green corner dots and dashed control lines) ; the patch interiors
 * are unaffected.
 */
public class PatchPrimitiveGM : GM() {

    override fun getName(): String = "patch_primitive"
    override fun getISize(): SkISize = SkISize.Make(1500, 1100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN)
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
        // 12 control points — identical to upstream's `gCubics`
        // (gm/patch.cpp:81). Order : top edge (left → right), right
        // edge (top → bottom), bottom edge (right → left), left edge
        // (bottom → top). Corners are shared between adjacent edges.
        private val GCubics: Array<SkPoint> = arrayOf(
            SkPoint(100f, 100f), SkPoint(150f, 50f), SkPoint(250f, 150f), SkPoint(300f, 100f),
            SkPoint(250f, 150f), SkPoint(350f, 250f),
            SkPoint(300f, 300f), SkPoint(250f, 250f), SkPoint(150f, 350f), SkPoint(100f, 300f),
            SkPoint(50f, 250f), SkPoint(150f, 150f),
        )
    }
}
