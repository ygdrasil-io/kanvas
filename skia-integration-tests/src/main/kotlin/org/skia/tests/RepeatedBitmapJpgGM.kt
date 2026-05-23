package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetRGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.min

/**
 * Port of Skia's `gm/repeated_bitmap.cpp::repeated_bitmap_jpg`
 * (`DEF_SIMPLE_GM_CAN_FAIL(repeated_bitmap_jpg, …, 576, 576)`).
 *
 * Identical to [RepeatedBitmapGM] (4×4 grid of rotated cells over a
 * 12-px checkerboard) but uses a JPEG source image (`images/color_wheel.jpg`)
 * instead of the PNG (`images/randPixels.png`).  The GM exercises JPEG
 * decode + repeated rotated blits through the same `draw_rotated_image`
 * helper used by the PNG variant.
 *
 * **Adaptation for `:kanvas-skia`** : identical to [RepeatedBitmapGM] —
 * rotated `drawImage` is lowered to a bitmap-shader-on-rect draw.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_CAN_FAIL(repeated_bitmap_jpg, canvas, errorMsg, 576, 576) {
 *     return draw_rotated_image(
 *             canvas, ToolUtils::GetResourceAsImage("images/color_wheel.jpg").get(), errorMsg);
 * }
 * ```
 */
public class RepeatedBitmapJpgGM : GM() {
    override fun getName(): String = "repeated_bitmap_jpg"
    override fun getISize(): SkISize = SkISize.Make(576, 576)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/color_wheel.jpg") ?: return
        drawRotatedImage(c, image)
    }

    private fun drawRotatedImage(canvas: SkCanvas, image: SkImage) {
        ToolUtils.draw_checkerboard(canvas, SkColorSetRGB(156, 154, 156), SK_ColorWHITE, 12)
        val rect = SkRect.MakeLTRB(-68f, -68f, 68f, 68f)
        val paint = SkPaint().apply { color = SkColorSetRGB(49, 48, 49) }
        val scale = min(128f / image.width, 128f / image.height)
        val px = -0.5f * image.width
        val py = -0.5f * image.height
        for (j in 0 until 4) {
            for (i in 0 until 4) {
                val saveCount = canvas.save()
                canvas.translate(96f + 192f * i, 96f + 192f * j)
                canvas.rotate(18f * (i + 4 * j))
                // Background grey rect.
                canvas.drawRect(rect, paint)
                canvas.scale(scale, scale)
                // Lower rotated drawImage to shader-on-rect (same approach as RepeatedBitmapGM).
                val dst = SkRect.MakeXYWH(px, py, image.width.toFloat(), image.height.toFloat())
                val localMatrix = SkMatrix.MakeTrans(px, py)
                val shaderPaint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kClamp,
                        tileY = SkTileMode.kClamp,
                        sampling = SkSamplingOptions.Default,
                        localMatrix = localMatrix,
                    )
                }
                canvas.drawRect(dst, shaderPaint)
                canvas.restoreToCount(saveCount)
            }
        }
    }
}
