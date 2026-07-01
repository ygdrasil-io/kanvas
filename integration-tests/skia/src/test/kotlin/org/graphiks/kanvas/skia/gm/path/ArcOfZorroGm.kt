package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.random.Random

/**
 * Port of Skia's `gm/arcofzorro.cpp` (`ArcOfZorroGM`).
 * Two hundred consecutive open-arc strokes (useCenter=false,
 * strokeWidth=35, default kButt_Cap + kMiter_Join) of slowly
 * increasing sweep angle (134° → 136° in 0.01° steps), laid out by a
 * boustrophedon-style direction switch ("→ then ↙ then →"). Random
 * opaque colour per arc from a default-seeded Random (bit-compatible
 * with upstream).
 * Hits every Phase 3 piece end-to-end:
 * - GmCanvas.drawArc(useCenter=false) → builds an open path via
 *   arcTo + cubic-Bézier flattening.
 * - The path stroker converts the stroked open arc into a
 *   filled outline path with kButt_Cap ends and kMiter_Join
 *   bends — many overlapping strokes per cell exercise the AA edge
 *   arithmetic heavily.
 * @see https://github.com/google/skia/blob/main/gm/arcofzorro.cpp
 */
class ArcOfZorroGm : SkiaGm {
    override val name = "arcofzorro"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f)
        val rand = Random(0)
        val rect = Rect.fromXYWH(10f, 10f, 200f, 200f)
        var paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 35f
        )
        var xOffset = 0
        var yOffset = 0
        var direction = 0
        var arc = 134.0f
        while (arc < 136.0f) {
            val colorInt = rand.nextInt() or 0xFF000000.toInt()
            val r = (colorInt shr 16 and 0xFF) / 255f
            val g = (colorInt shr 8 and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            paint = paint.copy(color = Color.fromRGBA(r, g, b, 1f))

            canvas.save()
            canvas.translate(xOffset.toFloat(), yOffset.toFloat())
            canvas.drawArc(rect, 0f, arc, useCenter = false, paint = paint)
            canvas.restore()

            when (direction) {
                0 -> {
                    xOffset += 10
                    if (xOffset >= 700) direction = 1
                }
                1 -> {
                    xOffset -= 10
                    yOffset += 10
                    if (xOffset < 50) direction = 2
                }
                2 -> xOffset += 10
            }
            arc += 0.01f
        }
    }
}
