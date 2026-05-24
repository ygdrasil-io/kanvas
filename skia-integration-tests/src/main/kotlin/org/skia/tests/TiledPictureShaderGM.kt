package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/pictureshader.cpp::DEF_SIMPLE_GM(tiled_picture_shader, canvas, 400, 400)`](https://github.com/google/skia/blob/main/gm/pictureshader.cpp#L209).
 *
 * Regression test for https://code.google.com/p/skia/issues/detail?id=3398.
 *
 * Builds a 100×100 tile picture:
 *  - Inset rect at (4,4,96,96) in dark-blue (0xFF303F9F quantised via 565).
 *  - A 10-wide light-blue diagonal line from (20,20) to (80,80).
 *
 * Then draws the canvas in three layers:
 *  1. Green background paint covering the whole 400×400 canvas.
 *  2. Gray background clipped to 400×350 (leaving a green stripe at the bottom).
 *  3. Picture shader (kRepeat × kRepeat, kNearest) covering the whole clipped area.
 *
 * Colors go through `ToolUtils.colorTo565` to match the 565-quantised reference
 * PNG that was captured on an RGB-565 backbuffer — upstream Skia does the same.
 *
 * Output: 400 × 400.
 */
public class TiledPictureShaderGM : GM() {

    override fun getName(): String = "tiled_picture_shader"
    override fun getISize(): SkISize = SkISize.Make(400, 400)

    private var fPicture: SkPicture? = null

    override fun onOnceBeforeDraw() {
        val tile = SkRect.MakeWH(100f, 100f)
        val recorder = SkPictureRecorder()
        val c = recorder.beginRecording(tile)

        val r = SkRect.MakeLTRB(4f, 4f, 96f, 96f)
        val p = SkPaint().apply {
            color = ToolUtils.colorTo565(0xFF303F9F.toInt()) // dark blue
        }
        c.drawRect(r, p)

        p.color = ToolUtils.colorTo565(0xFFC5CAE9.toInt()) // light blue
        p.strokeWidth = 10f
        c.drawLine(20f, 20f, 80f, 80f, p)

        fPicture = recorder.finishRecordingAsPicture()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val picture = fPicture ?: return

        val p = SkPaint().apply {
            color = ToolUtils.colorTo565(0xFF8BC34A.toInt()) // green
        }
        c.drawPaint(p)

        c.clipRect(SkRect.MakeXYWH(0f, 0f, 400f, 350f))
        p.color = 0xFFB6B6B6.toInt() // gray
        c.drawPaint(p)

        p.shader = picture.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            filter = SkFilterMode.kNearest,
        )
        c.drawPaint(p)
    }
}
