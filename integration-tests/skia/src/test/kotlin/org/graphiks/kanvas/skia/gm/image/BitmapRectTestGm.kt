package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmaprecttest.cpp::bitmaprecttest`.
 * Precision bug regression test for drawImageRect with non-trivial dst.
 * @see https://github.com/google/skia/blob/main/gm/bitmaprecttest.cpp
 */
class BitmapRectTestGm : SkiaGm {
    override val name = "bitmaprecttest"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 240

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeImage()

        canvas.drawImage(image, Rect.fromXYWH(150f, 45f, image.width.toFloat(), image.height.toFloat()))

        val scale = 0.472560018f
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawImageRect(
            image,
            Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            Rect.fromXYWH(100f, 100f, 128f, 128f),
        )
        canvas.restore()

        canvas.scale(-1f, 1f)
        canvas.drawImage(image, Rect.fromXYWH(-310f, 45f, image.width.toFloat(), image.height.toFloat()))
    }

    private fun makeImage(): Image {
        val surf = Surface(60, 60)
        surf.canvas {
            drawPath(
                Path { moveTo(6f, 6f); lineTo(6f, 54f); lineTo(30f, 54f); close() },
                Paint(),
            )
            drawRect(
                Rect.fromLTRB(0.5f, 0.5f, 59.5f, 59.5f),
                Paint(style = PaintStyle.STROKE, strokeWidth = 1f),
            )
        }
        return surf.makeImageSnapshot()
    }
}
