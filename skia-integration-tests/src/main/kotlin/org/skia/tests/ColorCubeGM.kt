package org.skia.tests

import org.graphiks.kanvas.codec.Codec
import org.skia.core.SkCanvas
import org.graphiks.kanvas.codec.jpeg.JpegEncoder
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.graphiks.math.SkISize

/**
 * Port of Skia's
 * [`gm/jpg_color_cube.cpp::ColorCubeGM`](https://github.com/google/skia/blob/main/gm/jpg_color_cube.cpp)
 * (registered upstream as `"jpg-color-cube"`).
 *
 * Builds a `512×512` 8×8 grid of `64×64` patches walking the full
 * `R × G × B` cube on a 4-step grid : for each patch index
 * `b ∈ [0, 64)` the patch holds `(R, G) = (px * 4, py * 4)` and a
 * constant `B = b * 4`. The bitmap is JPEG-encoded with default
 * [JpegEncoder] options, decoded back through [Codec], and the
 * decoded image is what actually gets drawn.
 *
 * Purpose : exercise the JPEG encoder→decoder round-trip on a dense,
 * smoothly-varying colour gradient. Every 4-step in the cube lands
 * somewhere in the JPEG quantizer's domain and the chroma-subsampling
 * stage hits the high-saturation corners hardest. Similarity stays in
 * the JPEG-comparison band (~80-95 %) — JPEG-100 with 4:2:0 chroma
 * still loses the lowest-order few bits per channel.
 *
 * A sibling port `JpgColorCubeGM` covers the same upstream file ; this
 * class exists because the upstream `.cpp` defines `ColorCubeGM` (the
 * filename `jpg_color_cube.cpp` is the file *path*, the class name is
 * `ColorCubeGM`). Both are kept registered so the cross-backend ratchet
 * sees both shapes — the per-class similarity score tracks the same
 * underlying round-trip through two separate harness entries.
 *
 * C++ original (`onOnceBeforeDraw` + `onDraw`):
 * ```cpp
 * void onOnceBeforeDraw() override {
 *     SkBitmap bmp;
 *     bmp.allocN32Pixels(512, 512, true);
 *     int bX = 0, bY = 0;
 *     for (int b = 0; b < 64; ++b) {
 *         for (int r = 0; r < 64; ++r) {
 *             for (int g = 0; g < 64; ++g) {
 *                 *bmp.getAddr32(bX + r, bY + g) =
 *                     SkPackARGB32(255, SkTPin(r * 4, 0, 255),
 *                                       SkTPin(g * 4, 0, 255),
 *                                       SkTPin(b * 4, 0, 255));
 *             }
 *         }
 *         bX += 64;
 *         if (bX >= 512) { bX = 0; bY += 64; }
 *     }
 *     sk_sp<SkData> data = JpegEncoder::Encode(bmp.pixmap(), {});
 *     SkASSERT_RELEASE(data);
 *     fImage = SkImages::DeferredFromEncodedData(data);
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->drawImage(fImage, 0, 0);
 * }
 * ```
 */
public class ColorCubeGM : GM() {

    private var fImage: SkImage? = null

    override fun getName(): String = "jpg-color-cube"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onOnceBeforeDraw() {
        val bmp = SkBitmap(512, 512)
        var bX = 0
        var bY = 0
        for (b in 0 until 64) {
            for (r in 0 until 64) {
                for (g in 0 until 64) {
                    bmp.setPixel(
                        bX + r, bY + g,
                        SkColorSetARGB(
                            255,
                            (r * 4).coerceIn(0, 255),
                            (g * 4).coerceIn(0, 255),
                            (b * 4).coerceIn(0, 255),
                        ),
                    )
                }
            }
            bX += 64
            if (bX >= 512) {
                bX = 0
                bY += 64
            }
        }
        val data = JpegEncoder.encode(bmp) ?: return
        val codec = Codec.MakeFromData(data) ?: return
        val (decoded, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || decoded == null) return
        fImage = decoded.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = fImage ?: return
        c.drawImage(img, 0f, 0f)
    }
}
