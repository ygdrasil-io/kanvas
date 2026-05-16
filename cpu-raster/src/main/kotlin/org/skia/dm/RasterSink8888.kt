package org.skia.dm

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.tests.GM

/**
 * Raster sink rendering at 8 bits per channel into [SkColorType.kRGBA_8888].
 * Mirrors Skia DM's `--config 8888` ; the upstream class is
 * `dm/DMSrcSink.cpp::RasterSink` instantiated with `kN32_SkColorType`.
 *
 * Allocates a fresh [SkBitmap] sized to the GM's preferred [GM.size],
 * tagged with [colorSpace] (defaults to sRGB), erases it to the GM's
 * background colour, and runs `gm.draw(canvas)` against it. The result
 * is the bitmap.
 *
 * The 8888 sink is the legacy reference path : every GM port up to
 * Phase 5b was originally validated against an 8888 render, before the
 * F16 working space came online in Phase 6. Keeping the 8888 sink
 * ensures we can still produce DM-canonical 8-bit references on
 * demand — e.g. for diffing against upstream PNG outputs that were
 * captured with `--config 8888`.
 *
 * @param colorSpace the colour space the rendered bitmap is tagged
 *   with. Defaults to sRGB (matches upstream's default RasterSink).
 */
public class RasterSink8888(
    private val colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
) : Sink {

    override val tag: String = TAG

    override fun draw(src: GM): Sink.Result {
        return try {
            val size = src.size()
            val bitmap = SkBitmap(
                size.width, size.height,
                colorSpace,
                SkColorType.kRGBA_8888,
            )
            bitmap.eraseColor(src.bgColor())
            val canvas = SkCanvas(bitmap)
            src.draw(canvas)
            Sink.Result.Ok(bitmap)
        } catch (e: Throwable) {
            Sink.Result.Error("RasterSink8888[${src.name()}]: ${e.message ?: e::class.simpleName}")
        }
    }

    public companion object {
        /** DM tag matching upstream's `--config 8888`. */
        public const val TAG: String = "8888"
    }
}
