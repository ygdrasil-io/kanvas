package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/subsetshader.cpp::bitmap_subset_shader`
 * (`DEF_SIMPLE_GM_CAN_FAIL(bitmap_subset_shader, …, 256, 256)`).
 *
 * Loads `images/color_wheel.png`, extracts the left and right halves as
 * subset bitmaps, and tiles each one as a [SkBitmapShader] with a
 * `scale(0.75) · rotate(30°)` local matrix. The two shaders fill the top
 * and bottom halves of the canvas respectively, producing a 2×1 split of
 * tiled, rotated, scaled colour-wheel chunks.
 *
 * **Adaptation for `:kanvas-skia`** : we don't yet ship `SkBitmap::extractSubset`,
 * so the left/right halves are materialised by copying pixels from the source
 * bitmap into freshly-allocated [SkBitmap]s via [SkBitmap.getPixel] /
 * [SkBitmap.setPixel]. The shader pipeline then samples them just like
 * upstream would sample a `pixelRef`-backed subset — pixel-for-pixel identical
 * to a true subset because the pixels are bit-copies.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_CAN_FAIL(bitmap_subset_shader, canvas, errorMsg, 256, 256) {
 *     canvas->clear(SK_ColorWHITE);
 *     SkBitmap source;
 *     if (!ToolUtils::GetResourceAsBitmap("images/color_wheel.png", &source)) {
 *         *errorMsg = "Could not load images/color_wheel.png…";
 *         return skiagm::DrawResult::kFail;
 *     }
 *     SkIRect left = SkIRect::MakeWH(source.width()/2, source.height());
 *     SkIRect right = SkIRect::MakeXYWH(source.width()/2, 0,
 *                                       source.width()/2, source.height());
 *     SkBitmap leftBitmap, rightBitmap;
 *     source.extractSubset(&leftBitmap, left);
 *     source.extractSubset(&rightBitmap, right);
 *
 *     SkMatrix matrix;
 *     matrix.setScale(0.75f, 0.75f);
 *     matrix.preRotate(30.0f);
 *     SkTileMode tm = SkTileMode::kRepeat;
 *     SkPaint paint;
 *     paint.setShader(leftBitmap.makeShader(tm, tm, SkSamplingOptions(), matrix));
 *     canvas->drawRect(SkRect::MakeWH(256.0f, 128.0f), paint);
 *     paint.setShader(rightBitmap.makeShader(tm, tm, SkSamplingOptions(), matrix));
 *     canvas->drawRect(SkRect::MakeXYWH(0, 128.0f, 256.0f, 128.0f), paint);
 *     return skiagm::DrawResult::kOk;
 * }
 * ```
 */
public class BitmapSubsetShaderGM : GM() {
    override fun getName(): String = "bitmap_subset_shader"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorWHITE)

        val source = decodeBitmap("images/color_wheel.png") ?: return

        val left = SkIRect.MakeWH(source.width / 2, source.height)
        val right = SkIRect.MakeXYWH(source.width / 2, 0, source.width / 2, source.height)

        val leftBitmap = subset(source, left)
        val rightBitmap = subset(source, right)

        // matrix = Scale(0.75, 0.75) · Rotate(30°) — upstream's
        // `setScale` followed by `preRotate(30)` evaluates to the same
        // composition (`M · R`).
        val matrix = SkMatrix.MakeScale(0.75f, 0.75f).preRotate(30f)
        val tm = SkTileMode.kRepeat

        val paint = SkPaint().apply {
            shader = leftBitmap.makeShader(tm, tm, SkSamplingOptions.Default, matrix)
        }
        c.drawRect(SkRect.MakeWH(256f, 128f), paint)

        paint.shader = rightBitmap.makeShader(tm, tm, SkSamplingOptions.Default, matrix)
        c.drawRect(SkRect.MakeXYWH(0f, 128f, 256f, 128f), paint)
    }

    /**
     * Decode a classpath PNG into a freshly-allocated [SkBitmap]. Mirrors
     * `ToolUtils::GetResourceAsBitmap` minus the in-place pixel resize —
     * we allocate `width × height` directly off the codec's [SkImage].
     */
    private fun decodeBitmap(path: String): SkBitmap? {
        val image = ToolUtils.GetResourceAsImage(path) ?: return null
        val bitmap = SkBitmap(image.width, image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                bitmap.setPixel(x, y, image.peekPixel(x, y))
            }
        }
        return bitmap
    }

    /**
     * Copy-by-pixels port of `SkBitmap::extractSubset`. Allocates a fresh
     * [SkBitmap] sized to [rect]'s width/height and copies the corresponding
     * pixel region from [source]. Upstream zero-copies via `pixelRef` aliasing;
     * for our purposes a bit-exact pixel copy is indistinguishable.
     */
    private fun subset(source: SkBitmap, rect: SkIRect): SkBitmap {
        val w = rect.width()
        val h = rect.height()
        val out = SkBitmap(w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out.setPixel(x, y, source.getPixel(rect.left + x, rect.top + y))
            }
        }
        return out
    }
}
