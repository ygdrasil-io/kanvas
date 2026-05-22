package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkLattice
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/lattice.cpp::LatticeGM` (800 × 800).
 *
 * Builds a 220×220 (kCap = 28, kMid = 8, kSize = 2·kCap + 3·kMid = 80)
 * image patterned with a yellow rounded rect + three vertical + three
 * horizontal translucent colour stripes, then draws it with two lattice
 * configurations stacked vertically :
 *  1. 4 × 4 divs, no `rectTypes` → all cells fill normally.
 *  2. 5 × 5 divs (includes the degenerate first div), with custom
 *     `rectTypes` skipping cells 4 / 9 / 12 / 19 (kTransparent) and
 *     pinning three cells to a fixed black colour (kFixedColor).
 *
 * Two pad configurations are stacked vertically : (0,0,0,0) and
 * (3,7,4,11) — i.e. the second invocation pads the source by
 * `(left=3, top=7, right=4, bottom=11)` and feeds the lattice through
 * the same logic.
 *
 * **kanvas-skia adaptation** : upstream pulls `fixedColor` values out
 * of `image->readPixels(...)` at known pixel coordinates. We replicate
 * by hard-coding `SK_ColorBLACK` to match the upstream comment "These
 * colors match what was already in the bitmap" — the rounded-rect
 * corners that the divs land on happen to be black-on-the-canvas-bg.
 */
public class LatticeGM : GM() {

    override fun getName(): String = "lattice"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawHelper(c, 0, 0, 0, 0)
        c.translate(0f, 400f)
        drawHelper(c, 3, 7, 4, 11)
    }

    private fun drawHelper(
        canvas: SkCanvas, padLeft: Int, padTop: Int, padRight: Int, padBottom: Int,
    ) {
        canvas.save()

        val xDivs = IntArray(5)
        val yDivs = IntArray(5)
        xDivs[0] = padLeft
        yDivs[0] = padTop

        val image = makeImage(xDivs, yDivs, padLeft, padTop, padRight, padBottom)

        val sizes = arrayOf(
            Pair(50f, 50f),    // shrink in both axes
            Pair(50f, 200f),   // shrink in X
            Pair(200f, 50f),   // shrink in Y
            Pair(200f, 200f),
        )

        canvas.drawImage(image, 10f, 10f)

        val x = 100f
        val y = 100f

        // Lattice #1 : 4 × 4 divs from xDivs[1..4] / yDivs[1..4].
        val xDivs4 = intArrayOf(xDivs[1], xDivs[2], xDivs[3], xDivs[4])
        val yDivs4 = intArrayOf(yDivs[1], yDivs[2], yDivs[3], yDivs[4])
        val fullBounds = SkIRect.MakeWH(image.width, image.height)
        val cropBounds = SkIRect.MakeLTRB(padLeft, padTop, image.width - padRight, image.height - padBottom)
        val lattice1Bounds = if (cropBounds == fullBounds) null else cropBounds
        val lattice1 = SkLattice(
            xDivs = xDivs4, yDivs = yDivs4,
            rectTypes = null, bounds = lattice1Bounds, colors = null,
        )
        for (iy in 0 until 2) {
            for (ix in 0 until 2) {
                val i = ix * 2 + iy
                val r = SkRect.MakeXYWH(
                    x + ix * 60, y + iy * 60,
                    sizes[i].first, sizes[i].second,
                )
                canvas.drawImageLattice(image, lattice1, r)
            }
        }

        // Lattice #2 : 5 × 5 divs (xDivs[0..4]) with kFixedColor /
        // kTransparent overrides. 36 cells total.
        val fixedColorX = intArrayOf(2, 4, 1)
        val fixedColorY = intArrayOf(1, 1, 2)
        val fixedColor = intArrayOf(SK_ColorBLACK, SK_ColorBLACK, SK_ColorBLACK)

        val flags = Array(36) { SkLattice.RectType.kDefault }
        flags[4] = SkLattice.RectType.kTransparent
        flags[9] = SkLattice.RectType.kTransparent
        flags[12] = SkLattice.RectType.kTransparent
        flags[19] = SkLattice.RectType.kTransparent
        for (rectNum in 0 until 3) {
            flags[fixedColorY[rectNum] * 6 + fixedColorX[rectNum]] =
                SkLattice.RectType.kFixedColor
        }

        val colors = IntArray(36)
        for (rectNum in 0 until 3) {
            colors[fixedColorY[rectNum] * 6 + fixedColorX[rectNum]] = fixedColor[rectNum]
        }

        val lattice2 = SkLattice(
            xDivs = xDivs, yDivs = yDivs,
            rectTypes = flags, bounds = lattice1Bounds, colors = colors,
        )

        canvas.translate(400f, 0f)
        for (iy in 0 until 2) {
            for (ix in 0 until 2) {
                val i = ix * 2 + iy
                val r = SkRect.MakeXYWH(
                    x + ix * 60, y + iy * 60,
                    sizes[i].first, sizes[i].second,
                )
                canvas.drawImageLattice(image, lattice2, r)
            }
        }

        canvas.restore()
    }

    /**
     * Mirrors upstream `make_image(...)` — builds a 220 × 220 image with
     * a yellow rounded rect, three vertical translucent green / blue /
     * magenta stripes, and three matching horizontal stripes. Writes the
     * div schedule into [xDivs] starting at index 1 (caller pre-seeds
     * index 0 with `padLeft/padTop`).
     */
    private fun makeImage(
        xDivs: IntArray, yDivs: IntArray,
        padLeft: Int, padTop: Int, padRight: Int, padBottom: Int,
    ): SkImage {
        val kCap = 28
        val kMid = 8
        val kSize = 2 * kCap + 3 * kMid
        val info = SkImageInfo.MakeN32Premul(kSize + padLeft + padRight, kSize + padTop + padBottom)
        val surf = SkSurface.MakeRaster(info)
        val canvas = surf.canvas
        canvas.translate(padLeft.toFloat(), padTop.toFloat())

        val r = SkRect.MakeWH(kSize.toFloat(), kSize.toFloat())
        val strokeWidth = 6f
        val radius = kCap - strokeWidth / 2f

        xDivs[1] = kCap + padLeft
        yDivs[1] = kCap + padTop
        xDivs[2] = kCap + kMid + padLeft
        yDivs[2] = kCap + kMid + padTop
        xDivs[3] = kCap + 2 * kMid + padLeft
        yDivs[3] = kCap + 2 * kMid + padTop
        xDivs[4] = kCap + 3 * kMid + padLeft
        yDivs[4] = kCap + 3 * kMid + padTop

        val paint = SkPaint().apply { isAntiAlias = true }

        paint.color = 0xFFFFFF00.toInt()
        canvas.drawRoundRect(r, radius, radius, paint)

        // Vertical stripes
        var stripe = SkRect.MakeXYWH(kCap.toFloat(), 0f, kMid.toFloat(), kSize.toFloat())
        paint.color = 0x8800FF00.toInt(); canvas.drawRect(stripe, paint)
        stripe = SkRect.MakeXYWH((kCap + kMid).toFloat(), 0f, kMid.toFloat(), kSize.toFloat())
        paint.color = 0x880000FF.toInt(); canvas.drawRect(stripe, paint)
        stripe = SkRect.MakeXYWH((kCap + 2 * kMid).toFloat(), 0f, kMid.toFloat(), kSize.toFloat())
        paint.color = 0x88FF00FF.toInt(); canvas.drawRect(stripe, paint)

        // Horizontal stripes
        stripe = SkRect.MakeXYWH(0f, kCap.toFloat(), kSize.toFloat(), kMid.toFloat())
        paint.color = 0x8800FF00.toInt(); canvas.drawRect(stripe, paint)
        stripe = SkRect.MakeXYWH(0f, (kCap + kMid).toFloat(), kSize.toFloat(), kMid.toFloat())
        paint.color = 0x880000FF.toInt(); canvas.drawRect(stripe, paint)
        stripe = SkRect.MakeXYWH(0f, (kCap + 2 * kMid).toFloat(), kSize.toFloat(), kMid.toFloat())
        paint.color = 0x88FF00FF.toInt(); canvas.drawRect(stripe, paint)

        return surf.makeImageSnapshot()
    }
}
