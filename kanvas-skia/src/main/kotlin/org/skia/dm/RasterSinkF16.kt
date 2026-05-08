package org.skia.dm

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import org.skia.tests.GM

/**
 * Raster sink rendering at 16 bits per channel (half-float, normalised
 * to `[0, 1]`) into [SkColorType.kRGBA_F16Norm]. Mirrors Skia DM's
 * `--config f16` ; upstream class is
 * `dm/DMSrcSink.cpp::RasterSink(kRGBA_F16_SkColorType, …)`.
 *
 * The F16 sink is the **canonical reference path** for kanvas-skia :
 * the per-pixel composite arithmetic stays in float `[0, 1]` premul
 * space, so multi-pass blending and gradient rendering survive
 * end-to-end without 8-bit quantisation drift. Reference PNGs in
 * `original-888/` are 16-bit-per-channel (Skia DM emits them via
 * `--config f16`), so an F16 render diffs at single-ulp tolerance
 * instead of the worst-case ~150 the 8888 path would show.
 *
 * @param colorSpace the colour space the rendered bitmap is tagged
 *   with. Defaults to the **DM unified Rec.2020 working space** that
 *   matches the embedded ICC profile of the reference images under
 *   `original-888/`. See
 *   [docs/colorspace-fingerprint.md](../../resources/colorspace-fingerprint.md)
 *   for the full ICC dump.
 */
public class RasterSinkF16(
    private val colorSpace: SkColorSpace = DM_REFERENCE_COLOR_SPACE,
) : Sink {

    override val tag: String = TAG

    override fun draw(src: GM): Sink.Result {
        return try {
            val size = src.size()
            val bitmap = SkBitmap(
                size.width, size.height,
                colorSpace,
                SkColorType.kRGBA_F16Norm,
            )
            bitmap.eraseColor(src.bgColor())
            val canvas = SkCanvas(bitmap)
            src.draw(canvas)
            Sink.Result.Ok(bitmap)
        } catch (e: Throwable) {
            Sink.Result.Error("RasterSinkF16[${src.name()}]: ${e.message ?: e::class.simpleName}")
        }
    }

    public companion object {
        /** DM tag matching upstream's `--config f16`. */
        public const val TAG: String = "f16"

        /**
         * Default colour space tagged on F16 renders : matches the
         * `DM unified Rec.2020` profile embedded in the
         * `original-888/` reference PNG images. Kept in the companion
         * so callers / `RasterSinkF16()` with no override land on the
         * right space.
         *
         * Shared with `org.skia.testing.TestUtils.DM_REFERENCE_COLOR_SPACE`
         * (kept as a separate constant there for backward compatibility
         * — the test-side legacy entry will fold into this one in a
         * follow-up slice).
         */
        public val DM_REFERENCE_COLOR_SPACE: SkColorSpace =
            SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
    }
}
