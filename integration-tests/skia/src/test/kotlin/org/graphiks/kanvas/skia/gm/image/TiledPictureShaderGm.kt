/**
 * Port of Skia's `gm/pictureshader.cpp::tiled_picture_shader`.
 * Regression test for https://code.google.com/p/skia/issues/detail?id=3398.
 * Builds a 100x100 tile picture with dark-blue rect + light-blue diagonal,
 * then tiles it as a repeat shader over a clipped green/gray background.
 * @see https://github.com/google/skia/blob/main/gm/pictureshader.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's
 * [`gm/pictureshader.cpp::DEF_SIMPLE_GM(tiled_picture_shader, canvas, 400, 400)`](https://github.com/google/skia/blob/main/gm/pictureshader.cpp#L209).
 *
 * Regression test for https://code.google.com/p/skia/issues/detail?id=3398.
 *
 * Builds a 100x100 tile picture:
 *  - Inset rect at (4,4,96,96) in dark-blue.
 *  - A 10-wide light-blue diagonal line from (20,20) to (80,80).
 *
 * Then draws the canvas in three layers:
 *  1. Green background paint covering the whole 400x400 canvas.
 *  2. Gray background clipped to 400x350 (leaving a green stripe at the bottom).
 *  3. Picture shader (kRepeat x kRepeat, kNearest) covering the whole clipped area.
 *
 * Output: 400 x 400.
 */
class TiledPictureShaderGm : SkiaGm {

    override val name = "tiled_picture_shader"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val tile = Rect.fromLTRB(0f, 0f, 100f, 100f)
        val recorder = PictureRecorder()
        val c = recorder.beginRecording(tile)

        val r = Rect.fromLTRB(4f, 4f, 96f, 96f)
        var p = Paint(color = Color.fromRGBA(0x30f / 255f, 0x3Ff / 255f, 0x9Ff / 255f))
        c.drawRect(r, p)

        p = p.copy(
            color = Color.fromRGBA(0xC5f / 255f, 0xCAf / 255f, 0xE9f / 255f),
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
        )
        val linePath = Path { moveTo(20f, 20f); lineTo(80f, 80f) }
        c.drawPath(linePath, p)

        val picture = recorder.finishRecordingAsPicture()

        var paint = Paint(color = Color.fromRGBA(0x8Bf / 255f, 0xC3f / 255f, 0x4Af / 255f))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, width.toFloat(), height.toFloat()), paint)

        canvas.clipRect(Rect.fromXYWH(0f, 0f, 400f, 350f))
        paint = paint.copy(color = Color.fromRGBA(0xB6f / 255f, 0xB6f / 255f, 0xB6f / 255f))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, width.toFloat(), height.toFloat()), paint)

        paint = paint.copy(shader = picture.asShader(
            tileX = TileMode.REPEAT,
            tileY = TileMode.REPEAT,
            sampling = SamplingOptions.NEAREST,
        ))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, width.toFloat(), height.toFloat()), paint)
    }
}
