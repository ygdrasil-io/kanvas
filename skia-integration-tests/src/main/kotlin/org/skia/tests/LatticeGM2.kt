package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkLattice
import org.skia.core.SkSurface
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/lattice.cpp::LatticeGM2` (800 × 800).
 *
 * Exercises `drawImageLattice` code paths that draw fixed-colour and 1×1
 * rectangles, combined with blend-mode interaction (kSrcOver / kSrcATop).
 *
 * The source image is 80×80 and has three logical rows:
 *  - Row 0 (y = 0..0): green 4px | blue 1px | red fill
 *  - Row 1 (y = 1..1): red 4px  | semi-blue 1px | green fill — drawn as
 *    kFixedColor rects
 *  - Row 2 (y = 2..79): green 4px | semi-red 1px | blue fill — drawn as
 *    kTransparent rects
 *
 * Two draw calls per helper:
 *  1. Paint color 0xFFFFFFFF (opaque white) — plain colours show through.
 *  2. Paint color 0x80000FFF (semi-transparent) — alpha interaction.
 *
 * Two helper invocations stacked vertically:
 *  - kSrcOver blending
 *  - kSrcATop blending (with a semi-transparent grey background rect)
 */
public class LatticeGM2 : GM() {

    override fun getName(): String = "lattice2"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    /** Mirrors upstream `makeImage` — 80×80 coloured pixel pattern. */
    private fun makeImage(canvas: SkCanvas, padLeft: Int, padTop: Int,
                          padRight: Int, padBottom: Int): SkImage {
        val kSize = 80
        val info = SkImageInfo.MakeN32Premul(
            kSize + padLeft + padRight,
            kSize + padTop + padBottom,
        )
        val surface = SkSurface.MakeRaster(info)
        val c = surface.canvas
        val paint = SkPaint().apply { isAntiAlias = false }

        // First line
        var r = SkRect.MakeXYWH(0f, 0f, 4f, 1f)           // 4×1 green
        paint.color = 0xFF00FF00.toInt(); c.drawRect(r, paint)
        r = SkRect.MakeXYWH(4f, 0f, 1f, 1f)               // 1×1 blue pixel → rect
        paint.color = 0xFF0000FF.toInt(); c.drawRect(r, paint)
        r = SkRect.MakeXYWH(5f, 0f, (kSize - 5).toFloat(), 1f) // rest: red
        paint.color = 0xFFFF0000.toInt(); c.drawRect(r, paint)

        // Second line → drawn as kFixedColor rectangles
        r = SkRect.MakeXYWH(0f, 1f, 4f, 1f)               // 4×1 red
        paint.color = 0xFFFF0000.toInt(); c.drawRect(r, paint)
        r = SkRect.MakeXYWH(4f, 1f, 1f, 1f)               // 1×1 blue with alpha
        paint.color = 0x880000FF.toInt(); c.drawRect(r, paint)
        r = SkRect.MakeXYWH(5f, 1f, (kSize - 5).toFloat(), 1f) // rest: green
        paint.color = 0xFF00FF00.toInt(); c.drawRect(r, paint)

        // Third line — drawn as kTransparent (does not draw)
        r = SkRect.MakeXYWH(0f, 2f, 4f, (kSize - 2).toFloat())       // 4×78 green
        paint.color = 0xFF00FF00.toInt(); c.drawRect(r, paint)
        r = SkRect.MakeXYWH(4f, 2f, 1f, (kSize - 2).toFloat())       // 1×78 semi-red
        paint.color = 0x88FF0000.toInt(); c.drawRect(r, paint)
        r = SkRect.MakeXYWH(5f, 2f, (kSize - 5).toFloat(), (kSize - 2).toFloat()) // blue fill
        paint.color = 0xFF0000FF.toInt(); c.drawRect(r, paint)

        return surface.makeImageSnapshot()
    }

    private fun onDrawHelper(canvas: SkCanvas, padLeft: Int, padTop: Int,
                             padRight: Int, padBottom: Int, paint: SkPaint) {
        val xDivs = intArrayOf(4, 5)
        val yDivs = intArrayOf(1, 2)

        canvas.save()

        val image = makeImage(canvas, padLeft, padTop, padRight, padBottom)

        canvas.drawImage(image, 10f, 10f)

        val lattice = SkLattice(
            xDivs = xDivs,
            yDivs = yDivs,
            bounds = null,
            rectTypes = Array(9) { SkLattice.RectType.kDefault }.also { flags ->
                flags[3] = SkLattice.RectType.kFixedColor
                flags[4] = SkLattice.RectType.kFixedColor
                flags[5] = SkLattice.RectType.kFixedColor
                flags[6] = SkLattice.RectType.kTransparent
                flags[7] = SkLattice.RectType.kTransparent
                flags[8] = SkLattice.RectType.kTransparent
            },
            colors = IntArray(9) { SK_ColorBLACK }.also { colors ->
                colors[3] = 0xFFFF0000.toInt()
                colors[4] = 0x880000FF.toInt()
                colors[5] = 0xFF00FF00.toInt()
            },
        )

        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawImageLattice(
            image, lattice,
            SkRect.MakeXYWH(100f, 100f, 200f, 200f),
            SkFilterMode.kNearest, paint,
        )

        // Same content with alpha
        canvas.translate(400f, 0f)
        paint.color = 0x80000FFF.toInt()
        canvas.drawImageLattice(
            image, lattice,
            SkRect.MakeXYWH(100f, 100f, 200f, 200f),
            SkFilterMode.kNearest, paint,
        )

        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Draw a rectangle in the background with transparent pixels
        val paint = SkPaint()
        paint.color = 0x7F123456
        paint.blendMode = SkBlendMode.kSrc
        c.drawRect(SkRect.MakeXYWH(300f, 0f, 300f, 800f), paint)

        // Draw image lattice with kSrcOver blending
        paint.blendMode = SkBlendMode.kSrcOver
        onDrawHelper(c, 0, 0, 0, 0, paint)

        // Draw image lattice with kSrcATop blending
        c.translate(0f, 400f)
        paint.blendMode = SkBlendMode.kSrcATop
        onDrawHelper(c, 0, 0, 0, 0, paint)
    }
}
