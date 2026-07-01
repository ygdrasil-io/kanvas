package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's gm/androidblendmodes.cpp.
 * Recreates the blend-mode reference grid from the Android docs.
 * @see https://github.com/google/skia/blob/main/gm/androidblendmodes.cpp
 */
class AndroidBlendModesGm : SkiaGm {
    override val name = "androidblendmodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 30.0
    override val width = 1024
    override val height = 1280

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas, kWhiteInt, kGreyInt, 32)

        var xOffset = 0
        var yOffset = 0

        // Same iteration order as upstream
        val modes = listOf(
            BlendMode.PLUS, BlendMode.CLEAR,
            BlendMode.DARKEN, BlendMode.DST,
            BlendMode.DST_ATOP, BlendMode.DST_IN,
            BlendMode.DST_OUT, BlendMode.DST_OVER,
            BlendMode.LIGHTEN, BlendMode.MODULATE,
            BlendMode.OVERLAY, BlendMode.SCREEN,
            BlendMode.SRC, BlendMode.SRC_ATOP,
            BlendMode.SRC_IN, BlendMode.SRC_OUT,
            BlendMode.SRC_OVER, BlendMode.XOR,
        )

        for (mode in modes) {
            canvas.save()
            drawTile(canvas, xOffset.toFloat(), yOffset.toFloat(), mode)
            canvas.restore()

            xOffset += kBitmapSize
            if (xOffset >= kNumCols * kBitmapSize) {
                xOffset = 0
                yOffset += kBitmapSize
            }
        }
    }

    private fun drawTile(canvas: GmCanvas, xOffset: Float, yOffset: Float, mode: BlendMode) {
        canvas.translate(xOffset, yOffset)
        canvas.clipRect(Rect(0f, 0f, kBitmapSize.toFloat(), kBitmapSize.toFloat()))
        canvas.save()

        // Draw destination circle
        val dstPaint = Paint(
            color = kRed,
            antiAlias = true,
        )
        canvas.drawCircle(160f, 95f, 80f, dstPaint)

        // Draw source rectangle with blend mode
        val srcPaint = Paint(
            color = kBlue,
            blendMode = mode,
            antiAlias = true,
        )
        canvas.drawRect(Rect(16f, 96f, 160f, 240f), srcPaint)
    }

    private fun drawCheckerboard(canvas: GmCanvas, c1: Int, c2: Int, size: Int) {
        val w = kNumCols * kBitmapSize
        val h = kNumRows * kBitmapSize
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cx = x / size
                val cy = y / size
                val solid = Paint(
                    color = if (((cx + cy) and 1) == 0) intToColor(c2) else intToColor(c1),
                    antiAlias = false,
                )
                canvas.drawRect(
                    Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    solid,
                )
                x += size
            }
            y += size
        }
    }

    private fun intToColor(value: Int): Color {
        val a = (value ushr 24) and 0xFF
        val r = (value ushr 16) and 0xFF
        val g = (value ushr 8) and 0xFF
        val b = value and 0xFF
        return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    private companion object {
        const val kBitmapSize: Int = 256
        const val kNumRows: Int = 5
        const val kNumCols: Int = 4

        val kBlue = Color.fromRGBA(22f/255f, 150f/255f, 243f/255f, 1f)
        val kRed = Color.fromRGBA(233f/255f, 30f/255f, 99f/255f, 1f)
        val kWhite = Color.fromRGBA(243f/255f, 243f/255f, 243f/255f, 1f)
        val kGrey = Color.fromRGBA(222f/255f, 222f/255f, 222f/255f, 1f)

        const val kWhiteInt = 0xFFF3F3F3.toInt()
        const val kGreyInt = 0xFFDEDEDE.toInt()
    }
}
