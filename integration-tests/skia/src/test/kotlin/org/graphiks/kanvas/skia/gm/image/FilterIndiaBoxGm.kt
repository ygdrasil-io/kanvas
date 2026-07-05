package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class FilterIndiaBoxGm : SkiaGm {
    override val name = "filterindiabox"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 680
    override val height = 130

    private var fImage: Image? = null
    private var fW = 200
    private var fH = 55
    private val fMatrix = arrayOf(Matrix33.identity(), Matrix33.identity())

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
        fMatrix[0] = Matrix33.scale(horizScale, vertScale)
        fMatrix[1] = Matrix33.rotate(30f) * Matrix33.scale(horizScale, vertScale)
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

    private fun computeSize(w: Float, h: Float, mat: Matrix33): Pair<Float, Float> {
        val corners = listOf(
            mat * Point(0f, 0f), mat * Point(w, 0f),
            mat * Point(w, h), mat * Point(0f, h),
        )
        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        val maxX = corners.maxOf { it.x }
        val maxY = corners.maxOf { it.y }
        return (maxX - minX) to (maxY - minY)
    }

    private fun drawRow(canvas: GmCanvas, mat: Matrix33, dx: Float) {
        drawCell(canvas, mat, 0f * dx)
        drawCell(canvas, mat, 1f * dx)
        drawCell(canvas, mat, 2f * dx)
        drawCell(canvas, mat, 3f * dx)
    }

    private fun drawCell(canvas: GmCanvas, mat: Matrix33, dx: Float) {
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
