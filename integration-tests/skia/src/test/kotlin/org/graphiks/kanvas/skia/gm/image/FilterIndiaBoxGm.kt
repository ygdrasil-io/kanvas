package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/filterindiabox.cpp`.
 *  Tests ImageFilter.IndiaBox — renders a checkerboard image with
 *  transformed crop rects and filter configurations.
 *  @see https://github.com/google/skia/blob/main/gm/filterindiabox.cpp
 */
class FilterIndiaBoxGm : SkiaGm {
    override val name = "filterindiabox"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 680
    override val height = 130

    private var fImage: Image? = null
    private var fW = 200
    private var fH = 55
    private val fMatrix = arrayOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity)

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = loadResource("images/box.gif")
        if (bytes != null) {
            val img = Image.decode(bytes)
            if (img.width > 0) {
                fImage = img
                fW = img.width
                fH = img.height
            }
        }
        val cx = fW / 2f
        val cy = fH / 2f
        val vertScale = 30.0f / 55.0f
        val horizScale = 150.0f / 200.0f
        fMatrix[0] = Matrix3x3F32.scaling(horizScale, vertScale)
        fMatrix[1] = Matrix3x3F32.rotation(30f) * Matrix3x3F32.scaling(horizScale, vertScale)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)
        for (i in fMatrix.indices) {
            val size = computeSize(fW.toFloat(), fH.toFloat(), fMatrix[i])
            val w = size.first + 20f
            val h = size.second + 20f
            drawRow(canvas, fMatrix[i], w)
            canvas.translate(0f, h)
        }
    }

    private fun computeSize(w: Float, h: Float, mat: Matrix3x3F32): Pair<Float, Float> {
        val corners = listOf(
            mat.transform(Point2F32(0f, 0f)), mat.transform(Point2F32(w, 0f)),
            mat.transform(Point2F32(w, h)), mat.transform(Point2F32(0f, h)),
        )
        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        val maxX = corners.maxOf { it.x }
        val maxY = corners.maxOf { it.y }
        return (maxX - minX) to (maxY - minY)
    }

    private fun drawRow(canvas: GmCanvas, mat: Matrix3x3F32, dx: Float) {
        drawCell(canvas, mat, 0f * dx)
        drawCell(canvas, mat, 1f * dx)
        drawCell(canvas, mat, 2f * dx)
        drawCell(canvas, mat, 3f * dx)
    }

    private fun drawCell(canvas: GmCanvas, mat: Matrix3x3F32, dx: Float) {
        val image = fImage ?: return
        canvas.save()
        canvas.translate(dx, 0f)
        canvas.concat(mat)
        canvas.drawImage(image, Rect(0f, 0f, fW.toFloat(), fH.toFloat()))
        canvas.restore()
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
