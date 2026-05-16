package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

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
 */
public class PatchAlphaTestGM : GM() {

    override fun getName(): String = "patch_alpha_test"
    override fun getISize(): SkISize = SkISize.Make(550, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(-75f, -75f)

        val translucentRed = 0x80FF0000.toInt()
        val colors = intArrayOf(translucentRed, translucentRed, translucentRed, translucentRed)
        val paint = SkPaint()
        c.drawPatch(GCubics, colors, null, SkBlendMode.kDst, paint)

        c.translate(300f, 0f)

        val path = SkPathBuilder()
            .moveTo(GCubics[0].fX, GCubics[0].fY)
            .cubicTo(GCubics[1].fX, GCubics[1].fY, GCubics[2].fX, GCubics[2].fY, GCubics[3].fX, GCubics[3].fY)
            .cubicTo(GCubics[4].fX, GCubics[4].fY, GCubics[5].fX, GCubics[5].fY, GCubics[6].fX, GCubics[6].fY)
            .cubicTo(GCubics[7].fX, GCubics[7].fY, GCubics[8].fX, GCubics[8].fY, GCubics[9].fX, GCubics[9].fY)
            .cubicTo(GCubics[10].fX, GCubics[10].fY, GCubics[11].fX, GCubics[11].fY, GCubics[0].fX, GCubics[0].fY)
            .detach()
        paint.color = colors[0]
        c.drawPath(path, paint)
    }

    private companion object {
        // Same 12-control-point cubic chain as upstream's `gCubics`
        // (gm/patch.cpp:81). Order : top-left → top-right (top edge),
        // top-right → bottom-right (right edge), bottom-right →
        // bottom-left (bottom edge), bottom-left → top-left (left
        // edge). Corners are shared between adjacent edges.
        private val GCubics: Array<SkPoint> = arrayOf(
            // top points
            SkPoint(100f, 100f), SkPoint(150f, 50f), SkPoint(250f, 150f), SkPoint(300f, 100f),
            // right points
            SkPoint(250f, 150f), SkPoint(350f, 250f),
            // bottom points
            SkPoint(300f, 300f), SkPoint(250f, 250f), SkPoint(150f, 350f), SkPoint(100f, 300f),
            // left points
            SkPoint(50f, 250f), SkPoint(150f, 150f),
        )
    }
}
