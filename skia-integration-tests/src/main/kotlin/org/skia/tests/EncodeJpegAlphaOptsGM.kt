package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/encode_alpha_jpeg.cpp::EncodeJpegAlphaOptsGM`
 * (400 × 200).
 *
 * Exercises `SkJpegEncoder::Options::fAlphaOption` (`Ignore` vs
 * `BlendOnBlack`) — the JPEG encoder doesn't support an alpha
 * channel natively, so the encoder either drops alpha entirely or
 * blends the source onto a black background before encoding.
 *
 * **API gap** : the `:kanvas-skia` JPEG encoder exposes the basic
 * quality / progressive options but not the `AlphaOption` enum
 * (deferred — R-suivi). Stub keeps the class registered ; the
 * associated test is `@Ignore`'d.
 */
public class EncodeJpegAlphaOptsGM : GM() {
    override fun getName(): String = "encodeJpegAlphaOpts"
    override fun getISize(): SkISize = SkISize.Make(400, 200)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once SkJpegEncoder.Options.AlphaOption
        //   (Ignore / BlendOnBlack) is surfaced.
    }
}
