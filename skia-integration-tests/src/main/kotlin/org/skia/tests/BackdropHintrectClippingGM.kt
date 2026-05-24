package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/backdrop.cpp::DEF_SIMPLE_GM(backdrop_hintrect_clipping, 512, 1024)`.
 *
 * 2x4 grid of sweep circles with backdrop blur. Each row tests a
 * combination of `useHintRect` / `useClip`. The left column draws
 * straight to the canvas, the right column renders the same draw
 * through an `SkPictureRecorder` and plays back.
 *
 * In the C++ original, the backdrop layer is created via
 * `SkCanvasPriv::ScaledBackdropLayer(drawrptr, nullptr, blur.get(),
 * scaleFactor, 0)` (here `scaleFactor = 1`). We approximate that with
 * the public [SaveLayerRec] (no scale factor exposed in the public
 * API ; visually identical at `scaleFactor == 1`).
 *
 * The C++ also computes an explicit `blurCrop` rect via
 * `SkMatrixPriv::InverseMapRect` of the canvas's device imageInfo —
 * with an identity CTM (this GM doesn't rotate/scale), it's just the
 * device rect. We pass `null` for the cropRect since our [SkImageFilters.Blur]
 * with `kClamp` tileMode already gives correct edge behaviour.
 */
public class BackdropHintrectClippingGM : GM() {

    override fun getName(): String = "backdrop_hintrect_clipping"
    override fun getISize(): SkISize = SkISize.Make(512, 1024)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        for (useHintRect in booleanArrayOf(false, true)) {
            for (useClip in booleanArrayOf(false, true)) {
                c.save()
                backdropDoDraw(c, useClip, useHintRect, 1.0f)

                val rec = SkPictureRecorder()
                val pCanvas = rec.beginRecording(256f, 256f)
                backdropDoDraw(pCanvas, useClip, useHintRect, 1.0f)
                c.translate(256f, 0f)
                rec.finishRecordingAsPicture().playback(c)
                c.restore()

                c.translate(0f, 256f)
            }
        }
    }
}

// -- shared helpers for backdrop.cpp GMs --------------------------------

internal fun backdropMakeShader(cx: Float, cy: Float): SkSweepGradient {
    val red = 0xFFFF0000.toInt()
    val blue = 0xFF0000FF.toInt()
    val green = 0xFF00FF00.toInt()
    val colors = intArrayOf(
        red, red, blue, blue, green, green,
        red, red, blue, blue, green, green,
    )
    val pos = floatArrayOf(0f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f, 5f, 5f, 6f)
    for (i in pos.indices) pos[i] *= 1f / 6f
    return SkSweepGradient.Make(
        center = SkPoint(cx, cy),
        colors = colors,
        positions = pos,
        tileMode = SkTileMode.kClamp,
    )
}

@Suppress("UNUSED_PARAMETER")
internal fun backdropDoDraw(canvas: SkCanvas, useClip: Boolean, useHintRect: Boolean, scaleFactor: Float) {
    canvas.save()
    canvas.clipRect(SkRect.MakeWH(256f, 256f))

    val cx = 128f
    val cy = 128f
    val rad = 100f
    val p = SkPaint().apply {
        shader = backdropMakeShader(cx, cy)
        isAntiAlias = true
    }
    canvas.drawCircle(cx, cy, rad, p)

    // setup saveLayer with backdrop blur
    val r = SkRect.MakeLTRB(cx - 50, cy - 50, cx + 50, cy + 50)
    val drawrptr: SkRect? = if (useHintRect) r else null
    val sigma = 10f
    if (useClip) {
        canvas.clipRect(r)
    }
    val blur = SkImageFilters.Blur(sigma, sigma, SkTileMode.kClamp, null, null)
    canvas.saveLayer(SaveLayerRec(bounds = drawrptr, backdrop = blur))
    // draw something inside, just to demonstrate that we don't blur the new contents
    p.shader = null
    p.color = SK_ColorYELLOW
    canvas.drawCircle(cx, cy, 30f, p)
    canvas.restore()

    canvas.restore()
}
