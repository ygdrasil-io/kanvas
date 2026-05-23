package org.skia.utils

import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo

/**
 * Mirrors Skia's
 * [`SkPixmapUtils`](https://github.com/google/skia/blob/main/include/codec/SkPixmapUtils.h)
 * namespace (R1 port, R-suivi.9 extension).
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
 * **Origin support (R-suivi.9 complete — all 8 EXIF origins)** :
 *  - [SkEncodedOrigin.kTopLeft]     : identity copy.
 *  - [SkEncodedOrigin.kTopRight]    : horizontal flip (reflect across y).
 *  - [SkEncodedOrigin.kBottomRight] : 180° rotation.
 *  - [SkEncodedOrigin.kBottomLeft]  : vertical flip (reflect across x).
 *  - [SkEncodedOrigin.kLeftTop]     : transpose.
 *  - [SkEncodedOrigin.kRightTop]    : 90° CW rotation.
 *  - [SkEncodedOrigin.kRightBottom] : anti-transpose (transpose + 180°).
 *  - [SkEncodedOrigin.kLeftBottom]  : 90° CCW rotation.
 *
 * The four origins that include a 90° rotation
 * ([SkEncodedOrigin.swapsWidthHeight] true) require `dst` to be
 * pre-allocated with `width = src.height` and `height = src.width`.
 *
 * The discrete pixel formulas mirror upstream's
 * `SkEncodedOriginToMatrix` (see [SkEncodedOrigin.toMatrix]) applied to
 * the centre of each source pixel : `(sx, sy) → (sx + 0.5, sy + 0.5) →
 * apply matrix → floor`. For the integer translation components used
 * by these eight origins, this collapses to the closed-form `(sw-1-sx)`
 * / `(sh-1-sy)` reflection terms used below.
 */
public object SkPixmapUtils {

    /**
     * Copy [src] into [dst], applying the orientation transform [origin].
     *
     * Returns `false` (and leaves [dst] unmodified) when :
     *  - [src] and [dst] don't have compatible [SkBitmap.colorType], or
     *  - [src] and [dst] don't have compatible dimensions for the
     *    requested orientation. Specifically :
     *      * For an orientation **without** width/height swap, `dst`
     *        must satisfy `dst.width == src.width` and `dst.height ==
     *        src.height`.
     *      * For a swap-style orientation (90° rotation / transpose,
     *        see [SkEncodedOrigin.swapsWidthHeight]), `dst` must
     *        satisfy `dst.width == src.height` and `dst.height ==
     *        src.width` — i.e. the dimensions produced by
     *        [SwapWidthHeight].
     */
    public fun Orient(dst: SkBitmap, src: SkBitmap, origin: SkEncodedOrigin): Boolean {
        if (dst.colorType != src.colorType) return false
        if (origin.swapsWidthHeight()) {
            if (dst.width != src.height || dst.height != src.width) return false
        } else {
            if (dst.width != src.width || dst.height != src.height) return false
        }

        val sw = src.width
        val sh = src.height

        when (origin) {
            SkEncodedOrigin.kTopLeft -> {
                // Identity copy : (sx, sy) -> (sx, sy).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sx, sy, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kTopRight -> {
                // Horizontal flip : (sx, sy) -> (sw-1-sx, sy).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sw - 1 - sx, sy, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kBottomRight -> {
                // 180° rotation : (sx, sy) -> (sw-1-sx, sh-1-sy).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sw - 1 - sx, sh - 1 - sy, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kBottomLeft -> {
                // Vertical flip : (sx, sy) -> (sx, sh-1-sy).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sx, sh - 1 - sy, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kLeftTop -> {
                // Transpose : (sx, sy) -> (sy, sx). dst is (sh, sw).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sy, sx, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kRightTop -> {
                // 90° CW rotation : (sx, sy) -> (sh-1-sy, sx). dst is (sh, sw).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sh - 1 - sy, sx, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kRightBottom -> {
                // Anti-transpose (transpose + 180°) : (sx, sy) -> (sh-1-sy, sw-1-sx). dst is (sh, sw).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sh - 1 - sy, sw - 1 - sx, src.getPixel(sx, sy))
                    }
                }
            }
            SkEncodedOrigin.kLeftBottom -> {
                // 90° CCW rotation : (sx, sy) -> (sy, sw-1-sx). dst is (sh, sw).
                for (sy in 0 until sh) {
                    for (sx in 0 until sw) {
                        dst.setPixel(sy, sw - 1 - sx, src.getPixel(sx, sy))
                    }
                }
            }
        }
        return true
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
