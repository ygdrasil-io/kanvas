package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bigmatrix.cpp`.
 * Stresses the rasteriser with an extreme CTM:
 * scale(3000) * rotate(33) * translate(6000, -5000).
 * Draws three sub-pixel-sized primitives in device space.
 * @see https://github.com/google/skia/blob/main/gm/bigmatrix.cpp
 */
class BigMatrixGm : SkiaGm {
    override val name = "bigmatrix"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 50
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.388f, 0.667f, 0.580f)

        val m = Matrix33.rotate(33f) * Matrix33.scale(3000f, 3000f) * Matrix33.translate(6000f, -5000f)
        canvas.concat(m)

        val inv = invert33(m)
        val small = 1f / 500f

        var pt = inv * Point(10f, 10f)
        canvas.drawCircle(pt.x, pt.y, small, Paint(color = Color.RED, antiAlias = true))

        pt = inv * Point(30f, 10f)
        canvas.drawRect(
            Rect.fromLTRB(pt.x - small, pt.y - small, pt.x + small, pt.y + small),
            Paint(color = Color.RED, antiAlias = true),
        )

        val bmpPixels = byteArrayOf(
            0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x80.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        )
        val bmp = Image.fromPixels(2, 2, bmpPixels, ColorType.RGBA_8888, "bigmatrix-bmp")

        pt = inv * Point(30f, 30f)
        val s = Matrix33.scale(1f / 1000f, 1f / 1000f)
        val shader = Shader.WithLocalMatrix(bmp.makeShader(TileMode.REPEAT, TileMode.REPEAT), s)
        canvas.drawRect(
            Rect.fromLTRB(pt.x - small, pt.y - small, pt.x + small, pt.y + small),
            Paint(shader = shader, antiAlias = false),
        )
    }
}

internal fun invert33(m: Matrix33): Matrix33 {
    val a = m.scaleX; val b = m.skewX; val c = m.transX
    val d = m.skewY; val e = m.scaleY; val f = m.transY
    val det = a * e - b * d
    return Matrix33.makeAll(
        sx = e / det, kx = -b / det, tx = (b * f - c * e) / det,
        ky = -d / det, sy = a / det, ty = (c * d - a * f) / det,
    )
}
