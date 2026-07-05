package org.graphiks.kanvas.skia.gm.composite

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
import org.graphiks.kanvas.types.Rect

class BadPaintGm : SkiaGm {
    override val name = "badpaint"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 31.0
    override val width = 100
    override val height = 100

    private val paints by lazy {
        val green = Color.fromRGBA(0f, 1f, 0f, 1f)

        val emptyPixels = ByteArray(4)
        val emptyImage = Image.fromPixels(1, 1, emptyPixels, ColorType.RGBA_8888)
        val badMatrix = Matrix33.scale(0f, 0f)

        val bluePixels = ByteArray(10 * 10 * 4)
        for (i in 0 until 10 * 10) {
            bluePixels[i * 4] = 0x00.toByte()
            bluePixels[i * 4 + 1] = 0x00.toByte()
            bluePixels[i * 4 + 2] = (-1).toByte()
            bluePixels[i * 4 + 3] = (-1).toByte()
        }
        val blueImage = Image.fromPixels(10, 10, bluePixels, ColorType.RGBA_8888)

        listOf(
            Paint(color = green, shader = Shader.Image(emptyImage, TileMode.CLAMP, TileMode.CLAMP)),
            Paint(
                color = green,
                shader = Shader.WithLocalMatrix(
                    Shader.Image(blueImage, TileMode.CLAMP, TileMode.CLAMP),
                    badMatrix,
                ),
            ),
        )
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rect = Rect.fromXYWH(10f, 10f, 80f, 80f)
        for (paint in paints) {
            canvas.drawRect(rect, paint)
        }
    }
}
