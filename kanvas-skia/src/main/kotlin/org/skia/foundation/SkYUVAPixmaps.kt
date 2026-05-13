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
     * Supports every [SkYUVAInfo.PlaneConfig] enum value :
     *  - **Three-plane** (`kY_U_V` / `kY_V_U`) — Y at plane 0, U/V at
     *    planes 1/2 (or 2/1) as single-channel `kAlpha_8` planes.
     *  - **Bi-planar** (`kY_UV` / `kY_VU`) — Y at plane 0, packed UV
     *    (or VU) at plane 1 with `bytesPerPixel == 2` (R-suivi.48).
     *  - **Interleaved** (`kYUV` / `kUYV`) — single plane carrying
     *    YUV (or UYV) with `bytesPerPixel == 3` ; subsampling must be
     *    `k444` (single-plane interleaved configs only support 4:4:4 —
     *    enforced by [SkYUVAInfo]).
     *  - **Alpha-bearing** (`kY_U_V_A`, `kY_V_U_A`, `kY_UV_A`, `kY_VU_A`,
     *    `kYUVA`, `kUYVA`) — same Y/UV layout as the alpha-less variants
     *    plus an A plane (separate or interleaved). The alpha is sampled
     *    per-pixel and applied as a non-premul ARGB alpha channel.
     *
     * The returned bitmap is opaque (alpha = 255) for alpha-less configs
     * and carries the per-pixel alpha for `*_A` / `YUVA` / `UYVA`.
     */
    public fun toRGBA8888(): SkBitmap {
        check(isValid()) { "SkYUVAPixmaps.toRGBA8888 requires isValid()=true" }
        val w = info.dimensions.width
        val h = info.dimensions.height
        require(w > 0 && h > 0) { "non-positive dimensions ${w}x$h" }

        val rgba = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        val mx = yuvToRgbMatrix(info.yuvColorSpace)

        // Helper closures share the per-pixel write-back so each branch
        // only computes a (Y, U, V, A) sample and we hand the rest to
        // applyMatrix + setPixel.
        val ys = FloatArray(1); val us = FloatArray(1); val vs = FloatArray(1); val as_ = FloatArray(1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                sampleYUVA(x, y, ys, us, vs, as_)
                val rgb = applyMatrix(mx, ys[0], us[0], vs[0])
                val ai = (as_[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val color = (ai shl 24) or (rgb and 0x00FFFFFF)
                rgba.setPixel(x, y, color)
            }
        }
        return rgba
    }

    /**
     * Sample the (Y, U, V, A) quadruplet for source pixel `(x, y)` from
     * this pixmap's planes, dispatched on [SkYUVAInfo.planeConfig].
     * Alpha defaults to `1.0` for alpha-less configs. Outputs land in
     * the supplied 1-element float arrays (avoiding per-pixel boxing).
     */
    private fun sampleYUVA(
        x: Int,
        y: Int,
        outY: FloatArray,
        outU: FloatArray,
        outV: FloatArray,
        outA: FloatArray,
    ) {
        outA[0] = 1f
        when (val cfg = info.planeConfig) {
            SkYUVAInfo.PlaneConfig.kY_U_V,
            SkYUVAInfo.PlaneConfig.kY_V_U,
            -> sampleThreePlane(x, y, cfg, outY, outU, outV, alphaPlaneIdx = -1, outA)
            SkYUVAInfo.PlaneConfig.kY_UV,
            SkYUVAInfo.PlaneConfig.kY_VU,
            -> sampleBiPlane(x, y, cfg, outY, outU, outV, alphaPlaneIdx = -1, outA)
            SkYUVAInfo.PlaneConfig.kYUV,
            SkYUVAInfo.PlaneConfig.kUYV,
            -> sampleInterleaved(x, y, cfg, outY, outU, outV, outA, hasAlpha = false)
            SkYUVAInfo.PlaneConfig.kY_U_V_A,
            SkYUVAInfo.PlaneConfig.kY_V_U_A,
            -> sampleThreePlane(
                x, y, cfg, outY, outU, outV,
                alphaPlaneIdx = 3, outA,
            )
            SkYUVAInfo.PlaneConfig.kY_UV_A,
            SkYUVAInfo.PlaneConfig.kY_VU_A,
            -> sampleBiPlane(
                x, y, cfg, outY, outU, outV,
                alphaPlaneIdx = 2, outA,
            )
            SkYUVAInfo.PlaneConfig.kYUVA,
            SkYUVAInfo.PlaneConfig.kUYVA,
            -> sampleInterleaved(x, y, cfg, outY, outU, outV, outA, hasAlpha = true)
            SkYUVAInfo.PlaneConfig.kUnknown -> error("unsupported planeConfig=kUnknown")
        }
    }

    /**
     * Three-plane sampler — Y at plane 0, U/V at planes 1/2 (or 2/1
     * for `kY_V_U` / `kY_V_U_A`). `alphaPlaneIdx >= 0` reads the alpha
     * sample from the corresponding plane (full-resolution, single
     * channel) ; `alphaPlaneIdx < 0` leaves [outA] untouched (1.0).
     */
    private fun sampleThreePlane(
        x: Int,
        y: Int,
        cfg: SkYUVAInfo.PlaneConfig,
        outY: FloatArray,
        outU: FloatArray,
        outV: FloatArray,
        alphaPlaneIdx: Int,
        outA: FloatArray,
    ) {
        val uIdx: Int
        val vIdx: Int
        when (cfg) {
            SkYUVAInfo.PlaneConfig.kY_U_V, SkYUVAInfo.PlaneConfig.kY_U_V_A -> {
                uIdx = 1; vIdx = 2
            }
            SkYUVAInfo.PlaneConfig.kY_V_U, SkYUVAInfo.PlaneConfig.kY_V_U_A -> {
                uIdx = 2; vIdx = 1
            }
            else -> error("sampleThreePlane: unexpected cfg=$cfg")
        }
        val (fxU, fyU) = info.planeSubsamplingFactors(uIdx)
        val (fxV, fyV) = info.planeSubsamplingFactors(vIdx)
        outY[0] = readChannel(planes[0], x, y, 0)
        outU[0] = readChannel(planes[uIdx], x / fxU, y / fyU, 0)
        outV[0] = readChannel(planes[vIdx], x / fxV, y / fyV, 0)
        if (alphaPlaneIdx >= 0) {
            // Alpha plane is full-resolution single-channel — no
            // subsampling to undo.
            outA[0] = readChannel(planes[alphaPlaneIdx], x, y, 0)
        }
    }

    /**
     * Bi-planar sampler — Y at plane 0 (single channel), UV (or VU) at
     * plane 1 packed as 2 bytes/pixel. `alphaPlaneIdx >= 0` reads the
     * alpha from a separate full-resolution plane (`kY_UV_A` / `kY_VU_A`).
     */
    private fun sampleBiPlane(
        x: Int,
        y: Int,
        cfg: SkYUVAInfo.PlaneConfig,
        outY: FloatArray,
        outU: FloatArray,
        outV: FloatArray,
        alphaPlaneIdx: Int,
        outA: FloatArray,
    ) {
        val uChannel: Int
        val vChannel: Int
        when (cfg) {
            SkYUVAInfo.PlaneConfig.kY_UV, SkYUVAInfo.PlaneConfig.kY_UV_A -> {
                uChannel = 0; vChannel = 1
            }
            SkYUVAInfo.PlaneConfig.kY_VU, SkYUVAInfo.PlaneConfig.kY_VU_A -> {
                uChannel = 1; vChannel = 0
            }
            else -> error("sampleBiPlane: unexpected cfg=$cfg")
        }
        val (fxUV, fyUV) = info.planeSubsamplingFactors(1)
        outY[0] = readChannel(planes[0], x, y, 0)
        // Plane 1 carries 2 bytes/pixel (U + V interleaved). readChannel
        // walks bytesPerPixel internally, so passing the channel offset
        // (0 or 1) does the right thing.
        outU[0] = readChannel(planes[1], x / fxUV, y / fyUV, uChannel)
        outV[0] = readChannel(planes[1], x / fxUV, y / fyUV, vChannel)
        if (alphaPlaneIdx >= 0) {
            outA[0] = readChannel(planes[alphaPlaneIdx], x, y, 0)
        }
    }

    /**
     * Interleaved single-plane sampler — `kYUV` / `kUYV` / `kYUVA` /
     * `kUYVA`. Subsampling must be `k444` (the upstream `SkYUVAInfo`
     * rule, returns `(0, 0)` factors otherwise — we read at
     * full resolution and rely on the validator). When [hasAlpha] is
     * true, reads a 4th channel from the same plane.
     */
    private fun sampleInterleaved(
        x: Int,
        y: Int,
        cfg: SkYUVAInfo.PlaneConfig,
        outY: FloatArray,
        outU: FloatArray,
        outV: FloatArray,
        outA: FloatArray,
        hasAlpha: Boolean,
    ) {
        // Channel order : YUV / UYV / YUVA / UYVA.
        val yChannel: Int
        val uChannel: Int
        val vChannel: Int
        val aChannel: Int
        when (cfg) {
            SkYUVAInfo.PlaneConfig.kYUV -> {
                yChannel = 0; uChannel = 1; vChannel = 2; aChannel = -1
            }
            SkYUVAInfo.PlaneConfig.kUYV -> {
                yChannel = 1; uChannel = 0; vChannel = 2; aChannel = -1
            }
            SkYUVAInfo.PlaneConfig.kYUVA -> {
                yChannel = 0; uChannel = 1; vChannel = 2; aChannel = 3
            }
            SkYUVAInfo.PlaneConfig.kUYVA -> {
                yChannel = 1; uChannel = 0; vChannel = 2; aChannel = 3
            }
            else -> error("sampleInterleaved: unexpected cfg=$cfg")
        }
        outY[0] = readChannel(planes[0], x, y, yChannel)
        outU[0] = readChannel(planes[0], x, y, uChannel)
        outV[0] = readChannel(planes[0], x, y, vChannel)
        if (hasAlpha && aChannel >= 0) {
            outA[0] = readChannel(planes[0], x, y, aChannel)
        }
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
