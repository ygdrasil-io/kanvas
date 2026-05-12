package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/tilemodes_alpha.cpp::tilemodes_alpha`
 * (`DEF_SIMPLE_GM(tilemodes_alpha, canvas, 512, 512)`).
 *
 * Regression for [crbug.com/957275](https://crbug.com/957275) :
 * `SkImage::makeShader` with a half-alpha paint must propagate the
 * paint alpha through the shader sampling. The GM draws a 4×4 grid
 * of 126×126 rects, each filled by a translated image shader cycling
 * through `{kClamp, kRepeat, kMirror, kDecal}` on the X and Y axes.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(tilemodes_alpha, canvas, 512, 512) {
 *     sk_sp<SkImage> image = ToolUtils::GetResourceAsImage("images/mandrill_64.png");
 *     if (!image) { return; }
 *     constexpr SkTileMode kModes[4] = {
 *         SkTileMode::kClamp,
 *         SkTileMode::kRepeat,
 *         SkTileMode::kMirror,
 *         SkTileMode::kDecal,
 *     };
 *     for (int y = 0; y < 4; ++y) {
 *         for (int x = 0; x < 4; ++x) {
 *             SkRect rect = SkRect::MakeXYWH(128 * x + 1, 128 * y + 1, 126, 126);
 *             SkMatrix matrix = SkMatrix::Translate(rect.x(), rect.y());
 *             SkPaint paint(SkColor4f{0, 0, 0, 0.5f});
 *             paint.setShader(image->makeShader(kModes[x], kModes[y], SkSamplingOptions(), &matrix));
 *             canvas->drawRect(rect, paint);
 *         }
 *     }
 * }
 * ```
 */
public class TilemodesAlphaGM : GM() {
    override fun getName(): String = "tilemodes_alpha"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/mandrill_64.png") ?: return

        val modes = arrayOf(
            SkTileMode.kClamp,
            SkTileMode.kRepeat,
            SkTileMode.kMirror,
            SkTileMode.kDecal,
        )

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val rect = SkRect.MakeXYWH(128f * x + 1f, 128f * y + 1f, 126f, 126f)
                val matrix = SkMatrix.MakeTrans(rect.left, rect.top)
                val paint = SkPaint(SkColor4f(0f, 0f, 0f, 0.5f)).apply {
                    shader = image.makeShader(
                        tileX = modes[x],
                        tileY = modes[y],
                        sampling = SkSamplingOptions(),
                        localMatrix = matrix,
                    )
                }
                c.drawRect(rect, paint)
            }
        }
    }
}
