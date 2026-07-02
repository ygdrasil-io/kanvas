package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class BigMatrixGm : SkiaGm {
    override val name = "bigmatrix"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 50
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x66 / 255f, 0xAA / 255f, 0x99 / 255f)

        val m = Matrix33.rotate(33f) * Matrix33.scale(3000f, 3000f) * Matrix33.translate(6000f, -5000f)
        canvas.concat(m)

        val inv = invertAffine(m) ?: error("BigMatrixGm: m.invert() failed")
        val paint = Paint(color = Color.RED, antiAlias = true)

        val small = 1f / 500f
        fun mapPoint(x: Float, y: Float): Point = inv * Point(x, y)

        var pt = mapPoint(10f, 10f)
        canvas.drawCircle(pt.x, pt.y, small, paint)

        pt = mapPoint(30f, 10f)
        canvas.drawRect(Rect(pt.x - small, pt.y - small, pt.x + small, pt.y + small), paint)

        val bmpPixels = byteArrayOf(
            (-1).toByte(), 0, 0, (-1).toByte(),
            0, (-1).toByte(), 0, (-1).toByte(),
            0, 0, 0, 0x80.toByte(),
            0, 0, (-1).toByte(), (-1).toByte(),
        )
        val bmp = Image.fromPixels(2, 2, bmpPixels)

        pt = mapPoint(30f, 30f)
        val s = Matrix33.scale(1f / 1000f, 1f / 1000f)
        canvas.drawRect(
            Rect(pt.x - small, pt.y - small, pt.x + small, pt.y + small),
            Paint(
                shader = Shader.WithLocalMatrix(
                    Shader.Image(bmp, TileMode.REPEAT, TileMode.REPEAT), s,
                ),
            ),
        )
    }

    private fun invertAffine(m: Matrix33): Matrix33? {
        val det = m.scaleX * m.scaleY - m.skewX * m.skewY
        if (det == 0f) return null
        val invDet = 1f / det
        return Matrix33.makeAll(
            m.scaleY * invDet, -m.skewX * invDet, (m.skewX * m.transY - m.scaleY * m.transX) * invDet,
            -m.skewY * invDet, m.scaleX * invDet, (m.skewY * m.transX - m.scaleX * m.transY) * invDet,
        )
    }
}
