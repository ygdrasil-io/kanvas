package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkM44
import org.graphiks.math.SkRect
import org.graphiks.math.SkV3
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils
import kotlin.math.PI

/**
 * Port of Skia's
 * [`gm/pictureshader.cpp::DEF_SIMPLE_GM(pictureshader_persp, canvas, 215, 110)`](https://github.com/google/skia/blob/main/gm/pictureshader.cpp#L239).
 *
 * Exercises `SkPicture.makeShader` with `kDecal × kDecal` tiling and a bounds
 * rect under a perspective (SkM44) CTM. The GM draws two 100×100 views
 * side-by-side, each showing the same `Hamburgefons` text picture projected
 * through a perspective matrix:
 *
 *  - **kDirect**: `canvas.clipRect(0..50, 0..50, true); canvas.drawPicture(picture)`
 *  - **kPictureShader**: `picture.makeShader(kDecal, kDecal, kLinear, null, bounds={0..50, 0..50})`
 *    followed by `canvas.drawRect(0..50, 0..50, paint)`
 *
 * The perspective matrix is:
 * ```
 * m = Scale(2, 2) ∘ Perspective(0.01, 10, π/3) ∘ Translate(0, 5, -0.1) ∘ Rotate(Y-axis, 0.008)
 * ```
 * (built in-place then `postConcat`'d into `m`).
 *
 * The canvas is cleared to black, translated by (5, 5), then each strategy is
 * drawn 105 px apart (separated by a white outline rect).
 *
 * Output: 215 × 110.
 *
 * **Note** — `SkTextBlob.MakeFromString` is used here and is implemented
 * via [SkTextBlob.MakeFromString] in kanvas-skia's companion. Font hinting
 * is set to [SkFontHinting.kNormal] and stored on the font; the current
 * raster text path does not implement distinct hinting modes.
 */
public class PictureShaderPerspGM : GM() {

    override fun getName(): String = "pictureshader_persp"
    override fun getISize(): SkISize = SkISize.Make(215, 110)

    private var fPicture: SkPicture? = null

    override fun onOnceBeforeDraw() {
        // Build the text picture (records into a [0,0]..[100,100] bounds).
        val typeface = ToolUtils.DefaultPortableTypeface()
        val font = SkFont(typeface).apply {
            hinting = SkFontHinting.kNormal
            size = 8f
        }

        val paint = SkPaint().apply { color = SK_ColorGREEN }
        val recorder = SkPictureRecorder()
        val recordCanvas = recorder.beginRecording(SkRect.MakeLTRB(0f, 0f, 100f, 100f))
        val blob = SkTextBlob.MakeFromString("Hamburgefons", font)
        if (blob != null) {
            recordCanvas.drawTextBlob(blob, 0f, 16f, paint)
        }
        fPicture = recorder.finishRecordingAsPicture()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val picture = fPicture ?: return

        // Build the perspective matrix (mirrors upstream exactly):
        //   m = Scale(2,2)
        //   persp = Perspective(0.01f, 10f, PI/3f)
        //   persp.preTranslate(0f, 5f, -0.1f)
        //   persp.preConcat(Rotate(Y=(0,1,0), angle=0.008f))
        //   m.postConcat(persp)
        val m = SkM44().apply { preScale(2f, 2f) }
        val persp = SkM44.perspective(0.01f, 10f, (PI / 3.0).toFloat())
        persp.preTranslate(0f, 5f, -0.1f)
        persp.preConcat(SkM44.rotate(SkV3(0f, 1f, 0f), 0.008f))
        m.postConcat(persp)

        c.clear(SK_ColorBLACK)
        c.translate(5f, 5f)

        for (strategy in DrawStrategy.entries) {
            c.save()

            val outline = SkPaint().apply {
                color = SK_ColorWHITE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 1f
            }
            c.drawRect(SkRect.MakeLTRB(-1f, -1f, 101f, 101f), outline)

            c.clipRect(SkRect.MakeWH(100f, 100f))
            c.concat(m)

            drawPicture(c, picture, strategy)
            c.restore()

            c.translate(105f, 0f)
        }
    }

    private fun drawPicture(canvas: SkCanvas, picture: SkPicture, strategy: DrawStrategy) {
        // Only want local upper 50×50 of 'picture' before we apply decal (or clip).
        val bounds = SkRect.MakeLTRB(0f, 0f, 50f, 50f)
        when (strategy) {
            DrawStrategy.kDirect -> {
                canvas.clipRect(bounds, doAntiAlias = true)
                canvas.drawPicture(picture)
            }
            DrawStrategy.kPictureShader -> {
                val paint = SkPaint().apply {
                    shader = picture.makeShader(
                        tileX = SkTileMode.kDecal,
                        tileY = SkTileMode.kDecal,
                        filter = SkFilterMode.kLinear,
                        localMatrix = null,
                        tile = bounds,
                    )
                }
                canvas.drawRect(SkRect.MakeWH(50f, 50f), paint)
            }
        }
    }

    private enum class DrawStrategy { kDirect, kPictureShader }
}
