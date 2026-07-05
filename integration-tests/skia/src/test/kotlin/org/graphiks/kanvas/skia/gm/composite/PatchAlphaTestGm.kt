package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/patch.cpp::patch_alpha_test`
 * (`DEF_SIMPLE_GM(patch_alpha_test, canvas, 550, 250)`).
 *
 * Side-by-side comparison of two ways to render the same shape :
 *  - left : `drawPatch` with all 4 corner colours = `0x80FF0000`
 *    (translucent red), `kDst` blend so the corners modulate
 *    nothing else ;
 *  - right : the *outline* of the same Coons patch — 4 cubic edges
 *    closed into a single SkPath — drawn with the same colour.
 *
 * The two should land at the same screen position with the same
 * translucent-red coverage. Reference upstream uses canvas pre-shift
 * `(-75, -75)` so the natural patch coords (origin `(50, 50)` ish)
 * land near `(0, 0)` in the 550 × 250 frame.
 * @see https://github.com/google/skia/blob/main/gm/patch.cpp
 */
class PatchAlphaTestGm : SkiaGm {
    override val name = "patch_alpha_test"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 550
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(-75f, -75f)

        val translucentRed = Color.fromRGBA(1f, 0f, 0f, 0.5f)
        val colors = listOf(translucentRed, translucentRed, translucentRed, translucentRed)
        val paint = Paint()
        canvas.drawPatch(GCubics, colors, null, BlendMode.DST, paint)

        canvas.translate(300f, 0f)

        val path = Path {
            moveTo(GCubics[0].x, GCubics[0].y)
            cubicTo(GCubics[1].x, GCubics[1].y, GCubics[2].x, GCubics[2].y, GCubics[3].x, GCubics[3].y)
            cubicTo(GCubics[4].x, GCubics[4].y, GCubics[5].x, GCubics[5].y, GCubics[6].x, GCubics[6].y)
            cubicTo(GCubics[7].x, GCubics[7].y, GCubics[8].x, GCubics[8].y, GCubics[9].x, GCubics[9].y)
            cubicTo(GCubics[10].x, GCubics[10].y, GCubics[11].x, GCubics[11].y, GCubics[0].x, GCubics[0].y)
        }
        canvas.drawPath(path, Paint(color = translucentRed))
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
