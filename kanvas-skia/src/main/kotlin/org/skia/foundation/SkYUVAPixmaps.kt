package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkYUVAPixmaps`](https://github.com/google/skia/blob/main/include/core/SkYUVAPixmaps.h)
 * — a [SkYUVAInfo] plus the per-plane [SkPixmap] views that back it.
 * Each plane's [SkPixmap] carries its own [SkImageInfo] and row-byte
 * stride, so a single [SkYUVAPixmaps] fully describes a YUV(A) image
 * without owning the pixel memory.
 *
 * **R-suivi.41 scope.** The metadata holder ([info] + [planes]) ships
 * alongside a software [toRGBA8888] conversion that materialises an
 * 8888 [SkBitmap] from the YUV planes — enough to feed
 * [SkImages.YUVA] and the existing [SkCanvas.drawImage] path. The
 * conversion supports the three-plane `kY_U_V` / `kY_V_U` configs
 * (single-channel `kAlpha_8` planes — Y at full resolution, U/V at
 * `info.subsampling`'s reduced resolution) with the seven
 * [SkYUVAInfo.YUVColorSpace] enum entries (Rec.601 / Rec.709 / BT.2020
 * limited+full, JPEG full, identity). Bi-planar (`kY_UV` / `kY_VU`),
 * interleaved single-plane (`kYUV` / `kUYV` / `kYUVA` / `kUYVA`) and
 * alpha-bearing configs are **not yet supported** — they raise
 * `IllegalStateException` and are tracked as a follow-up R-suivi item.
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

    /**
     * Convert this multi-plane YUV(A) holder into a single full-resolution
     * raster [SkBitmap] with [SkColorType.kRGBA_8888]. The Y plane is
     * sampled at full resolution ; U/V planes are nearest-neighbour
     * resampled per their [SkYUVAInfo.planeSubsamplingFactors] (`(fx, fy)`
     * — the U/V coord is `(x / fx, y / fy)`). The matrix that maps the
     * sampled `(Y, U, V)` triplet to linear `[0, 1]` RGB is selected by
     * [SkYUVAInfo.yuvColorSpace] — see [yuvToRgbMatrix].
     *
     * Currently supports the three-plane configs ([SkYUVAInfo.PlaneConfig.kY_U_V],
     * `kY_V_U`). Bi-planar (`kY_UV` / `kY_VU`), interleaved, and
     * alpha-bearing configs raise [IllegalStateException] — a follow-up
     * R-suivi item.
     *
     * The returned bitmap is opaque (alpha = 255) ; alpha-plane carrying
     * configs are not yet wired through.
     */
    public fun toRGBA8888(): SkBitmap {
        check(isValid()) { "SkYUVAPixmaps.toRGBA8888 requires isValid()=true" }
        val w = info.dimensions.width
        val h = info.dimensions.height
        require(w > 0 && h > 0) { "non-positive dimensions ${w}x$h" }

        val rgba = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        val mx = yuvToRgbMatrix(info.yuvColorSpace)

        when (info.planeConfig) {
            SkYUVAInfo.PlaneConfig.kY_U_V,
            SkYUVAInfo.PlaneConfig.kY_V_U,
            -> {
                val uIdx = if (info.planeConfig == SkYUVAInfo.PlaneConfig.kY_U_V) 1 else 2
                val vIdx = if (info.planeConfig == SkYUVAInfo.PlaneConfig.kY_U_V) 2 else 1
                val yPlane = planes[0]
                val uPlane = planes[uIdx]
                val vPlane = planes[vIdx]
                val (fxU, fyU) = info.planeSubsamplingFactors(uIdx)
                val (fxV, fyV) = info.planeSubsamplingFactors(vIdx)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val yVal = readChannel(yPlane, x, y, 0)
                        val uVal = readChannel(uPlane, x / fxU, y / fyU, 0)
                        val vVal = readChannel(vPlane, x / fxV, y / fyV, 0)
                        rgba.setPixel(x, y, applyMatrix(mx, yVal, uVal, vVal))
                    }
                }
            }
            else -> error(
                "SkYUVAPixmaps.toRGBA8888 not yet implemented for planeConfig=${info.planeConfig} " +
                    "(bi-planar, interleaved and alpha configs are R-suivi follow-ups)",
            )
        }
        return rgba
    }

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

        /**
         * Read a single channel (0..bytesPerPixel) of plane [p] at
         * `(x, y)` as a normalised `[0, 1]` float. Out-of-range coords
         * are clamped to the plane bounds — defensive against off-by-one
         * sampling at the bottom-right edge when `dim % fx != 0`.
         */
        private fun readChannel(p: SkPixmap, x: Int, y: Int, channel: Int): Float {
            val pw = p.width()
            val ph = p.height()
            val cx = x.coerceIn(0, pw - 1)
            val cy = y.coerceIn(0, ph - 1)
            // YUV planes are stored as packed bytes — Skia uses kAlpha_8 for
            // single-channel planes and kR8G8 / kR8G8B8A8 etc. for multi.
            // We bypass the SkPixmap.getColor decoder (which would map a
            // single-byte plane to an alpha-only ARGB) and read the raw
            // byte at the channel offset.
            val info = p.info()
            val bpp = info.bytesPerPixel()
            val rowBytes = p.rowBytes()
            val addr = p.addr()
            val offset = cy * rowBytes + cx * bpp + channel
            val byte = addr.get(offset).toInt() and 0xFF
            return byte / 255f
        }

        /**
         * Apply the 4x3 YUV→RGB affine matrix [mx] (row-major,
         * `[a b c bias]`) to a `(Y, U, V)` triplet in `[0, 1]` and pack
         * the resulting RGB as an opaque non-premul [SkColor].
         */
        private fun applyMatrix(mx: FloatArray, y: Float, u: Float, v: Float): SkColor {
            val r = mx[0] * y + mx[1] * u + mx[2] * v + mx[3]
            val g = mx[4] * y + mx[5] * u + mx[6] * v + mx[7]
            val b = mx[8] * y + mx[9] * u + mx[10] * v + mx[11]
            val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            return SkColorSetARGB(0xFF, ri, gi, bi)
        }

        /**
         * Return the 4×3 row-major affine matrix that maps a normalised
         * `(Y, U, V)` triplet in `[0, 1]` to linear RGB, for the supplied
         * [cs]. Values mirror the precomputed `yuv_to_rgb_array` entries
         * in upstream `src/core/SkYUVMath.cpp` (the "low-precision"
         * branch — exact-to-six-decimals reproduction of the upstream
         * single-precision floats).
         *
         * Layout : `[m00 m01 m02 b0  m10 m11 m12 b1  m20 m21 m22 b2]`,
         * where the bias `b*` already absorbs the `-0.5` chroma offset
         * (and, for limited-range entries, the `-16/255` luma offset).
         *
         * Falls back to the JPEG-full matrix for color spaces we don't
         * (yet) hard-code — the seven enum entries are explicitly listed
         * with the seven upstream matrices, so no fallback is exercised
         * in practice.
         */
        private fun yuvToRgbMatrix(cs: SkYUVAInfo.YUVColorSpace): FloatArray = when (cs) {
            // JPEG full-range BT.601 — yuv_to_rgb_array[0].
            SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace -> floatArrayOf(
                1.000000f, 0.000000f, 1.402000f, -0.703749f,
                1.000000f, -0.344136f, -0.714136f, 0.531211f,
                1.000000f, 1.772000f, 0.000000f, -0.889475f,
            )
            // Rec.601 limited-range — yuv_to_rgb_array[1].
            SkYUVAInfo.YUVColorSpace.kRec601_Limited_YUV_ColorSpace -> floatArrayOf(
                1.164384f, 0.000000f, 1.596027f, -0.874202f,
                1.164384f, -0.391762f, -0.812968f, 0.531668f,
                1.164384f, 2.017232f, 0.000000f, -1.085631f,
            )
            // Rec.709 full-range — yuv_to_rgb_array[2].
            SkYUVAInfo.YUVColorSpace.kRec709_Full_YUV_ColorSpace -> floatArrayOf(
                1.000000f, 0.000000f, 1.574800f, -0.790488f,
                1.000000f, -0.187324f, -0.468124f, 0.329010f,
                1.000000f, 1.855600f, 0.000000f, -0.931439f,
            )
            // Rec.709 limited-range — yuv_to_rgb_array[3].
            SkYUVAInfo.YUVColorSpace.kRec709_Limited_YUV_ColorSpace -> floatArrayOf(
                1.164384f, 0.000000f, 1.792741f, -0.972945f,
                1.164384f, -0.213249f, -0.532909f, 0.301483f,
                1.164384f, 2.112402f, 0.000000f, -1.133402f,
            )
            // BT.2020 8-bit full-range — yuv_to_rgb_array[4].
            SkYUVAInfo.YUVColorSpace.kBT2020_8bit_Full_YUV_ColorSpace -> floatArrayOf(
                1.000000f, 0.000000f, 1.474600f, -0.740191f,
                1.000000f, -0.164553f, -0.571353f, 0.369396f,
                1.000000f, 1.881400f, 0.000000f, -0.944389f,
            )
            // BT.2020 8-bit limited-range — yuv_to_rgb_array[5].
            SkYUVAInfo.YUVColorSpace.kBT2020_8bit_Limited_YUV_ColorSpace -> floatArrayOf(
                1.164384f, 0.000000f, 1.678674f, -0.915688f,
                1.164384f, -0.187326f, -0.650424f, 0.347458f,
                1.164384f, 2.141772f, 0.000000f, -1.148145f,
            )
            // Identity passthrough — (Y, U, V) → (G, B, R) per
            // `kIdentity_SkYUVColorSpace` ; SkColorMatrix_YUV2RGB stores
            // the identity matrix (m[0]=m[6]=m[12]=1) which maps YUV
            // directly onto RGB indices (Y→R, U→G, V→B). We follow that
            // here for parity with upstream's matrix dump.
            SkYUVAInfo.YUVColorSpace.kIdentity_YUV_ColorSpace -> floatArrayOf(
                1.000000f, 0.000000f, 0.000000f, 0.000000f,
                0.000000f, 1.000000f, 0.000000f, 0.000000f,
                0.000000f, 0.000000f, 1.000000f, 0.000000f,
            )
        }
    }
}
