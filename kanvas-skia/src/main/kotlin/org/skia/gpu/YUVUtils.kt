package org.skia.gpu

import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType

/**
 * Pure-Kotlin port of upstream Skia's
 * [`tools/gpu/YUVUtils`](https://github.com/google/skia/blob/main/tools/gpu/YUVUtils.h)
 * — the helpers used by GMs (`compositor_quads_image`, the
 * `LazyYUVImage` family) to materialise an RGBA bitmap from a
 * multi-plane YUV source.
 *
 * **Why under `org.skia.gpu` ?** Upstream sites the file under
 * `tools/gpu/` even though the conversion is CPU-only — the historical
 * placement is "the helper used by the GPU-backed YUV image tests".
 * The Kotlin port mirrors the namespace for parity with `_GM` files
 * that import it via `org.skia.gpu.YUVUtils.…`.
 *
 * **R-final.8 surface.** Shipped : the [yuvToRgb] one-shot helper that
 * drives the CompositorQuadsImageGM. Two colour-space matrices are
 * supported (BT.601 limited / BT.709 limited) and three subsampling
 * profiles (4:4:4 / 4:2:2 / 4:2:0 — chroma upsampled by nearest-
 * neighbour). Other variants (full-range, BT.2020, identity, alpha
 * plane) are queued for the next sprint that needs them.
 */
public object YUVUtils {

    /**
     * YUV colour-space conversion matrix variant. Mirrors the subset
     * of `SkYUVColorSpace` (see [org.skia.foundation.SkYUVAInfo.YUVColorSpace])
     * that this raster-only helper supports.
     *
     * Coefficients follow ITU-R Rec.601 (`BT601`) and Rec.709 (`BT709`)
     * limited-range encodings — Y in `[16, 235]`, Cb/Cr in `[16, 240]`
     * — which matches the JPEG-conformant content the GMs feed in.
     */
    public enum class YUVColorSpace { BT601, BT709 }

    /**
     * Chroma subsampling profile of the YUV planes. Mirrors the
     * `SkYUVAInfo::Subsampling` triplet that
     * [org.skia.foundation.SkYUVAPixmaps] understands. The U/V plane
     * dimensions are derived from the Y plane :
     *  - `k444` : `Uw = Yw,     Uh = Yh`     (no subsampling).
     *  - `k422` : `Uw = Yw / 2, Uh = Yh`     (horizontal halving).
     *  - `k420` : `Uw = Yw / 2, Uh = Yh / 2` (both axes halved).
     *
     * The decoder upscales the chroma planes by integer nearest-
     * neighbour, matching the simplest YUV→RGB path Skia's
     * `LazyYUVImage` exposes.
     */
    public enum class YUVSubsampling { k444, k422, k420 }

    /**
     * Convert three 8-bpp planes ([y], [u], [v]) into a fully-decoded
     * sRGB [SkBitmap] of dimensions [width] × [height]. Mirrors the
     * "build a CPU [SkBitmap] from the YUV planes for compare" path
     * `LazyYUVImage::reset` falls back to when no GPU context is
     * available.
     *
     * Coefficient sets (8-bit limited-range) :
     *  - BT.601 : `R = Y + 1.402   * (V-128)`,
     *             `G = Y - 0.344136 * (U-128) - 0.714136 * (V-128)`,
     *             `B = Y + 1.772   * (U-128)`.
     *  - BT.709 : `R = Y + 1.5748  * (V-128)`,
     *             `G = Y - 0.1873  * (U-128) - 0.4681  * (V-128)`,
     *             `B = Y + 1.8556  * (U-128)`.
     *
     * Throws [IllegalArgumentException] if any plane is too small for
     * its derived dimensions.
     */
    public fun yuvToRgb(
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        width: Int,
        height: Int,
        colorSpace: YUVColorSpace = YUVColorSpace.BT601,
        subsampling: YUVSubsampling = YUVSubsampling.k420,
    ): SkBitmap {
        require(width > 0 && height > 0) { "non-positive dimensions: ${width}x$height" }
        require(y.size >= width * height) {
            "Y plane too small : ${y.size} < ${width * height}"
        }
        val (uvW, uvH) = chromaDimensions(width, height, subsampling)
        require(u.size >= uvW * uvH) {
            "U plane too small : ${u.size} < ${uvW * uvH} (subsampling=$subsampling)"
        }
        require(v.size >= uvW * uvH) {
            "V plane too small : ${v.size} < ${uvW * uvH} (subsampling=$subsampling)"
        }

        val bitmap = SkBitmap(
            width = width,
            height = height,
            colorSpace = SkColorSpace.makeSRGB(),
            colorType = SkColorType.kRGBA_8888,
        )

        // Pre-compute the chroma stride in source pixels (Y) per chroma
        // sample. For k444 it's 1, for k422 horizontal stride 2 / vertical 1,
        // for k420 both axes 2.
        val sx = when (subsampling) {
            YUVSubsampling.k444 -> 1
            YUVSubsampling.k422 -> 2
            YUVSubsampling.k420 -> 2
        }
        val sy = when (subsampling) {
            YUVSubsampling.k444 -> 1
            YUVSubsampling.k422 -> 1
            YUVSubsampling.k420 -> 2
        }

        for (py in 0 until height) {
            val cy = (py / sy).coerceAtMost(uvH - 1)
            for (px in 0 until width) {
                val cx = (px / sx).coerceAtMost(uvW - 1)
                val yVal = (y[py * width + px].toInt() and 0xFF)
                val uVal = (u[cy * uvW + cx].toInt() and 0xFF)
                val vVal = (v[cy * uvW + cx].toInt() and 0xFF)
                val argb = yuvSample(yVal, uVal, vVal, colorSpace)
                bitmap.setPixel(px, py, argb)
            }
        }
        return bitmap
    }

    /**
     * Compute the dimensions of the U/V planes for a Y plane of
     * `width × height` under the given [subsampling] profile. Mirrors
     * `SkYUVAInfo::PlaneDimensions` for the planar 3-plane configs.
     */
    public fun chromaDimensions(
        width: Int,
        height: Int,
        subsampling: YUVSubsampling,
    ): Pair<Int, Int> = when (subsampling) {
        YUVSubsampling.k444 -> width to height
        // 4:2:2 — half horizontal resolution. Round up so a 9-wide Y
        // plane gets a 5-wide chroma plane (matches `(w + 1) / 2`).
        YUVSubsampling.k422 -> ((width + 1) / 2) to height
        // 4:2:0 — half on both axes.
        YUVSubsampling.k420 -> ((width + 1) / 2) to ((height + 1) / 2)
    }

    /**
     * Decode a single (Y, U, V) triplet to an 0xAARRGGBB Int via the
     * [colorSpace]'s coefficient table. Channels are clamped to
     * `[0, 255]` ; alpha is always `0xFF` (opaque — YUV has no alpha).
     */
    private fun yuvSample(
        y: Int,
        u: Int,
        v: Int,
        colorSpace: YUVColorSpace,
    ): Int {
        val cu = (u - 128).toFloat()
        val cv = (v - 128).toFloat()
        val yf = y.toFloat()
        val r: Float
        val g: Float
        val b: Float
        when (colorSpace) {
            YUVColorSpace.BT601 -> {
                r = yf + 1.402f * cv
                g = yf - 0.344136f * cu - 0.714136f * cv
                b = yf + 1.772f * cu
            }
            YUVColorSpace.BT709 -> {
                r = yf + 1.5748f * cv
                g = yf - 0.1873f * cu - 0.4681f * cv
                b = yf + 1.8556f * cu
            }
        }
        val ri = r.toInt().coerceIn(0, 255)
        val gi = g.toInt().coerceIn(0, 255)
        val bi = b.toInt().coerceIn(0, 255)
        return SkColorSetARGB(0xFF, ri, gi, bi)
    }

    /**
     * Round-trip helper : decode the JPEG bytes into a [SkBitmap], then
     * re-encode the RGB into Y/U/V planes via the BT.601 (limited-range)
     * matrix at the requested [subsampling], and finally call
     * [yuvToRgb] to round-trip back to RGBA. Mirrors the path taken
     * by upstream's `LazyYUVImage::Make(SkData jpegData, …)` factory
     * when feeding a raster bitmap into the YUV pipeline. Useful for
     * GMs that want a YUV-flavoured copy of a JPEG without round-
     * tripping through libjpeg's native YUV decode.
     */
    @Suppress("unused")
    public fun yuvFromRgba(
        rgba: SkBitmap,
        subsampling: YUVSubsampling = YUVSubsampling.k420,
        colorSpace: YUVColorSpace = YUVColorSpace.BT601,
    ): Triple<ByteArray, ByteArray, ByteArray> {
        require(rgba.colorType == SkColorType.kRGBA_8888) {
            "yuvFromRgba expects kRGBA_8888 source, got ${rgba.colorType}"
        }
        val w = rgba.width
        val h = rgba.height
        val (uvW, uvH) = chromaDimensions(w, h, subsampling)
        val yPlane = ByteArray(w * h)
        val uPlane = ByteArray(uvW * uvH)
        val vPlane = ByteArray(uvW * uvH)

        val sx = when (subsampling) {
            YUVSubsampling.k444 -> 1
            YUVSubsampling.k422 -> 2
            YUVSubsampling.k420 -> 2
        }
        val sy = when (subsampling) {
            YUVSubsampling.k444 -> 1
            YUVSubsampling.k422 -> 1
            YUVSubsampling.k420 -> 2
        }

        // Per-pixel Y first (Y has full resolution).
        for (py in 0 until h) {
            for (px in 0 until w) {
                val c = rgba.getPixel(px, py)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val bv = c and 0xFF
                val yv = when (colorSpace) {
                    YUVColorSpace.BT601 -> 0.299f * r + 0.587f * g + 0.114f * bv
                    YUVColorSpace.BT709 -> 0.2126f * r + 0.7152f * g + 0.0722f * bv
                }
                yPlane[py * w + px] = yv.toInt().coerceIn(0, 255).toByte()
            }
        }

        // Chroma : average the source pixels covered by each chroma
        // sample, then convert the averaged RGB to U/V.
        for (cy in 0 until uvH) {
            for (cx in 0 until uvW) {
                var rs = 0
                var gs = 0
                var bs = 0
                var n = 0
                for (dy in 0 until sy) {
                    val py = cy * sy + dy
                    if (py >= h) continue
                    for (dx in 0 until sx) {
                        val px = cx * sx + dx
                        if (px >= w) continue
                        val c = rgba.getPixel(px, py)
                        rs += (c shr 16) and 0xFF
                        gs += (c shr 8) and 0xFF
                        bs += c and 0xFF
                        n++
                    }
                }
                val r = rs.toFloat() / n
                val g = gs.toFloat() / n
                val bv = bs.toFloat() / n
                val u = when (colorSpace) {
                    YUVColorSpace.BT601 -> 128f + (-0.168736f * r - 0.331264f * g + 0.5f * bv)
                    YUVColorSpace.BT709 -> 128f + (-0.1146f * r - 0.3854f * g + 0.5f * bv)
                }
                val v = when (colorSpace) {
                    YUVColorSpace.BT601 -> 128f + (0.5f * r - 0.418688f * g - 0.081312f * bv)
                    YUVColorSpace.BT709 -> 128f + (0.5f * r - 0.4542f * g - 0.0458f * bv)
                }
                uPlane[cy * uvW + cx] = u.toInt().coerceIn(0, 255).toByte()
                vPlane[cy * uvW + cx] = v.toInt().coerceIn(0, 255).toByte()
            }
        }
        return Triple(yPlane, uPlane, vPlane)
    }

    /**
     * Tag suppressing the "unused" warning on [SkAlphaType] re-export.
     * The enum is referenced through the bitmap path the helper builds.
     */
    @Suppress("unused")
    private val unusedAlpha: SkAlphaType = SkAlphaType.kUnpremul
}
