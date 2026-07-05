package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/plus.cpp::PlusMergesAA` (256 × 256).
 *
 * Demonstrates that AA seams between two adjacent triangles **merge
 * losslessly** when drawn under `kPlus` inside a `saveLayer`, vs the
 * naive `kSrcOver` case which leaks the underlying red square through
 * the AA-subpixel coverage gap.
 *
 *  - Top-left red 100×100 square + green over-draw via two triangles
 *    under `kSrcOver` → faint red diagonal seam visible.
 *  - Top-right red 100×100 square + green under `kPlus` inside a
 *    `saveLayer` → seam fully covered, output is uniform green.
 *
 * The `saveLayer + kPlus` path validates our composite-from-layer
 * implementation under a non-`kSrcOver` paint blend mode.
 */
class PlusMergesAaGm : SkiaGm {
    override val name = "PlusMergesAA"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p = Paint(color = Color.RED, antiAlias = true)

        canvas.drawRect(Rect(0f, 0f, 100f, 100f), p)
        canvas.drawRect(Rect.fromXYWH(150f, 0f, 100f, 100f), p)

        val green = Color.fromRGBA(0f, 1f, 0f, 0xF0 / 255f)

        val upperLeft = Path {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(0f, 100f)
            lineTo(0f, 0f)
        }

        val bottomRight = Path {
            moveTo(100f, 0f)
            lineTo(100f, 100f)
            lineTo(0f, 100f)
            lineTo(100f, 0f)
        }

        canvas.drawPath(upperLeft, p.copy(color = green))
        canvas.drawPath(bottomRight, p.copy(color = green))

        canvas.saveLayer(null, null)
        val plusPaint = p.copy(color = green, blendMode = BlendMode.PLUS)
        canvas.translate(150f, 0f)
        canvas.drawPath(upperLeft, plusPaint)
        canvas.drawPath(bottomRight, plusPaint)
        canvas.restore()
    }
}
