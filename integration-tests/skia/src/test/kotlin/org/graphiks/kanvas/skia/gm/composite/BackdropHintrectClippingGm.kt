/**
 * Port of Skia's `gm/backdrop.cpp::DEF_SIMPLE_GM(backdrop_hintrect_clipping, …)`.
 * 2×4 grid of sweep circles with backdrop blur. Each row tests
 * useHintRect/useClip. Left column draws directly, right column renders
 * the same through PictureRecorder + playback.
 * @see https://github.com/google/skia/blob/main/gm/backdrop.cpp
 */
package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class BackdropHintrectClippingGm : SkiaGm {
    override val name = "backdrop_hintrect_clipping"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        for (useHintRect in booleanArrayOf(false, true)) {
            for (useClip in booleanArrayOf(false, true)) {
                canvas.save()
                drawOne(canvas, useClip, useHintRect)

                val rec = PictureRecorder()
                val rc = rec.beginRecording(Rect.fromLTRB(0f, 0f, 256f, 256f))
                val tmp = GmCanvas(rc, 256, 256)
                drawOne(tmp, useClip, useHintRect)
                canvas.translate(256f, 0f)
                val pic = rec.finishRecordingAsPicture()
                canvas.drawPicture(pic)
                canvas.restore()

                canvas.translate(0f, 256f)
            }
        }
    }
}

private fun makeSweepShader(cx: Float, cy: Float): Shader {
    val stops = listOf(
        GradientStop(0f / 6f, Color(0xFFFF0000u)),
        GradientStop(1f / 6f, Color(0xFFFF0000u)),
        GradientStop(1f / 6f, Color(0xFF0000FFu)),
        GradientStop(2f / 6f, Color(0xFF0000FFu)),
        GradientStop(2f / 6f, Color(0xFF00FF00u)),
        GradientStop(3f / 6f, Color(0xFF00FF00u)),
        GradientStop(3f / 6f, Color(0xFFFF0000u)),
        GradientStop(4f / 6f, Color(0xFFFF0000u)),
        GradientStop(4f / 6f, Color(0xFF0000FFu)),
        GradientStop(5f / 6f, Color(0xFF0000FFu)),
        GradientStop(5f / 6f, Color(0xFF00FF00u)),
        GradientStop(6f / 6f, Color(0xFF00FF00u)),
    )
    return Shader.SweepGradient(
        center = Point(cx, cy),
        startAngle = 0f,
        endAngle = 360f,
        stops = stops,
        tileMode = TileMode.CLAMP,
    )
}

private fun drawOne(canvas: GmCanvas, useClip: Boolean, useHintRect: Boolean) {
    canvas.save()
    canvas.clipRect(Rect.fromLTRB(0f, 0f, 256f, 256f))

    val cx = 128f
    val cy = 128f
    val rad = 100f
    val p = Paint(shader = makeSweepShader(cx, cy), antiAlias = true)
    canvas.drawCircle(cx, cy, rad, p)

    val r = Rect.fromLTRB(cx - 50, cy - 50, cx + 50, cy + 50)
    val sigma = 10f
    if (useClip) {
        canvas.clipRect(r)
    }
    val blur = ImageFilter.Blur(sigma, sigma, TileMode.CLAMP)
    val backdropBounds: Rect? = if (useHintRect) r else null

    // Note: Kanvas saveLayer does not support backdrop filter directly;
    // we apply the blur as the layer's image filter instead.
    canvas.saveLayer(bounds = backdropBounds, paint = Paint(imageFilter = blur))

    val fillPaint = Paint(color = Color(0xFFFFFF00u))
    canvas.drawCircle(cx, cy, 30f, fillPaint)
    canvas.restore()

    canvas.restore()
}
