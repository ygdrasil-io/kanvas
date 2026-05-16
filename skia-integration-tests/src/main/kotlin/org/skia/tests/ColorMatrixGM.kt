package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/colormatrix.cpp::ColorMatrixGM` (500 × 160).
 *
 * Phase 7a validation GM — exercises the full [SkColorFilters.Matrix]
 * pipeline through `drawImage` (the per-pixel `paint.colorFilter`
 * application path). 2 source bitmaps × 6 matrix variants = 12 cells :
 *
 *  - Row 0 (y = 0): solid 64 × 64 image with linearly-varying RG
 *    (R = x/64 × 255, G = y/64 × 255).
 *  - Row 1 (y = 80): linear-gradient transparent image (alpha 0 → 1
 *    diagonal).
 *
 * Columns (matrices applied via `paint.colorFilter`) :
 *
 *  | x   | matrix              | semantic                             |
 *  | --- | ------------------- | ------------------------------------ |
 *  | 0   | identity            | passes through unchanged             |
 *  | 80  | saturation 0        | greyscale via Rec.709 luma weights   |
 *  | 160 | saturation 0.5      | half-desaturated                     |
 *  | 240 | saturation 1        | identity (re-saturated)              |
 *  | 320 | saturation 2        | over-saturated                       |
 *  | 400 | "red → alpha"       | RGB = white, alpha = original red    |
 *
 * The bg colour is `0xFF808080` (mid-grey) so the alpha-modulated
 * cells visually disambiguate the result.
 */
public class ColorMatrixGM : GM() {
    init { setBGColor(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80)) }

    private lateinit var fSolidImg: SkImage
    private lateinit var fTransparentImg: SkImage

    override fun getName(): String = "colormatrix"
    override fun getISize(): SkISize = SkISize.Make(500, 160)

    override fun onOnceBeforeDraw() {
        fSolidImg = createSolidBitmap(64, 64).asImage()
        fTransparentImg = createTransparentBitmap(64, 64).asImage()
    }

    /**
     * 64 × 64 RGBA image whose RG channels vary linearly with `(x, y)`,
     * blue is 0, alpha is opaque. Mirrors Skia's `CreateSolidBitmap`.
     */
    private fun createSolidBitmap(width: Int, height: Int): SkBitmap {
        val bm = SkBitmap(width, height).also { it.eraseColor(0) }
        val canvas = SkCanvas(bm)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val paint = SkPaint(SkColorSetARGB(0xFF, x * 255 / width, y * 255 / height, 0))
                canvas.drawRect(SkRect.MakeXYWH(x.toFloat(), y.toFloat(), 1f, 1f), paint)
            }
        }
        return bm
    }

    /**
     * 64 × 64 image filled with a black-to-white linear gradient that
     * also sweeps the alpha channel from 0 to 1 (premul black at top-
     * left, opaque white at bottom-right). Mirrors Skia's
     * `CreateTransparentBitmap`.
     */
    private fun createTransparentBitmap(width: Int, height: Int): SkBitmap {
        val bm = SkBitmap(width, height).also { it.eraseColor(0) }
        val canvas = SkCanvas(bm)
        val gradient = SkLinearGradient.Make(
            SkPoint.Make(0f, 0f),
            SkPoint.Make(width.toFloat(), height.toFloat()),
            colors = intArrayOf(0x00000000, 0xFFFFFFFF.toInt()),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply { shader = gradient }
        canvas.drawRect(SkRect.MakeWH(width.toFloat(), height.toFloat()), paint)
        return bm
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sampling = SkSamplingOptions.Default
        val bmps = arrayOf(fSolidImg, fTransparentImg)

        for (bmp in bmps) {
            // x = 0 — identity matrix (passes pixels through unchanged
            //         except that paint.blendMode = kSrc *replaces* the
            //         existing dst, so the bg goes away in this cell).
            drawWithMatrix(c, bmp, 0f, identityMatrix(), sampling)

            // Saturation series.
            drawWithMatrix(c, bmp, 80f,  saturationMatrix(0.0f), sampling)
            drawWithMatrix(c, bmp, 160f, saturationMatrix(0.5f), sampling)
            drawWithMatrix(c, bmp, 240f, saturationMatrix(1.0f), sampling)
            drawWithMatrix(c, bmp, 320f, saturationMatrix(2.0f), sampling)

            // Move red → alpha, force RGB to white.
            drawWithMatrix(c, bmp, 400f, redToAlphaWhiteMatrix(), sampling)

            c.translate(0f, 80f)
        }
    }

    private fun drawWithMatrix(
        canvas: SkCanvas,
        image: SkImage,
        x: Float,
        matrix: FloatArray,
        sampling: SkSamplingOptions,
    ) {
        val paint = SkPaint().apply {
            blendMode = SkBlendMode.kSrc
            colorFilter = SkColorFilters.Matrix(matrix)
        }
        canvas.drawImage(image, x, 0f, sampling, paint)
    }

    public companion object {
        /** 4×5 identity colour matrix. */
        public fun identityMatrix(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        /**
         * Mirrors Skia's
         * [`SkColorMatrix::setSaturation`](https://github.com/google/skia/blob/main/src/core/SkColorMatrix.cpp).
         *
         * `s = 0` → fully desaturated (greyscale via Rec.709 luma weights).
         * `s = 1` → identity.
         * `s > 1` → over-saturated.
         *
         * Constructed as `(1-s) * grayMatrix + s * identity`, where
         * `grayMatrix` projects every channel onto the luma axis with
         * weights `(R = 0.213, G = 0.715, B = 0.072)`.
         */
        public fun saturationMatrix(s: Float): FloatArray {
            val r = 0.213f * (1f - s)
            val g = 0.715f * (1f - s)
            val b = 0.072f * (1f - s)
            return floatArrayOf(
                s + r, g,     b,     0f, 0f,
                r,     s + g, b,     0f, 0f,
                r,     g,     s + b, 0f, 0f,
                0f,    0f,    0f,    1f, 0f,
            )
        }

        /**
         * "Red → alpha, RGB = white" matrix from upstream :
         *
         * ```
         *   R'    0  0  0  0  1     R
         *   G' =  0  0  0  0  1  *  G
         *   B'    0  0  0  0  1     B
         *   A'    1  0  0  0  0     A
         *                            1
         * ```
         */
        public fun redToAlphaWhiteMatrix(): FloatArray = floatArrayOf(
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f,
        )
    }
}
