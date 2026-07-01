package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.random.Random

/**
 * Port of FillCircleGM.
 *
 * Draws concentric filled circles with deterministic random colors.
 * The original used `SkRandom` and `ToolUtils.colorTo565` for
 * pixel-exact reference matching; we use `kotlin.random.Random`
 * directly so new references must be generated.
 * The original `rotate` call (no-op at `fRotate = 0`) is omitted.
 */
/**
 * Port of Skia's `gm/fillcircle.cpp`.
 * Spiralling stack of concentric AA ovals with 565-quantised random colours.
 * @see https://github.com/google/skia/blob/main/gm/fillcircle.cpp
 */
class FillCircleGm : SkiaGm {
    override val name = "fillcircle"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 520
    override val height = 520

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(20f, 20f)
        canvas.translate(13f, 13f)

        val strokeWidth = 0.5f
        val delta = strokeWidth * 3f / 2f
        var r = Rect.fromXYWH(-12f, -12f, 24f, 24f)
        val rand = Random(0)

        while (r.width > strokeWidth * 2f) {
            val raw = rand.nextInt()
            val colorInt = raw or (0xFF000000.toInt())
            val a = ((colorInt ushr 24) and 0xFF) / 255f
            val rr = ((colorInt ushr 16) and 0xFF) / 255f
            val g = ((colorInt ushr 8) and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            val paint = Paint(color = Color.fromRGBA(rr, g, b, a), antiAlias = true)

            canvas.save()
            canvas.drawOval(r, paint)
            canvas.restore()

            r = Rect.fromLTRB(r.left + delta, r.top + delta, r.right - delta, r.bottom - delta)
        }
    }
}
