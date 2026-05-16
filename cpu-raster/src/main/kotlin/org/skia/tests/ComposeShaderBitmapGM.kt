package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withRestore
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's
 * [`gm/composeshader.cpp::ComposeShaderBitmapGM`](https://github.com/google/skia/blob/main/gm/composeshader.cpp).
 *
 * Two variants registered upstream :
 *  - `composeshader_bitmap` — `useLm = false`,
 *  - `composeshader_bitmap_lm` — `useLm = true` (R-final.2 consumer).
 *
 * The `useLm = true` variant wraps each composed `Blend(DstOver,
 * gradient, bitmap)` shader with `shader.makeWithLocalMatrix(
 * Translate(0, sq/2))`, shifting the bitmap-shader's tile origin
 * vertically by half the square length. Visually the two columns
 * (gradient over color bitmap, gradient over alpha-8 bitmap) get
 * shifted down by 10 px ; the gradient stripe stays anchored to
 * device space because the surrounding canvas-CTM doesn't change.
 *
 * 2 × 7 grid of 20 × 20 squares, each square is the same shader
 * drawn at decreasing paint alpha (0xFF → 0x07 step 0x28) so the
 * test exercises the alpha-modulated SrcOver path on top of the
 * shader's own composite output.
 *
 * @param useLm when `true`, register as `composeshader_bitmap_lm` and
 *              wrap each output shader with the local-matrix translate.
 */
public class ComposeShaderBitmapGM(
    private val useLm: Boolean,
) : GM() {

    private var initialized = false
    private lateinit var fColorBitmap: SkBitmap
    private lateinit var fAlpha8Bitmap: SkBitmap
    private var fColorBitmapShader: SkShader? = null
    private var fAlpha8BitmapShader: SkShader? = null
    private var fLinearGradientShader: SkShader? = null

    override fun getName(): String = "composeshader_bitmap${if (useLm) "_lm" else ""}"

    override fun getISize(): SkISize =
        SkISize.Make(7 * (squareLength + 5), 2 * (squareLength + 5))

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (!initialized) initBitmapsAndShaders()

        val mode = SkBlendMode.kDstOver
        val lm = SkMatrix.MakeTrans(0f, squareLength * 0.5f)

        val grad = fLinearGradientShader!!
        val colorBmShader = fColorBitmapShader!!
        val a8BmShader = fAlpha8BitmapShader!!

        var shaders: Array<SkShader> = arrayOf(
            // gradient should appear over color bitmap
            SkShaders.Blend(mode, grad, colorBmShader),
            // gradient should appear over alpha8 bitmap colorized by paint
            SkShaders.Blend(mode, grad, a8BmShader),
        )
        if (useLm) {
            shaders = Array(shaders.size) { i -> shaders[i].makeWithLocalMatrix(lm) }
        }

        val paint = SkPaint().apply { color = SK_ColorYELLOW }
        val r = SkRect.MakeWH(squareLength.toFloat(), squareLength.toFloat())

        for (y in shaders.indices) {
            c.withRestore {
                var alpha = 0xFF
                while (alpha > 0) {
                    paint.alpha = alpha
                    paint.shader = shaders[y]
                    drawRect(r, paint)
                    translate(r.width() + 5f, 0f)
                    alpha -= 0x28
                }
            }
            c.translate(0f, r.height() + 5f)
        }
    }

    private fun initBitmapsAndShaders() {
        // -- color bitmap --------------------------------------------------
        fColorBitmap = SkBitmap(squareLength, squareLength)
        fColorBitmap.eraseColor(SK_ColorRED)
        SkCanvas(fColorBitmap).drawCircle(
            squareLength / 2f, squareLength / 2f, squareLength / 2f,
            SkPaint().apply {
                color = SK_ColorGREEN
                isAntiAlias = false
            },
        )
        fColorBitmapShader = fColorBitmap.makeShader(
            tileX = SkTileMode.kRepeat, tileY = SkTileMode.kRepeat,
            sampling = SkSamplingOptions.Default,
            localMatrix = SkMatrix.Identity,
        )

        // -- alpha8 bitmap -------------------------------------------------
        fAlpha8Bitmap = SkBitmap.allocPixels(SkImageInfo.MakeA8(squareLength, squareLength))
        fAlpha8Bitmap.eraseColor(SK_ColorTRANSPARENT)
        SkCanvas(fAlpha8Bitmap).drawCircle(
            squareLength / 2f, squareLength / 2f, squareLength / 4f,
            SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = false
            },
        )
        fAlpha8BitmapShader = fAlpha8Bitmap.makeShader(
            tileX = SkTileMode.kRepeat, tileY = SkTileMode.kRepeat,
            sampling = SkSamplingOptions.Default,
            localMatrix = SkMatrix.Identity,
        )

        // -- linear gradient (blue → blue@0 along x, length squareLength) --
        // Upstream uses SkColor4f {0,0,1,0} for the second stop —
        // identical to argb(0x00, 0, 0, 0xFF) once quantised to bytes.
        fLinearGradientShader = SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(squareLength.toFloat(), 0f),
            colors = intArrayOf(SK_ColorBLUE, SkColorSetARGB(0, 0, 0, 0xFF)),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )

        initialized = true
    }

    private companion object {
        const val squareLength: Int = 20
    }
}
