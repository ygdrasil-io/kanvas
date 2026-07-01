package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class ModeColorFilterGm : SkiaGm {
    override val name = "modecolorfilters"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x30 / 255f, 0x30 / 255f, 0x30 / 255f, 1f)

        val kRectWidth = 20
        val kRectHeight = 20

        val bgPaint = Paint(
            color = Color.fromRGBA(0x80 / 255f, 0f, 0f, 1f),
            blendMode = BlendMode.SRC,
        )

        val colors = listOf(
            Color.fromRGBA(1f, 1f, 1f, 1f),
            Color.fromRGBA(0f, 0f, 0f, 1f),
            Color.fromRGBA(0f, 0f, 0f, 0f),
            Color.fromRGBA(0x10 / 255f, 0x20 / 255f, 0x42 / 255f, 1f),
            Color.fromRGBA(0x20 / 255f, 0x30 / 255f, 0x90 / 255f, 0xA0 / 255f),
        )

        val alphas = listOf(
            Color.fromRGBA(1f, 1f, 1f, 1f),
            Color.fromRGBA(0x80 / 255f, 0x80 / 255f, 0x80 / 255f, 0x80 / 255f),
        )

        val modes = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER,
            BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT,
            BlendMode.DST_OUT, BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR,
            BlendMode.PLUS, BlendMode.MODULATE,
        )

        var idx = 0
        val rectsPerRow = maxOf(width / kRectWidth, 1)
        for (cfm in modes.indices) {
            for (cfc in colors.indices) {
                val colorFilter = ColorFilter.Blend(colors[cfc], modes[cfm])
                for (sIdx in 0..3) {
                    val hasShader = sIdx == 0
                    val paintColors = if (hasShader) alphas else colors
                    for (pc in paintColors.indices) {
                        val x = (idx % rectsPerRow).toFloat()
                        val y = (idx / rectsPerRow).toFloat()
                        val rect = Rect.fromXYWH(
                            x * kRectWidth, y * kRectHeight,
                            kRectWidth.toFloat(), kRectHeight.toFloat(),
                        )
                        canvas.save()
                        canvas.drawRect(rect, bgPaint)
                        val testPaint = Paint(
                            color = paintColors[pc],
                            colorFilter = colorFilter,
                        )
                        canvas.drawRect(rect, testPaint)
                        canvas.restore()
                        ++idx
                    }
                }
            }
        }
    }
}
