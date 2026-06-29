package org.skia.tests

import org.graphiks.kanvas.codec.Codec
import org.skia.core.SkCanvas
import org.skia.encode.SkJpegEncoder
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/jpg_color_cube.cpp::ColorCubeGM`.
 *
 * Builds a `512×512` "colour cube" : 64 horizontally-tiled `64×64`
 * patches, each patch holding the cross-section of the RGB cube at
 * a fixed `B` plane (`B = patchIndex * 4`). Within each patch, the
 * `(x, y)` pixel is `(R, G) = (px * 4, py * 4)`. The bitmap is then
 * encoded through [SkJpegEncoder] with default options and
 * decoded back ; the decoded image is what actually gets drawn.
 *
 * The point of the GM is to exercise the JPEG encoder against a
 * dense, smoothly-varying colour gradient — every 4-step in the cube
 * lands somewhere in the JPEG quantizer's domain and the
 * encoder→decoder round-trip exposes any bias / clamping bugs in the
 * chroma subsampler. Pixel similarity stays in the JPEG-comparison
 * band (~80-95 %) — JPEG-100 with 4:2:0 chroma subsampling still
 * loses the lowest-order few bits per channel.
 *
 * C++ original:
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
 *     sk_sp<SkData> data = SkJpegEncoder::Encode(bmp.pixmap(), {});
 *     fImage = SkImages::DeferredFromEncodedData(data);
 * }
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->drawImage(fImage, 0, 0);
 * }
 * ```
 */
public class JpgColorCubeGM : GM() {

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
        val data = SkJpegEncoder.Encode(bmp) ?: return
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
