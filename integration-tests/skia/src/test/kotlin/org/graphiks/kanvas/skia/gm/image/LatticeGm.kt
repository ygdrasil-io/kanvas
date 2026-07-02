package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.drawRoundRect
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/lattice.cpp::LatticeGM` (800 x 800).
 * Tests drawImageLattice with 4x4 and 5x5 div configurations.
 * @see https://github.com/google/skia/blob/main/gm/lattice.cpp
 */
class LatticeGm : SkiaGm {
    override val name = "lattice"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawHelper(canvas, 0, 0, 0, 0)
        canvas.translate(0f, 400f)
        drawHelper(canvas, 3, 7, 4, 11)
    }

    private fun drawHelper(
        canvas: GmCanvas, padLeft: Int, padTop: Int, padRight: Int, padBottom: Int,
    ) {
        canvas.save()

        val xDivs = IntArray(5)
        val yDivs = IntArray(5)
        xDivs[0] = padLeft
        yDivs[0] = padTop

        val image = makeImage(xDivs, yDivs, padLeft, padTop, padRight, padBottom)

        val sizes = arrayOf(
            50f to 50f,
            50f to 200f,
            200f to 50f,
            200f to 200f,
        )

        canvas.drawImage(image, Rect.fromXYWH(10f, 10f, image.width.toFloat(), image.height.toFloat()))

        val x = 100f
        val y = 100f

        val xDivs4 = listOf(xDivs[1], xDivs[2], xDivs[3], xDivs[4])
        val yDivs4 = listOf(yDivs[1], yDivs[2], yDivs[3], yDivs[4])
        val lattice1 = Lattice(xDivs = xDivs4, yDivs = yDivs4)
        for (iy in 0 until 2) {
            for (ix in 0 until 2) {
                val i = ix * 2 + iy
                val r = Rect.fromXYWH(
                    x + ix * 60, y + iy * 60,
                    sizes[i].first, sizes[i].second,
                )
                canvas.drawImageLattice(image, lattice1, r)
            }
        }

        val fixedColorX = intArrayOf(2, 4, 1)
        val fixedColorY = intArrayOf(1, 1, 2)

        val flags = MutableList(36) { LatticeFlags.DEFAULT }
        flags[4] = LatticeFlags.TRANSPARENT
        flags[9] = LatticeFlags.TRANSPARENT
        flags[12] = LatticeFlags.TRANSPARENT
        flags[19] = LatticeFlags.TRANSPARENT

        val colors = MutableList<Color>(36) { Color.TRANSPARENT }
        for (rectNum in 0 until 3) {
            colors[fixedColorY[rectNum] * 6 + fixedColorX[rectNum]] = Color.BLACK
        }

        val lattice2 = Lattice(
            xDivs = xDivs.toList(),
            yDivs = yDivs.toList(),
            flags = flags,
            colors = colors,
        )

        canvas.translate(400f, 0f)
        for (iy in 0 until 2) {
            for (ix in 0 until 2) {
                val i = ix * 2 + iy
                val r = Rect.fromXYWH(
                    x + ix * 60, y + iy * 60,
                    sizes[i].first, sizes[i].second,
                )
                canvas.drawImageLattice(image, lattice2, r)
            }
        }

        canvas.restore()
    }

    private fun makeImage(
        xDivs: IntArray, yDivs: IntArray,
        padLeft: Int, padTop: Int, padRight: Int, padBottom: Int,
    ): org.graphiks.kanvas.image.Image {
        val kCap = 28
        val kMid = 8
        val kSize = 2 * kCap + 3 * kMid
        val totalW = kSize + padLeft + padRight
        val totalH = kSize + padTop + padBottom
        val surf = Surface(totalW, totalH)
        surf.canvas {
            translate(padLeft.toFloat(), padTop.toFloat())

            xDivs[1] = kCap + padLeft
            yDivs[1] = kCap + padTop
            xDivs[2] = kCap + kMid + padLeft
            yDivs[2] = kCap + kMid + padTop
            xDivs[3] = kCap + 2 * kMid + padLeft
            yDivs[3] = kCap + 2 * kMid + padTop
            xDivs[4] = kCap + 3 * kMid + padLeft
            yDivs[4] = kCap + 3 * kMid + padTop

            val r = Rect.fromXYWH(0f, 0f, kSize.toFloat(), kSize.toFloat())
            val strokeWidth = 6f
            val radius = kCap - strokeWidth / 2f

            drawRoundRect(r, radius, radius, Paint(color = Color(0xFFFFFF00u), antiAlias = true))

            var stripe = Rect.fromXYWH(kCap.toFloat(), 0f, kMid.toFloat(), kSize.toFloat())
            drawRect(stripe, Paint(color = Color(0x8800FF00u)))
            stripe = Rect.fromXYWH((kCap + kMid).toFloat(), 0f, kMid.toFloat(), kSize.toFloat())
            drawRect(stripe, Paint(color = Color(0x880000FFu)))
            stripe = Rect.fromXYWH((kCap + 2 * kMid).toFloat(), 0f, kMid.toFloat(), kSize.toFloat())
            drawRect(stripe, Paint(color = Color(0x88FF00FFu)))

            stripe = Rect.fromXYWH(0f, kCap.toFloat(), kSize.toFloat(), kMid.toFloat())
            drawRect(stripe, Paint(color = Color(0x8800FF00u)))
            stripe = Rect.fromXYWH(0f, (kCap + kMid).toFloat(), kSize.toFloat(), kMid.toFloat())
            drawRect(stripe, Paint(color = Color(0x880000FFu)))
            stripe = Rect.fromXYWH(0f, (kCap + 2 * kMid).toFloat(), kSize.toFloat(), kMid.toFloat())
            drawRect(stripe, Paint(color = Color(0x88FF00FFu)))
        }
        return surf.makeImageSnapshot()
    }
}
