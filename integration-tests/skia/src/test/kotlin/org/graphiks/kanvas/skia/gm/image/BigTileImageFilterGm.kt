package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class BigTileImageFilterGm : SkiaGm {
    override val name = "bigtileimagefilter"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val redImage = createCircleTexture(kBitmapSize, Color.RED)
        val greenImage = createCircleTexture(kBitmapSize, Color.GREEN)

        val bound = Rect.fromXYWH(0f, 0f, kWidth.toFloat(), kHeight.toFloat())
        val tileRed: ImageFilter = ImageFilter.Tile(
            src = Rect.fromXYWH(0f, 0f, kBitmapSize.toFloat(), kBitmapSize.toFloat()),
            dst = Rect.fromXYWH(0f, 0f, kWidth.toFloat(), kHeight.toFloat()),
            input = null,
        )
        val redPaint = Paint(imageFilter = tileRed)
        canvas.saveLayer(bound, redPaint)
        canvas.restore()

        val bound2 = Rect.fromXYWH(0f, 0f, kBitmapSize.toFloat(), kBitmapSize.toFloat())
        val tileGreen: ImageFilter = ImageFilter.Tile(
            src = Rect.fromXYWH(0f, 0f, kBitmapSize.toFloat(), kBitmapSize.toFloat()),
            dst = Rect.fromXYWH(0f, 0f, kBitmapSize.toFloat(), kBitmapSize.toFloat()),
            input = null,
        )
        val greenPaint = Paint(imageFilter = tileGreen)
        canvas.translate(320f, 320f)
        canvas.saveLayer(bound2, greenPaint)
        canvas.setMatrix(org.graphiks.kanvas.types.Matrix33.identity())
        canvas.drawImageRect(greenImage, bound2, Rect.fromXYWH(320f, 320f, kBitmapSize.toFloat(), kBitmapSize.toFloat()))
        canvas.restore()
    }

    private fun createCircleTexture(size: Int, color: Color): Image {
        val surface = Surface(size, size)
        val path = Path { }
        path.addCircle(size / 2f, size / 2f, size / 2f)
        surface.canvas {
            drawColor(Color.fromRGBA(0f, 0f, 0f, 1f))
            drawPath(path, Paint(color = color, style = PaintStyle.STROKE, strokeWidth = 3f))
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        const val kWidth: Int = 512
        const val kHeight: Int = 512
        const val kBitmapSize: Int = 64
    }
}
