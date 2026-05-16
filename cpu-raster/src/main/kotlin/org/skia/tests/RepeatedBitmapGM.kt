package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColorSetRGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.min

/**
 * Port of Skia's `gm/repeated_bitmap.cpp::repeated_bitmap`
 * (`DEF_SIMPLE_GM_CAN_FAIL(repeated_bitmap, …, 576, 576)`).
 *
 * 4×4 grid of rotated cells. Each cell paints a grey rounded background
 * rect on top of a global 12-px checkerboard, then draws the scaled
 * `images/randPixels.png` (8×8 native, upscaled to 128×128) on top.
 * Cell `(i, j)` is rotated by `18° * (i + 4·j)`, so every grid step
 * adds 18° — the GM exercises the full 270° rotation range.
 *
 * **Adaptation for `:kanvas-skia`** : the canvas-based `drawImage` path
 * in this raster build drops rotated draws (the device only handles
 * axis-aligned `drawImageRect`s — see [SkCanvas.drawImageRect]). To
 * render the rotated images we lower them into a [SkPaint.shader] with
 * a [SkBitmapShader] localMatrix that captures `translate · rotate ·
 * scale · centring-translate` ; the destination is then a plain
 * axis-aligned [SkCanvas.drawRect] sized to cover the rotated cell.
 * Pixel-for-pixel output should match upstream because the shader
 * pipeline uses the same nearest-neighbour sampling that
 * [SkSamplingOptions.Default] selects in the C++ source.
 *
 * C++ original:
 * ```cpp
 * static skiagm::DrawResult draw_rotated_image(SkCanvas* canvas, const SkImage* image,
 *                                              SkString* errorMsg) {
 *     ToolUtils::draw_checkerboard(canvas, SkColorSetRGB(156, 154, 156), SK_ColorWHITE, 12);
 *     if (!image) { *errorMsg = "No image…"; return skiagm::DrawResult::kFail; }
 *     SkRect rect = SkRect::MakeLTRB(-68.0f, -68.0f, 68.0f, 68.0f);
 *     SkPaint paint;
 *     paint.setColor(SkColorSetRGB(49, 48, 49));
 *     SkScalar scale = std::min(128.0f / image->width(), 128.0f / image->height());
 *     SkScalar point[2] = {-0.5f * image->width(), -0.5f * image->height()};
 *     for (int j = 0; j < 4; ++j) {
 *         for (int i = 0; i < 4; ++i) {
 *             SkAutoCanvasRestore autoCanvasRestore(canvas, true);
 *             canvas->translate(96.0f + 192.0f * i, 96.0f + 192.0f * j);
 *             canvas->rotate(18.0f * (i + 4 * j));
 *             canvas->drawRect(rect, paint);
 *             canvas->scale(scale, scale);
 *             canvas->drawImage(image, point[0], point[1]);
 *         }
 *     }
 *     return skiagm::DrawResult::kOk;
 * }
 *
 * DEF_SIMPLE_GM_CAN_FAIL(repeated_bitmap, canvas, errorMsg, 576, 576) {
 *     return draw_rotated_image(
 *             canvas, ToolUtils::GetResourceAsImage("images/randPixels.png").get(), errorMsg);
 * }
 * ```
 */
public class RepeatedBitmapGM : GM() {
    override fun getName(): String = "repeated_bitmap"
    override fun getISize(): SkISize = SkISize.Make(576, 576)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/randPixels.png") ?: return
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
                // Background grey rect (path rasterizer handles arbitrary rotation).
                canvas.drawRect(rect, paint)
                canvas.scale(scale, scale)
                // `:kanvas-skia` drops rotated `drawImage` ; lower to a shader-on-rect
                // draw so the path rasterizer + bitmap shader handle the rotation.
                // The destination rect in image-local coords is the image bounds
                // offset by `(px, py)` (matches `canvas->drawImage(image, px, py)`).
                val dst = SkRect.MakeXYWH(px, py, image.width.toFloat(), image.height.toFloat())
                // Local matrix maps shader-local coords to image pixel coords.
                // `drawImage(image, px, py)` samples the image at `pixel = dst - (px, py)`.
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
