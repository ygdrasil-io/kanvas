package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imageblurtiled.cpp::ImageBlurTiledGM(3.0f, 3.0f)`.
 *
 * Tiles the 640×480 canvas into 128×128 cells. Each cell `clipRect`s
 * itself, opens a `saveLayer` whose paint carries an [SkImageFilters.Blur]
 * (σ = 3) image filter, and draws four lines of size-100 text inside.
 * Output: 5×4 grid of independently blurred text fragments, the cell
 * clip producing hard-edged boundaries that the blur cannot cross.
 *
 * C++ original:
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setImageFilter(SkImageFilters::Blur(fSigmaX, fSigmaY, nullptr));
 *     const SkScalar tileSize = SkIntToScalar(128);
 *     SkRect bounds = canvas->getLocalClipBounds();
 *     for (SkScalar y = bounds.top(); y < bounds.bottom(); y += tileSize) {
 *         for (SkScalar x = bounds.left(); x < bounds.right(); x += tileSize) {
 *             canvas->save();
 *             canvas->clipRect(SkRect::MakeXYWH(x, y, tileSize, tileSize));
 *             canvas->saveLayer(nullptr, &paint);
 *             const char* str[] = { "The quick", "brown fox", "jumped over", "the lazy dog." };
 *             SkFont font(ToolUtils::DefaultPortableTypeface(), 100);
 *             int posY = 0;
 *             for (unsigned i = 0; i < std::size(str); i++) {
 *                 posY += 100;
 *                 canvas->drawString(str[i], 0, SkIntToScalar(posY), font, SkPaint());
 *             }
 *             canvas->restore();
 *             canvas->restore();
 *         }
 *     }
 * }
 * ```
 */
public class ImageBlurTiledGM(
    private val sigmaX: Float = 3f,
    private val sigmaY: Float = 3f,
) : GM() {
    override fun getName(): String = "imageblurtiled"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.Blur(sigmaX, sigmaY, null)
        }
        val tileSize = 128f
        val bounds: SkRect = c.getLocalClipBounds()
        val strs = arrayOf("The quick", "brown fox", "jumped over", "the lazy dog.")
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 100f)

        var y = bounds.top
        while (y < bounds.bottom) {
            var x = bounds.left
            while (x < bounds.right) {
                c.save()
                c.clipRect(SkRect.MakeXYWH(x, y, tileSize, tileSize))
                c.saveLayer(null, paint)
                var posY = 0
                for (s in strs) {
                    posY += 100
                    c.drawString(s, 0f, posY.toFloat(), font, SkPaint())
                }
                c.restore()
                c.restore()
                x += tileSize
            }
            y += tileSize
        }
    }
}
