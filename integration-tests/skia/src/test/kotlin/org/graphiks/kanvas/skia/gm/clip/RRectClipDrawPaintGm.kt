package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rrectclipdrawpaint.cpp` (256 × 256).
 *
 * Exercises a [clipRRect] followed by a full-canvas [drawRect] (stand-in
 * for `drawPaint`). Tests solid / linear / radial gradient fills through
 * the rrect clip under successive zoom-out transforms.
 * @see https://github.com/google/skia/blob/main/gm/rrectclipdrawpaint.cpp
 */
class RRectClipDrawPaintGm : SkiaGm {
    override val name = "rrect_clip_draw_paint"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rrect = RRect(Rect.fromXYWH(10f, 10f, 236f, 236f), radius = 0f).copy(
            topLeft = org.graphiks.kanvas.types.CornerRadii(30f, 40f),
            topRight = org.graphiks.kanvas.types.CornerRadii(30f, 40f),
            bottomRight = org.graphiks.kanvas.types.CornerRadii(30f, 40f),
            bottomLeft = org.graphiks.kanvas.types.CornerRadii(30f, 40f),
        )
        val zoomOut = Matrix33.translate(128f, 128f) * Matrix33.scale(0.7f, 0.7f) * Matrix33.translate(-128f, -128f)
        val layerRect = Rect.fromXYWH(0f, 0f, 256f, 256f)

        var p = Paint(color = Color.RED)
        canvas.saveLayer(layerRect, null)
        canvas.clipRRect(rrect, antiAlias = true)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()), p)
        canvas.restore()

        canvas.concat(zoomOut)
        p = p.copy(color = Color.BLUE)
        canvas.saveLayer(layerRect, null)
        canvas.clipRRect(rrect, antiAlias = false)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()), p)
        canvas.restore()

        val cyan = Color(0xFF00FFFFu)
        val green = Color(0xFF00FF00u)
        p = p.copy(shader = Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(256f, 256f),
            stops = listOf(GradientStop(0f, cyan), GradientStop(1f, green)),
        ))
        canvas.concat(zoomOut)
        canvas.saveLayer(layerRect, null)
        canvas.clipRRect(rrect, antiAlias = true)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()), p)
        canvas.restore()

        val magenta = Color(0xFFFF00FFu)
        val gray = Color(0xFF888888u)
        p = p.copy(shader = Shader.RadialGradient(
            center = Point(128f, 128f), radius = 128f,
            stops = listOf(GradientStop(0f, magenta), GradientStop(1f, gray)),
        ))
        canvas.concat(zoomOut)
        canvas.saveLayer(layerRect, null)
        canvas.clipRRect(rrect, antiAlias = false)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()), p)
        canvas.restore()
    }
}
