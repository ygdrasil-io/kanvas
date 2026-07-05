package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class DuckyYuvBlendGm : SkiaGm {
    override val name = "ducky_yuv_blend"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 560
    override val height = 1130

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val duckyBG = decodeResource("images/ducky.png") ?: return
        val duckyJpeg = decodeResource("images/ducky.jpg") ?: return
        val duckyFG = arrayOf(duckyJpeg, duckyJpeg)

        val kNumPerRow = 4
        val kPad = 10f
        val kDstRect = Rect(0f, 0f, 130f, 130f)
        val bgSrcRect = Rect(0f, 0f, duckyBG.width.toFloat(), duckyBG.height.toFloat())
        val fgSrcRect = Rect(0f, 0f, duckyJpeg.width.toFloat(), duckyJpeg.height.toFloat())

        val separableAndHslModes = listOf(
            BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT,
            BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.MULTIPLY,
            BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY,
        )

        canvas.translate(kPad, kPad)
        canvas.save()

        drawCheckerboard(canvas)

        var rowCnt = 0

        fun newRow() {
            canvas.restore()
            canvas.translate(0f, kDstRect.height + kPad)
            canvas.save()
            rowCnt = 0
        }

        for (fg in duckyFG) {
            for (bm in separableAndHslModes) {
                canvas.drawImageRect(duckyBG, bgSrcRect, kDstRect)
                canvas.drawImageRect(fg, fgSrcRect, kDstRect, Paint(blendMode = bm))
                canvas.translate(kDstRect.width + kPad, 0f)
                if (++rowCnt == kNumPerRow) newRow()
            }
            newRow()
        }

        canvas.restore()
    }

    private fun decodeResource(path: String): Image? {
        val bytes = this::class.java.classLoader?.getResourceAsStream(path)?.readBytes() ?: return null
        val img = Image.decode(bytes)
        return if (img.width > 0) img else null
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val c1 = Color.fromRGBA(0.25f, 0.25f, 0.25f, 1f)
        val c2 = Color.fromRGBA(0.75f, 0.75f, 0.75f, 1f)
        val size = 25
        for (y in 0 until height step size) {
            for (x in 0 until width step size) {
                val on = ((x / size) + (y / size)) % 2 == 0
                canvas.drawRect(
                    Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    Paint(color = if (on) c1 else c2),
                )
            }
        }
    }
}
