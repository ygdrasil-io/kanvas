package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.encode.SkWebpEncoder
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * R-final.S — **STUB.WEBP_LOSSY** consumer GM. Lossy variant of the
 * `encode_alpha_jpeg` family (see [EncodeAlphaJpegGM] for the JPEG
 * sibling). Exercises [SkWebpEncoder.Encode] with
 * [SkWebpEncoder.Compression.kLossy] — which throws
 * `STUB.WEBP_LOSSY` until a libwebp JNI binding is registered via
 * [SkWebpEncoder.Custom].
 *
 * The GM body is a one-liner that touches the stubbed code path so
 * the surface stays compile-pinned ; the [EncodeAlphaJpegLossyWebpTest]
 * is `@Disabled` for the same reason.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.WEBP_LOSSY.
 */
public class EncodeAlphaJpegLossyWebpGM : GM() {

    override fun getName(): String = "encode-alpha-jpeg-lossy-webp"
    override fun getISize(): SkISize = SkISize.Make(400, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val src = SkBitmap(8, 8)
        // Touch the stubbed dispatch — throws STUB.WEBP_LOSSY at runtime.
        // [SkWebpEncoder.requireLossy] is the sharp-edge entry-point that
        // throws on the missing path ; the soft-failure [Encode] would
        // just return `null` and skip the GM silently.
        SkWebpEncoder.requireLossy(
            src,
            SkWebpEncoder.Options(
                compression = SkWebpEncoder.Compression.kLossy,
                quality = 80f,
            ),
        )
    }
}
