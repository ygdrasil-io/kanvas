package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ResizeImageFilterGm : SkiaGm {
    override val name = "resizeimagefilter"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 630
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val srcRect = Rect.fromXYWH(0f, 0f, 96f, 96f)
        val ovalPath = Path { }
        ovalPath.addOval(Rect.fromLTRB(4f, 4f, 92f, 92f))

        for (i in 0 until 5) {
            draw(canvas, srcRect, ovalPath)
            canvas.translate(srcRect.width + 10f, 0f)
        }

        val surface = Surface(16, 16)
        val smallOvalPath = Path { }
        smallOvalPath.addOval(Rect.fromLTRB(2f / 3f, 2f / 3f, 16f - 2f / 3f, 16f - 2f / 3f))
        surface.canvas {
            drawColor(Color.fromRGBA(0f, 0f, 0f, 0f))
            drawPath(smallOvalPath, Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f)))
        }
        surface.makeImageSnapshot()

        canvas.save()
        canvas.translate(srcRect.left, srcRect.top)
        canvas.scale(16f / 96f, 16f / 96f)
        canvas.translate(-srcRect.left, -srcRect.top)
        canvas.saveLayer(srcRect, null)
        canvas.drawPath(ovalPath, Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f)))
        canvas.restore()
        canvas.restore()
    }

    private fun draw(canvas: GmCanvas, rect: Rect, ovalPath: Path) {
        val deviceScale = 16f / 96f
        canvas.save()
        canvas.translate(rect.left, rect.top)
        canvas.scale(deviceScale, deviceScale)
        canvas.translate(-rect.left, -rect.top)
        canvas.saveLayer(rect, null)
        canvas.drawPath(ovalPath, Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f)))
        canvas.restore()
        canvas.restore()
    }
}
