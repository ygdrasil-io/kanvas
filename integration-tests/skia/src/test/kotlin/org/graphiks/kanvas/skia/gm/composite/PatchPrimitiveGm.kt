package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

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
 * @see https://github.com/google/skia/blob/main/gm/patch.cpp
 */
class PatchPrimitiveGm : SkiaGm {
    override val name = "patch_primitive"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1500
    override val height = 1100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(0f, 1f, 1f, 1f))
        val texCoords = listOf(
            Point(0f, 0f), Point(100f, 0f), Point(100f, 100f), Point(0f, 100f),
        )
        val shader = Shader.LinearGradient(
            start = Point(100f / 4f, 0f),
            end = Point(3f * 100f / 4f, 100f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f / 6f, Color.fromRGBA(0f, 1f, 1f, 1f)),
                GradientStop(2f / 6f, Color.GREEN),
                GradientStop(3f / 6f, Color.WHITE),
                GradientStop(4f / 6f, Color.fromRGBA(1f, 0f, 1f, 1f)),
                GradientStop(5f / 6f, Color.BLUE),
                GradientStop(1f, Color.fromRGBA(1f, 1f, 0f, 1f)),
            ),
            tileMode = TileMode.MIRROR,
        )
        val modes = listOf(BlendMode.SRC, BlendMode.DST, BlendMode.COLOR_DODGE)
        val paint = Paint(color = Color.GREEN)

        canvas.save()
        for (iy in 0 until 3) {
            for (ix in 0 until 4) {
                canvas.save()
                canvas.translate(ix * 350f, iy * 350f)
                when (ix) {
                    0 -> canvas.drawPatch(GCubics, null, null, modes[iy], paint)
                    1 -> canvas.drawPatch(GCubics, colors, null, modes[iy], paint)
                    2 -> canvas.drawPatch(GCubics, null, texCoords, modes[iy], paint.copy(shader = shader))
                    3 -> canvas.drawPatch(GCubics, colors, texCoords, modes[iy], paint.copy(shader = shader))
                }
                canvas.restore()
            }
        }
        canvas.restore()
    }

    private companion object {
        private val GCubics: List<Point> = listOf(
            Point(100f, 100f), Point(150f, 50f), Point(250f, 150f), Point(300f, 100f),
            Point(250f, 150f), Point(350f, 250f),
            Point(300f, 300f), Point(250f, 250f), Point(150f, 350f), Point(100f, 300f),
            Point(50f, 250f), Point(150f, 150f),
        )
    }
}
