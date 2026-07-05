package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

// Shared helpers for backdrop.cpp GMs

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
