package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkM44
import org.graphiks.math.SkRect
import org.graphiks.math.SkSize
import org.graphiks.math.SkV3
import kotlin.math.PI
import kotlin.math.tan

/**
 * Port of Skia's [`gm/3d.cpp`](https://github.com/google/skia/blob/main/gm/3d.cpp)
 * `sk3d_simple` GM (300 × 300).
 *
 * Exercises the `SkM44`-based 3D camera pipeline by drawing one red
 * rectangle directly onto the canvas and one half-transparent blue
 * rectangle through `SkPictureRecorder` + `canvas.drawPicture`, both
 * with the same model-view-projection matrix:
 *
 *   `viewport * perspective * camera * model * inv(viewport)`.
 */
public class Sk3dSimpleGM : GM() {

    override fun getName(): String = "sk3d_simple"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        doDraw(c, 0xFFFF0000.toInt())

        val recorder = SkPictureRecorder()
        val recCanvas = recorder.beginRecording(300f, 300f)
        doDraw(recCanvas, 0x880000FF.toInt())
        val pic = recorder.finishRecordingAsPicture()
        c.drawPicture(pic)
    }

    private fun doDraw(canvas: SkCanvas, color: Int) {
        val save = canvas.save()
        try {
            val info = Info()
            val model = SkM44.rotate(SkV3(0f, 1f, 0f), (PI / 6.0).toFloat())
            canvas.concat(makeCtm(info, model, SkSize.Make(300f, 300f)))
            canvas.translate(150f, 150f)
            val paint = SkPaint().apply { this.color = color }
            canvas.drawRect(SkRect.MakeLTRB(-100f, -100f, 100f, 100f), paint)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private data class Info(
        val near: Float = 0.05f,
        val far: Float = 4f,
        val angle: Float = (PI / 4.0).toFloat(),
    ) {
        val eye: SkV3 = SkV3(0f, 0f, 1f / tan((angle / 2f).toDouble()).toFloat() - 1f)
        val coa: SkV3 = SkV3(0f, 0f, 0f)
        val up: SkV3 = SkV3(0f, 1f, 0f)
    }

    private fun makeCtm(info: Info, model: SkM44, size: SkSize): SkM44 {
        val w = size.width
        val h = size.height

        val perspective = SkM44.perspective(info.near, info.far, info.angle)
        val camera = SkM44.lookAt(info.eye, info.coa, info.up)
        val viewport = SkM44().also { it.setScale(w * 0.5f, h * 0.5f, 1f) }

        val invViewport = viewport.invert() ?: SkM44.identity()

        // viewport * perspective * camera * model * inv(viewport)
        return viewport * perspective * camera * model * invViewport
    }
}
