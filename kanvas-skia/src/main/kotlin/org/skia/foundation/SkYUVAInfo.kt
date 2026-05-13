package org.skia.foundation

import org.skia.codec.SkEncodedOrigin
import org.skia.math.SkISize

/**
 * Mirrors Skia's
 * [`SkYUVAInfo`](https://github.com/google/skia/blob/main/include/core/SkYUVAInfo.h)
 * — the metadata-only description of how a multi-plane YUV (with
 * optional alpha) image is laid out. Actual pixel storage lives in a
 * companion [SkYUVAPixmaps].
 *
 * The class describes :
 *  - the displayed [dimensions] of the full-resolution image ;
 *  - the [planeConfig] — how Y, U, V (and optionally A) are spread
 *    across one to four planes ;
 *  - the [subsampling] — the U/V resolution as a fraction of Y ;
 *  - the [yuvColorSpace] and [origin] EXIF-style orientation ;
 *  - the chroma [sitingX] / [sitingY] (centred vs top-left).
 *
 * **R3 scope.** Only the data structure ships ; YUV → RGB conversion +
 * draw is **R-suivi** — it would require a new draw path in
 * [SkBitmapDevice]. The class is therefore safe to construct, mutate
 * (via copy), and query (numPlanes / planeDimensions / computeTotalBytes)
 * but cannot yet be sampled by the raster backend.
 */
public data class SkYUVAInfo(
    public val dimensions: SkISize,
    public val planeConfig: PlaneConfig,
    public val subsampling: Subsampling,
    public val yuvColorSpace: YUVColorSpace = YUVColorSpace.kRec709_Limited_YUV_ColorSpace,
    public val origin: SkEncodedOrigin = SkEncodedOrigin.kTopLeft,
    public val sitingX: Siting = Siting.kCentered,
    public val sitingY: Siting = Siting.kCentered,
) {

    /**
     * Mirrors `SkYUVAInfo::PlaneConfig`. Specifies how Y, U, V (and
     * optionally A) are divided among planes. Planes are separated by
     * underscores in the enum value names ; channels within a plane are
     * ordered as the name suggests (e.g. `kY_UV` puts Y in plane 0 and
     * (U, V) packed into plane 1, channels 0 and 1 respectively).
     */
    public enum class PlaneConfig {
        kUnknown,
        kY_U_V,
        kY_V_U,
        kY_UV,
        kY_VU,
        kYUV,
        kUYV,
        kY_U_V_A,
        kY_V_U_A,
        kY_UV_A,
        kY_VU_A,
        kYUVA,
        kUYVA,
    }

    /**
     * Mirrors `SkYUVAInfo::Subsampling`. The J:a:b chroma-subsampling
     * naming. `k444` means no subsampling ; `k420` means 1 set of UV per
     * 2×2 block of Y (the most common configuration used by JPEG +
     * H.264). Subsamplings other than `k444` are only valid for
     * [PlaneConfig] values that keep U/V in a separate plane from Y.
     */
    public enum class Subsampling {
        kUnknown,
        k444,
        k422,
        k420,
        k440,
        k411,
        k410,
    }

    /**
     * Mirrors `SkYUVAInfo::YUVColorSpace`. The colour-space matrix used
     * to convert YUV → RGB (Rec.601 / Rec.709 / BT.2020 limited or full
     * range, plus the JPEG-full and identity passthrough variants).
     */
    public enum class YUVColorSpace {
        kJPEG_Full_YUV_ColorSpace,
        kRec601_Limited_YUV_ColorSpace,
        kRec709_Full_YUV_ColorSpace,
        kRec709_Limited_YUV_ColorSpace,
        kBT2020_8bit_Full_YUV_ColorSpace,
        kBT2020_8bit_Limited_YUV_ColorSpace,
        kIdentity_YUV_ColorSpace,
    }

    /**
     * Mirrors `SkYUVAInfo::Siting`. Describes where subsampled chroma
     * values sit relative to luma — centred in the luma block (the
     * default, MPEG-2/4 / H.264) or aligned to the top-left luma sample
     * (MPEG-1 / JPEG).
     */
    public enum class Siting { kCentered, kTopLeft }

    /** Width of the displayed full-resolution image. */
    public fun width(): Int = dimensions.width

    /** Height of the displayed full-resolution image. */
    public fun height(): Int = dimensions.height

    /**
     * Mirrors `bool SkYUVAInfo::isValid()`. Validity follows the upstream
     * "must have a non-`kUnknown` plane config" rule.
     */
    public fun isValid(): Boolean = planeConfig != PlaneConfig.kUnknown

    /** Mirrors `bool SkYUVAInfo::hasAlpha()`. */
    public fun hasAlpha(): Boolean = HasAlpha(planeConfig)

    /**
     * Mirrors `int SkYUVAInfo::numPlanes()`. Derived directly from
     * [planeConfig] — see [NumPlanes].
     */
    public fun numPlanes(): Int = NumPlanes(planeConfig)

    /**
     * Number of Y/U/V/A channels packed into plane [i] for this info's
     * [planeConfig]. Mirrors `SkYUVAInfo::numChannelsInPlane`.
     */
    public fun numChannelsInPlane(i: Int): Int = NumChannelsInPlane(planeConfig, i)

    /**
     * Mirrors `SkYUVAInfo::planeSubsamplingFactors`. Returns the
     * `(xFactor, yFactor)` ratio of Y-resolution to plane-[i] resolution.
     * `(1, 1)` for the Y/A plane(s) and the corresponding subsampling
     * factor for U/V planes. `(0, 0)` for an invalid `planeIdx`.
     */
    public fun planeSubsamplingFactors(planeIdx: Int): Pair<Int, Int> =
        PlaneSubsamplingFactors(planeConfig, subsampling, planeIdx)

    /**
     * Mirrors `int SkYUVAInfo::planeDimensions(SkISize planeDimensions[kMaxPlanes])`.
     *
     * Returns the dimensions of the [planeIndex]-th plane in storage
     * order (before [origin] is applied). For Y/A planes this matches
     * the full image dimensions ; for U/V it is reduced by the
     * [subsampling] factor along each axis.
     *
     * Throws if [planeIndex] is out of range — mirrors upstream's debug
     * assert lifted to a hard check.
     */
    public fun planeDimensions(planeIndex: Int): SkISize {
        require(planeIndex in 0 until numPlanes()) {
            "planeIndex=$planeIndex outside [0, ${numPlanes()}) for planeConfig=$planeConfig"
        }
        val (fx, fy) = planeSubsamplingFactors(planeIndex)
        if (fx <= 0 || fy <= 0) return SkISize.Make(0, 0)
        // Up-rounded integer division, matching upstream's
        // (dim + factor - 1) / factor formula.
        // Plane dimensions are reported in storage order — the [origin]
        // transform is applied on top by the consumer.
        val (storedW, storedH) = if (origin.swapsWidthHeight()) {
            dimensions.height to dimensions.width
        } else {
            dimensions.width to dimensions.height
        }
        val w = (storedW + fx - 1) / fx
        val h = (storedH + fy - 1) / fy
        return SkISize.Make(w, h)
    }

    /**
     * Mirrors `size_t SkYUVAInfo::computeTotalBytes(const size_t rowBytes[kMaxPlanes], …)`.
     *
     * For each plane, `(planeHeight - 1) * rowBytes + planeWidth * bpp`
     * — but kanvas-skia's lightweight API doesn't carry a per-plane
     * `bytesPerPixel` (that lives on [SkYUVAPixmaps]) so this overload
     * approximates with `planeHeight * rowBytes[i]`. Sufficient as a
     * conservative upper bound for allocation sizing ; an exact computation
     * lives on [SkYUVAPixmaps.computeTotalBytes] when pixmaps + color
     * types are known.
     *
     * Returns `Long.MAX_VALUE` on overflow.
     */
    public fun computeTotalBytes(rowBytes: IntArray): Long {
        val n = numPlanes()
        require(rowBytes.size >= n) {
            "rowBytes.size=${rowBytes.size} < numPlanes=$n"
        }
        var total = 0L
        for (i in 0 until n) {
            val dims = planeDimensions(i)
            val rb = rowBytes[i].toLong()
            val planeBytes = dims.height.toLong() * rb
            // Saturating add.
            if (planeBytes < 0 || total > Long.MAX_VALUE - planeBytes) return Long.MAX_VALUE
            total += planeBytes
        }
        return total
    }

    /**
     * Mirrors `SkYUVAInfo SkYUVAInfo::makeSubsampling(Subsampling)`. The
     * returned info is invalid if [s] != k444 but the plane config keeps
     * Y in the same plane as UV.
     */
    public fun makeSubsampling(s: Subsampling): SkYUVAInfo = copy(subsampling = s)

    /**
     * Mirrors `SkYUVAInfo SkYUVAInfo::makeDimensions(SkISize)`. An empty
     * [d] produces an invalid info.
     */
    public fun makeDimensions(d: SkISize): SkYUVAInfo = copy(dimensions = d)

    public companion object {
        /** Maximum number of planes any [PlaneConfig] requires. Mirrors `SkYUVAInfo::kMaxPlanes`. */
        public const val kMaxPlanes: Int = 4

        /** Mirrors `constexpr int SkYUVAInfo::NumPlanes(PlaneConfig)`. */
        public fun NumPlanes(planeConfig: PlaneConfig): Int = when (planeConfig) {
            PlaneConfig.kUnknown -> 0
            PlaneConfig.kY_U_V,
            PlaneConfig.kY_V_U,
            -> 3
            PlaneConfig.kY_UV,
            PlaneConfig.kY_VU,
            -> 2
            PlaneConfig.kYUV,
            PlaneConfig.kUYV,
            -> 1
            PlaneConfig.kY_U_V_A,
            PlaneConfig.kY_V_U_A,
            -> 4
            PlaneConfig.kY_UV_A,
            PlaneConfig.kY_VU_A,
            -> 3
            PlaneConfig.kYUVA,
            PlaneConfig.kUYVA,
            -> 1
        }

        /** Mirrors `constexpr int SkYUVAInfo::NumChannelsInPlane(PlaneConfig, int i)`. */
        public fun NumChannelsInPlane(config: PlaneConfig, i: Int): Int = when (config) {
            PlaneConfig.kUnknown -> 0
            PlaneConfig.kY_U_V,
            PlaneConfig.kY_V_U,
            -> if (i in 0..2) 1 else 0
            PlaneConfig.kY_UV,
            PlaneConfig.kY_VU,
            -> when (i) {
                0 -> 1
                1 -> 2
                else -> 0
            }
            PlaneConfig.kYUV,
            PlaneConfig.kUYV,
            -> if (i == 0) 3 else 0
            PlaneConfig.kY_U_V_A,
            PlaneConfig.kY_V_U_A,
            -> if (i in 0..3) 1 else 0
            PlaneConfig.kY_UV_A,
            PlaneConfig.kY_VU_A,
            -> when (i) {
                0 -> 1
                1 -> 2
                2 -> 1
                else -> 0
            }
            PlaneConfig.kYUVA,
            PlaneConfig.kUYVA,
            -> if (i == 0) 4 else 0
        }

        /** Mirrors `bool SkYUVAInfo::HasAlpha(PlaneConfig)`. */
        public fun HasAlpha(config: PlaneConfig): Boolean = when (config) {
            PlaneConfig.kY_U_V_A,
            PlaneConfig.kY_V_U_A,
            PlaneConfig.kY_UV_A,
            PlaneConfig.kY_VU_A,
            PlaneConfig.kYUVA,
            PlaneConfig.kUYVA,
            -> true
            else -> false
        }

        /**
         * Mirrors `std::tuple<int, int> SkYUVAInfo::SubsamplingFactors(Subsampling)`.
         * Returns `(xFactor, yFactor)` — the J:a:b ratio of Y to U/V
         * resolution along each axis.
         */
        public fun SubsamplingFactors(subsampling: Subsampling): Pair<Int, Int> = when (subsampling) {
            Subsampling.kUnknown -> 0 to 0
            Subsampling.k444 -> 1 to 1
            Subsampling.k422 -> 2 to 1
            Subsampling.k420 -> 2 to 2
            Subsampling.k440 -> 1 to 2
            Subsampling.k411 -> 4 to 1
            Subsampling.k410 -> 4 to 2
        }

        /**
         * Mirrors `std::tuple<int, int> SkYUVAInfo::PlaneSubsamplingFactors(PlaneConfig, Subsampling, int)`.
         *
         * Returns the subsampling factor for plane [planeIdx]. The Y/A
         * planes report `(1, 1)` ; the U/V planes report
         * [SubsamplingFactors] of [subsampling]. Invalid combinations
         * return `(0, 0)`.
         */
        public fun PlaneSubsamplingFactors(
            config: PlaneConfig,
            subsampling: Subsampling,
            planeIdx: Int,
        ): Pair<Int, Int> {
            val n = NumPlanes(config)
            if (planeIdx < 0 || planeIdx >= n) return 0 to 0
            // For interleaved configs (single plane carrying YUV / YUVA),
            // subsampling must be k444 — the upstream rule.
            val isInterleaved = when (config) {
                PlaneConfig.kYUV, PlaneConfig.kUYV, PlaneConfig.kYUVA, PlaneConfig.kUYVA -> true
                else -> false
            }
            if (isInterleaved && subsampling != Subsampling.k444) return 0 to 0
            // Y is always plane 0 ; A (if present) is the last plane for
            // *_A configs ; everything else is U/V.
            val isYPlane = planeIdx == 0
            val isAPlane = HasAlpha(config) && when (config) {
                // For the *_A configs, the alpha plane is the last separate plane.
                PlaneConfig.kY_U_V_A, PlaneConfig.kY_V_U_A -> planeIdx == 3
                PlaneConfig.kY_UV_A, PlaneConfig.kY_VU_A -> planeIdx == 2
                // Interleaved YUVA / UYVA — the single plane carries alpha
                // alongside YUV, no separate A plane.
                else -> false
            }
            return if (isYPlane || isAPlane) 1 to 1 else SubsamplingFactors(subsampling)
        }

        /**
         * Mirrors `int SkYUVAInfo::PlaneDimensions(SkISize, PlaneConfig, Subsampling, SkEncodedOrigin, SkISize*)`.
         *
         * Fills the first `<ret>` entries of [outPlaneDimensions] with the
         * expected size of each plane (in storage order) and returns the
         * number of planes written. The remaining entries are not
         * modified.
         */
        public fun PlaneDimensions(
            imageDimensions: SkISize,
            config: PlaneConfig,
            subsampling: Subsampling,
            origin: SkEncodedOrigin,
            outPlaneDimensions: Array<SkISize>,
        ): Int {
            val n = NumPlanes(config)
            require(outPlaneDimensions.size >= kMaxPlanes) {
                "outPlaneDimensions.size=${outPlaneDimensions.size} < kMaxPlanes=$kMaxPlanes"
            }
            // Storage order : swap W/H if origin rotates.
            val (storedW, storedH) = if (origin.swapsWidthHeight()) {
                imageDimensions.height to imageDimensions.width
            } else {
                imageDimensions.width to imageDimensions.height
            }
            for (i in 0 until n) {
                val (fx, fy) = PlaneSubsamplingFactors(config, subsampling, i)
                outPlaneDimensions[i] = if (fx <= 0 || fy <= 0) {
                    SkISize.Make(0, 0)
                } else {
                    SkISize.Make((storedW + fx - 1) / fx, (storedH + fy - 1) / fy)
                }
            }
            return n
        }
    }
}
