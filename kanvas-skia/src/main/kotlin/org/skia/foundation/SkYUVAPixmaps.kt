package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkYUVAPixmaps`](https://github.com/google/skia/blob/main/include/core/SkYUVAPixmaps.h)
 * — a [SkYUVAInfo] plus the per-plane [SkPixmap] views that back it.
 * Each plane's [SkPixmap] carries its own [SkImageInfo] and row-byte
 * stride, so a single [SkYUVAPixmaps] fully describes a YUV(A) image
 * without owning the pixel memory.
 *
 * **R3 scope.** Only the data structure ships — same caveat as
 * [SkYUVAInfo] : YUV → RGB conversion and a draw path are **R-suivi**
 * (would need a new branch in [SkBitmapDevice]). The class is valid +
 * inspectable but cannot yet be sampled by the raster backend.
 *
 * Validity is the upstream rule "number of provided planes matches
 * [SkYUVAInfo.numPlanes]" — see [isValid].
 */
public class SkYUVAPixmaps(
    public val info: SkYUVAInfo,
    public val planes: Array<SkPixmap>,
) {

    /**
     * Mirrors `bool SkYUVAPixmaps::isValid()`. Returns `true` iff the
     * underlying [info] is valid **and** the number of supplied [planes]
     * matches its [SkYUVAInfo.numPlanes].
     */
    public fun isValid(): Boolean = info.isValid() && info.numPlanes() == planes.size

    /** Mirrors `int SkYUVAPixmaps::numPlanes()`. */
    public fun numPlanes(): Int = planes.size

    /**
     * Mirrors `const SkPixmap& SkYUVAPixmaps::plane(int i)`. Throws if
     * [i] is out of range — upstream's debug assert lifted to a hard
     * check.
     */
    public fun plane(i: Int): SkPixmap {
        require(i in 0 until planes.size) {
            "plane index $i out of range [0, ${planes.size})"
        }
        return planes[i]
    }

    /** Mirrors `const SkYUVAInfo& SkYUVAPixmaps::yuvaInfo()`. */
    public fun yuvaInfo(): SkYUVAInfo = info

    public companion object {
        /** Mirrors `SkYUVAPixmaps::kMaxPlanes`. */
        public const val kMaxPlanes: Int = SkYUVAInfo.kMaxPlanes

        /**
         * Mirrors `SkYUVAPixmaps::FromExternalPixmaps(const SkYUVAInfo&, const SkPixmap[kMaxPlanes])`.
         *
         * Returns an [SkYUVAPixmaps] that wraps [externalPixmaps] without
         * taking ownership. Only the first [SkYUVAInfo.numPlanes] entries
         * of [externalPixmaps] are retained.
         */
        public fun FromExternalPixmaps(info: SkYUVAInfo, externalPixmaps: Array<SkPixmap>): SkYUVAPixmaps {
            val n = info.numPlanes()
            require(externalPixmaps.size >= n) {
                "externalPixmaps.size=${externalPixmaps.size} < info.numPlanes=$n"
            }
            return SkYUVAPixmaps(info, Array(n) { externalPixmaps[it] })
        }
    }
}
