package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/rrects.cpp` — `RRectGM(kBW_Clip_Type)`.
 *
 * GM name: `rrect_clip_bw`. Each of the 43 rrects is used as a
 * **non-AA clip** (`clipRRect(rrect, false)`). After clipping the canvas
 * matrix is replaced with `Scale(kImageWidth, kImageHeight)` and a
 * 1 × 1 rect is drawn with a black → yellow [SkLinearGradient] shader,
 * which verifies that local coordinates inside the clip are respected.
 *
 * Reference image: `rrect_clip_bw.png`, 640 × 480, BG 0xFFDDDDDD.
 */
public class RRectClipBwGM : GM() {

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun getName(): String = "rrect_clip_bw"
    override fun getISize(): SkISize = SkISize.Make(kImageWidth, kImageHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rrects = buildRRects()

        val paint = SkPaint().apply {
            shader = SkLinearGradient.Make(
                SkPoint(0f, 0f), SkPoint(1.5f, 1f),
                intArrayOf(SK_ColorBLACK, SK_ColorYELLOW),
                null,
                SkTileMode.kClamp,
            )
        }

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            if (idx == kNumRRects - 1) {
                c.clipRect(SkRect.MakeWH((kTileX - 2).toFloat(), (kTileY - 2).toFloat()))
                c.translate(-0.14f * rrects[idx].rect().width(),
                             -0.14f * rrects[idx].rect().height())
            }
            c.clipRRect(rrects[idx], doAntiAlias = false)
            c.setMatrix(SkMatrix.MakeScale(kImageWidth.toFloat(), kImageHeight.toFloat()))
            c.drawRect(SkRect.MakeWH(1f, 1f), paint)
            c.restore()
            x += kTileX
            if (x > kImageWidth) {
                x = 1
                y += kTileY
            }
        }
    }
}
