package org.skia.utils

import org.skia.codec.SkEncodedOrigin
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo

/**
 * Mirrors Skia's
 * [`SkPixmapUtils`](https://github.com/google/skia/blob/main/include/codec/SkPixmapUtils.h)
 * namespace (R1 port).
 *
 * Two helpers :
 *
 *  - [Orient] — copy pixels from `src` into `dst` with an
 *    [SkEncodedOrigin] applied. Used by image decoders to undo the EXIF
 *    Orientation tag so the rendered pixels match the scene's intended
 *    top-left.
 *  - [SwapWidthHeight] — convenience to swap an [SkImageInfo]'s
 *    `width` and `height`. Used when the [SkEncodedOrigin] of a
 *    decoded stream includes a 90° rotation.
 *
 * **R1 implementation note — pixmap type** : kanvas-skia doesn't have a
 * dedicated `SkPixmap` view type yet (a non-owning pointer + info
 * descriptor in upstream). The Orient helper therefore operates on
 * [SkBitmap], which is the closest existing concept (owning pixel buffer
 * + width/height/colourType). Two callers/types end up forwarding through
 * the same code path with no loss of semantics.
 *
 * **R1 origin support** :
 *  - [SkEncodedOrigin.kTopLeft] : identity copy (full coverage).
 *  - [SkEncodedOrigin.kBottomLeft] : vertical flip (reflect across x —
 *    full coverage).
 *  - All other origins : flagged TODO; the call returns `false` and
 *    leaves [dst] untouched. Plumbing the remaining six (180° rotate,
 *    90° rotations, transpose-style reflections) is a follow-up — see
 *    `SkEncodedOriginToMatrix` for the per-origin recipes.
 */
public object SkPixmapUtils {

    /**
     * Copy [src] into [dst], applying the orientation transform [origin].
     *
     * Returns `false` (and leaves [dst] unmodified) when :
     *  - [src] and [dst] don't have compatible dimensions / colour type
     *    for the requested orientation, or
     *  - [origin] is one of the 90°-rotated / transposed origins that
     *    R1 doesn't implement yet.
     *
     * **Caller contract** : [dst] must already be allocated. For an
     * orientation with no width/height swap, `dst.width == src.width` and
     * `dst.height == src.height`. For a swap-style orientation (currently
     * unimplemented in R1) callers will eventually need to pre-allocate a
     * `dst` whose dimensions match [SwapWidthHeight].
     */
    public fun Orient(dst: SkBitmap, src: SkBitmap, origin: SkEncodedOrigin): Boolean {
        if (dst.colorType != src.colorType) return false
        if (origin.swapsWidthHeight()) {
            if (dst.width != src.height || dst.height != src.width) return false
        } else {
            if (dst.width != src.width || dst.height != src.height) return false
        }

        return when (origin) {
            SkEncodedOrigin.kTopLeft -> {
                // Identity copy : pixel (x, y) -> (x, y).
                for (y in 0 until src.height) {
                    for (x in 0 until src.width) {
                        dst.setPixel(x, y, src.getPixel(x, y))
                    }
                }
                true
            }
            SkEncodedOrigin.kBottomLeft -> {
                // Reflected across x : pixel (x, y) -> (x, h-1-y).
                val h = src.height
                for (y in 0 until h) {
                    for (x in 0 until src.width) {
                        dst.setPixel(x, h - 1 - y, src.getPixel(x, y))
                    }
                }
                true
            }
            // TODO(R1 follow-up) implement the remaining six origins :
            //   kTopRight, kBottomRight (in-place reflections)
            //   kLeftTop, kRightTop, kRightBottom, kLeftBottom (90° rotations)
            else -> false
        }
    }

    /**
     * Return a copy of [info] with [SkImageInfo.width] and
     * [SkImageInfo.height] swapped. Mirrors upstream verbatim. Used in
     * combination with [SkEncodedOrigin.swapsWidthHeight] when allocating
     * a destination image for a rotation-style [Orient] call.
     */
    public fun SwapWidthHeight(info: SkImageInfo): SkImageInfo =
        info.makeWH(info.height, info.width)
}
