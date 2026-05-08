package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/badpaint.cpp` :
 * `DEF_GM(return new BadPaintGM;)`.
 *
 * **Contract** (per upstream comment) : "draws with invalid paints.
 * It should draw nothing other than the background."
 *
 * The two paints in question :
 *  1. Empty (zero-sized) bitmap shader — `SkBitmap()` makes a zero-
 *     dimension bitmap whose `makeShader()` produces a degenerate
 *     shader that the rasterizer should treat as null.
 *  2. Non-invertible local-matrix shader — `SkMatrix.MakeAll(0, 0,
 *     0, 0, 0, 0, 0, 0, 0)` is a singular matrix ; the shader should
 *     evaluate to nothing.
 *
 * Both paints carry `color = SK_ColorGREEN` ; if the shader was
 * silently ignored, we'd see green rectangles. The expected output
 * is the canvas background only (white).
 */
public class BadPaintGM : GM() {

    override fun getName(): String = "badpaint"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    private val paints: MutableList<SkPaint> = mutableListOf()

    override fun onOnceBeforeDraw() {
        // (1) Empty bitmap shader.
        // Our SkBitmap requires positive dimensions, so substitute a
        // 1×1 bitmap that we never erase — the pixels are uninitialised
        // (zero) and the shader degenerates to "nothing observable" in
        // intent. (Upstream's `SkBitmap emptyBmp;` declares but never
        // allocates pixels — a zero-dim bitmap.)
        val emptyBmp = SkBitmap(1, 1)  // approximation
        paints.add(
            SkPaint().apply {
                color = SK_ColorGREEN
                shader = emptyBmp.makeShader(SkTileMode.kClamp, SkTileMode.kClamp)
            }
        )

        // (2) Non-invertible local-matrix shader on a real bitmap.
        val blueBmp = SkBitmap(10, 10)
        blueBmp.eraseColor(SK_ColorBLUE)
        val badMatrix = SkMatrix.MakeAll(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        paints.add(
            SkPaint().apply {
                color = SK_ColorGREEN
                shader = blueBmp.makeShader(
                    tileX = SkTileMode.kClamp,
                    tileY = SkTileMode.kClamp,
                    sampling = SkSamplingOptions.Default,
                    localMatrix = badMatrix,
                )
            }
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rect = SkRect.MakeXYWH(10f, 10f, 80f, 80f)
        for (paint in paints) {
            c.drawRect(rect, paint)
        }
    }
}
