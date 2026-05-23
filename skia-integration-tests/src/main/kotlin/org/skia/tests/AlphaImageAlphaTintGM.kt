package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions

/**
 * Port of Skia's `gm/alpha_image.cpp::DEF_SIMPLE_GM(alpha_image_alpha_tint, …, 152, 80)`.
 *
 * Created to demonstrate [skbug.com/40041892](https://skbug.com/40041892) — the
 * GPU backend was failing to apply paint alpha to alpha-only image shaders.
 * The two 64 × 64 boxes drawn side-by-side should look the same :
 *
 *  - **Left**  : `drawImage(image, 0, 0, &paint)` with a `SkColor4f(0, 1, 0, 0.5f)`
 *    paint colour. The A8 sample is modulated by the paint's RGB and alpha,
 *    producing a translucent green gradient (alpha rises 0 → 252 vertically).
 *  - **Right** : `paint.setShader(image->makeShader(…))` + `drawRect({0, 0, 64, 64})`
 *    with the same `SkColor4f(0, 1, 0, 0.5f)` paint colour. The image-shader
 *    path samples the A8 image as `(0, 0, 0, a)` and modulates by the paint's
 *    colour and alpha — same visual.
 *
 * The synthetic A8 bitmap is a vertical alpha ramp : every row `y` is filled
 * with `(y * 4) & 0xFF` (so 0 .. 252 across the 64 rows, both bytes set
 * per pixel before [SkBitmap.setImmutable]).
 *
 * Both translates of `(8, 8)` and `(72, 0)` are cumulative (Skia's matrix
 * stack accumulates ; no `save/restore` here) — the two cells end up at
 * `(8, 8)` and `(80, 8)` respectively.
 *
 * **Iso-fidelity caveat** : same `:kanvas-skia` H1.5 gap as
 * [AlphaImageGM] — `drawImage` on an A8 source doesn't yet modulate the
 * sampled alpha by `paint.color`. For the image-shader path (right box)
 * the `SkBitmapShader` machinery does propagate the source alpha but the
 * tint multiplication by `paint.color` is still subject to the same gap,
 * so neither cell renders the upstream translucent-green output exactly.
 * We expect ~30 % similarity vs the reference and let the ratchet floor it.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(alpha_image_alpha_tint, canvas, 152, 80) {
 *   canvas->clear(SK_ColorGRAY);
 *   SkBitmap bm;
 *   bm.allocPixels(SkImageInfo::MakeA8(64, 64));
 *   for (int y = 0; y < bm.height(); ++y) {
 *     for (int x = 0; x < bm.width(); ++x) {
 *       *bm.getAddr8(x, y) = y * 4;
 *     }
 *   }
 *   bm.setImmutable();
 *   auto image = bm.asImage();
 *   SkPaint paint;
 *   paint.setColor4f({ 0, 1, 0, 0.5f });
 *   canvas->translate(8, 8);
 *   canvas->drawImage(image.get(), 0, 0, SkSamplingOptions(), &paint);
 *   canvas->translate(72, 0);
 *   paint.setShader(image->makeShader(SkSamplingOptions()));
 *   canvas->drawRect({ 0, 0, 64, 64 }, paint);
 * }
 * ```
 */
public class AlphaImageAlphaTintGM : GM() {

    override fun getName(): String = "alpha_image_alpha_tint"

    override fun getISize(): SkISize = SkISize.Make(152, 80)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorGRAY)

        val bm = SkBitmap.allocPixels(SkImageInfo.MakeA8(64, 64))
        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                // y * 4 in the range [0, 252] : the byte cast wraps for any
                // hypothetical y > 63 but bm.height is 64, so we're safe.
                bm.pixelsA8[y * bm.width + x] = (y * 4).toByte()
            }
        }
        // kanvas-skia's SkBitmap has no `setImmutable()` hook — snapshots
        // copy the pixel buffer at `asImage()` time, so the upstream
        // immutability guarantee is implicit.
        val image: SkImage = bm.asImage()

        val paint = SkPaint()
        paint.setColor4f(SkColor4f(0f, 1f, 0f, 0.5f))

        c.translate(8f, 8f)
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)

        c.translate(72f, 0f)
        paint.shader = image.makeShader(SkSamplingOptions.Default)
        c.drawRect(SkRect.MakeXYWH(0f, 0f, 64f, 64f), paint)
    }
}
