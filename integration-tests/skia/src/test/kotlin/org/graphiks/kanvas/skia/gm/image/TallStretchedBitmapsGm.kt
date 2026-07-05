package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.drawArc
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/tallstretchedbitmaps.cpp::TallStretchedBitmapsGM`.
 * Progressively taller bitmaps with arc content, drawn via drawImageRect.
 * @see https://github.com/google/skia/blob/main/gm/tallstretchedbitmaps.cpp
 */
class TallStretchedBitmapsGm : SkiaGm {
    override val name = "tall_stretched_bitmaps"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 730
    override val height = 690

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(1.3f, 1.3f)
        for (i in 0 until 8) {
            val rawH = (4 + i) * 1024
            val (bmp, itemCnt) = makeBm(rawH)
            val startItem = itemCnt - 10
            val itemHeight = bmp.height / itemCnt
            val subRect = Rect.fromLTRB(
                0f, (startItem * itemHeight).toFloat(),
                bmp.width.toFloat(), bmp.height.toFloat(),
            )
            val dstRect = Rect.fromXYWH(0f, 0f, bmp.width.toFloat(), (10f * itemHeight).toFloat())
            canvas.drawImageRect(bmp, subRect, dstRect)
            canvas.translate((bmp.width + 10).toFloat(), 0f)
        }
    }
}

private const val K_RADIUS = 22
private const val K_MARGIN = 8
private const val K_START_ANGLE = 0f
private const val K_DANGLE = 25f
private const val K_SWEEP = 320f
private const val K_THICKNESS = 8f

private fun makeBm(rawHeight: Int): Pair<org.graphiks.kanvas.image.Image, Int> {
    val count = rawHeight / (2 * K_RADIUS + K_MARGIN)
    val height = count * (2 * K_RADIUS + K_MARGIN)
    val width = 2 * (K_RADIUS + K_MARGIN)
    val surf = Surface(width, height)
    val random = Random(0)

    surf.canvas {
        var angle = K_START_ANGLE
        for (i in 0 until count) {
            save()
            translate(
                (K_MARGIN + K_RADIUS).toFloat(),
                (i * (K_MARGIN + 2 * K_RADIUS) + K_MARGIN + K_RADIUS).toFloat(),
            )
            val raw = random.nextInt()
            val colorInt = raw or (0xFF000000.toInt())
            val r = ((colorInt ushr 16) and 0xFF) / 255f
            val g = ((colorInt ushr 8) and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            val paint = Paint(
                color = Color.fromRGBA(r, g, b),
                style = PaintStyle.STROKE,
                strokeWidth = K_THICKNESS,
                strokeCap = StrokeCap.ROUND,
                antiAlias = true,
            )
            val radius = K_RADIUS - K_THICKNESS / 2f
            val bounds = Rect.fromLTRB(-radius, -radius, radius, radius)
            drawArc(bounds, angle, K_SWEEP, useCenter = false, paint)
            restore()
            angle += K_DANGLE
        }
    }
    return Pair(surf.makeImageSnapshot(), count)
}
