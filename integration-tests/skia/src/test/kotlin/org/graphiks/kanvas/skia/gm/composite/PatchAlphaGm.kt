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
 * Port of Skia's `gm/patch.cpp::patch_alpha`
 * (`DEF_SIMPLE_GM(patch_alpha, canvas, 1500, 1100)`).
 *
 * Identical to `patch_primitive` (see [PatchPrimitiveGm]) except the
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
 * **Out of scope** : `draw_control_points` (see [PatchPrimitiveGm]).
 * @see https://github.com/google/skia/blob/main/gm/patch.cpp
 */
class PatchAlphaGm : SkiaGm {
    override val name = "patch_alpha"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1500
    override val height = 1100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val colors = listOf(
            Color.RED,
            Color.fromRGBA(0f, 1f, 0f, 0f),
            Color.BLUE,
            Color.fromRGBA(1f, 0f, 1f, 0f),
        )
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
