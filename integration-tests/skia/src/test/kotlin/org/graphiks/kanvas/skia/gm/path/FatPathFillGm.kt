package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/fatpathfill.cpp::fatpathfill` (288 × 480).
 * Renders a 9×3 alpha bitmap zoomed 32× to make individual pixel-coverage values visible.
 * @see https://github.com/google/skia/blob/main/gm/fatpathfill.cpp
 */
class FatPathFillGm : SkiaGm {
    override val name = "fatpathfill"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 288
    override val height = 480

    private val zoom = 32
    private val smallW = 9
    private val smallH = 3
    private val repeatLoop = 5

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surface = Surface(smallW, smallH)
        canvas.scale(zoom.toFloat(), zoom.toFloat())

        val paint = Paint(style = PaintStyle.STROKE, strokeWidth = 1f)

        for (i in 0 until repeatLoop) {
            val line = Path {
                moveTo(1f, 2f)
                lineTo(4f + i, 1f)
            }
            val strokePath = Path { }
            strokePath.addPath(line)
            drawFatpath(canvas, surface, strokePath)
            canvas.translate(0f, smallH.toFloat())
        }
    }

    private fun drawFatpath(canvas: GmCanvas, surface: Surface, path: Path) {
        val fillPaint = Paint()
        surface.canvas {
            drawRect(Rect(0f, 0f, smallW.toFloat(), smallH.toFloat()), Paint(color = Color.TRANSPARENT))
            drawPath(path, fillPaint)
        }
        val image = surface.makeImageSnapshot()
        canvas.drawImage(image, Rect(0f, 0f, smallW.toFloat(), smallH.toFloat()))

        val strokePaint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
            color = Color.RED,
            antiAlias = true,
        )
        canvas.drawPath(path, strokePaint)
        drawPixelCenters(canvas)
    }

    private fun drawPixelCenters(canvas: GmCanvas) {
        val paint = Paint(
            color = Color.fromRGBA(0f, 0.53f, 1f, 1f),
            antiAlias = true,
        )
        for (y in 0 until smallH) {
            for (x in 0 until smallW) {
                canvas.drawCircle(x + 0.5f, y + 0.5f, 1.5f / zoom, paint)
            }
        }
    }
}
