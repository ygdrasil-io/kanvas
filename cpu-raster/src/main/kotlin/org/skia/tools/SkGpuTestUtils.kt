package org.skia.tools

import org.skia.foundation.SkImage
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkYUVAInfo
import org.skia.gpu.YUVUtils.YUVSubsampling
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB

/**
 * Mirrors Skia's `tools/gpu/YUVUtils.h` test helpers — split an RGB(A)
 * [SkImage] into per-plane A8 [SkImage]s under a chosen
 * [SkYUVColorSpace] + chroma [YUVSubsampling], suitable for feeding
 * back into [org.skia.foundation.SkImages.YUVA] (the raster equivalent
 * of upstream's `SkImages::TextureFromYUVAPixmaps`).
 *
 * `:kanvas-skia` does not yet implement the YUV plane-splitting math
 * (the cross-channel mixer matrices vary per [SkYUVColorSpace] +
 * full / limited-range flag). The surface here is flag-planting so GMs
 * that exercise the upstream test helper (`gm/wacky_yuv_formats.cpp::YUVSplitterGM`)
 * compile against a real call site rather than against a placeholder
 * `// TODO: missing API` comment.
 *
 * Mirrors the upstream signature :
 * ```cpp
 * std::tuple<std::array<sk_sp<SkImage>, SkYUVAInfo::kMaxPlanes>,
 *            SkYUVAInfo>
 *   sk_gpu_test::MakeYUVAPlanesAsA8(SkImage*, SkYUVColorSpace,
 *                                   SkYUVAInfo::Subsampling,
 *                                   GrRecordingContext*);
 * ```
 */
public object SkGpuTestUtils {
    /**
     * Container for the per-plane A8 [SkImage] array + the
     * [SkYUVAInfo] describing the plane layout. Mirrors the
     * `std::tuple<std::array<...>, SkYUVAInfo>` upstream return.
     */
    public data class YUVAPlanesAsA8(
        public val planes: List<SkImage>,
        public val info: SkYUVAInfo,
    )

    /**
     * Split [src] into A8 luma + chroma planes under [colorSpace] with
     * [subsampling] chroma decimation. The fourth plane is the alpha
     * channel iff [src] is not opaque (otherwise 3 planes).
     *
     * **TODO: STUB.YUVA_PIXMAPS** — split kernel + `SkColorMatrix_RGB2YUV`
     * matrices not yet wired through `:kanvas-skia`. Implementers should
     * land the same `sk_gpu_test::MakeYUVAPlanesAsA8` math + write the
     * per-channel A8 bitmaps via [org.skia.foundation.SkImage.MakeFromBitmap].
     */
    public fun MakeYUVAPlanesAsA8(
        src: SkImage,
        colorSpace: SkYUVAInfo.YUVColorSpace,
        subsampling: YUVSubsampling = YUVSubsampling.k444,
    ): YUVAPlanesAsA8 {
        val width = src.width
        val height = src.height
        require(width > 0 && height > 0) { "empty source image" }

        val rgba = src.readAsBitmap()
        val hasAlpha = hasAlphaChannel(rgba)
        val planeConfig = if (hasAlpha) SkYUVAInfo.PlaneConfig.kY_U_V_A else SkYUVAInfo.PlaneConfig.kY_U_V
        val info = SkYUVAInfo(
            dimensions = org.graphiks.math.SkISize.Make(width, height),
            planeConfig = planeConfig,
            subsampling = toSkSubsampling(subsampling),
            yuvColorSpace = colorSpace,
        )

        val yPlane = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val uvDim = info.planeDimensions(1)
        val uPlane = SkBitmap(uvDim.width, uvDim.height, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val vPlane = SkBitmap(uvDim.width, uvDim.height, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val aPlane = if (hasAlpha) SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8) else null

        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = rgba.getPixel(x, y)
                yPlane.setPixel(x, y, SkColorSetARGB(rgbToY(c, colorSpace), 0, 0, 0))
                if (hasAlpha) aPlane!!.setPixel(x, y, SkColorSetARGB(SkColorGetA(c), 0, 0, 0))
            }
        }

        val sx = if (subsampling == YUVSubsampling.k444) 1 else 2
        val sy = if (subsampling == YUVSubsampling.k420) 2 else 1
        for (cy in 0 until uvDim.height) {
            for (cx in 0 until uvDim.width) {
                var rs = 0
                var gs = 0
                var bs = 0
                var n = 0
                for (dy in 0 until sy) {
                    val py = cy * sy + dy
                    if (py >= height) continue
                    for (dx in 0 until sx) {
                        val px = cx * sx + dx
                        if (px >= width) continue
                        val c = rgba.getPixel(px, py)
                        rs += SkColorGetR(c)
                        gs += SkColorGetG(c)
                        bs += SkColorGetB(c)
                        n++
                    }
                }
                val r = rs.toFloat() / n
                val g = gs.toFloat() / n
                val b = bs.toFloat() / n
                uPlane.setPixel(cx, cy, SkColorSetARGB(rgbToU(r, g, b, colorSpace), 0, 0, 0))
                vPlane.setPixel(cx, cy, SkColorSetARGB(rgbToV(r, g, b, colorSpace), 0, 0, 0))
            }
        }

        val planes = buildList {
            add(SkImage.Make(yPlane))
            add(SkImage.Make(uPlane))
            add(SkImage.Make(vPlane))
            if (hasAlpha) add(SkImage.Make(aPlane!!))
        }
        return YUVAPlanesAsA8(planes, info)
    }

    private fun SkImage.readAsBitmap(): SkBitmap {
        val info = org.skia.foundation.SkImageInfo.MakeN32(width, height, org.skia.foundation.SkAlphaType.kUnpremul, SkColorSpace.makeSRGB())
        val rowBytes = info.minRowBytes()
        val buf = java.nio.ByteBuffer.allocate(rowBytes * height).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        check(readPixels(info, buf, rowBytes, 0, 0)) { "readPixels failed for MakeYUVAPlanesAsA8 source" }
        val bm = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        buf.rewind()
        for (y in 0 until height) {
            for (x in 0 until width) {
                bm.setPixel(x, y, buf.int)
            }
        }
        return bm
    }

    private fun hasAlphaChannel(bitmap: SkBitmap): Boolean {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (SkColorGetA(bitmap.getPixel(x, y)) != 255) return true
            }
        }
        return false
    }

    private fun toSkSubsampling(subsampling: YUVSubsampling): SkYUVAInfo.Subsampling = when (subsampling) {
        YUVSubsampling.k444 -> SkYUVAInfo.Subsampling.k444
        YUVSubsampling.k422 -> SkYUVAInfo.Subsampling.k422
        YUVSubsampling.k420 -> SkYUVAInfo.Subsampling.k420
    }

    private fun rgbToY(c: Int, cs: SkYUVAInfo.YUVColorSpace): Int {
        val r = SkColorGetR(c).toFloat()
        val g = SkColorGetG(c).toFloat()
        val b = SkColorGetB(c).toFloat()
        val y = when (cs) {
            SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace -> 0.299f * r + 0.587f * g + 0.114f * b
            SkYUVAInfo.YUVColorSpace.kRec601_Limited_YUV_ColorSpace -> 16f + (65.481f * r + 128.553f * g + 24.966f * b) / 255f
            else -> 16f + (65.738f * r + 129.057f * g + 25.064f * b) / 255f
        }
        return y.toInt().coerceIn(0, 255)
    }

    private fun rgbToU(r: Float, g: Float, b: Float, cs: SkYUVAInfo.YUVColorSpace): Int {
        val u = when (cs) {
            SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace -> 128f + (-0.168736f * r - 0.331264f * g + 0.5f * b)
            SkYUVAInfo.YUVColorSpace.kRec601_Limited_YUV_ColorSpace -> 128f + (-37.797f * r - 74.203f * g + 112.0f * b) / 255f
            else -> 128f + (-37.945f * r - 74.494f * g + 112.439f * b) / 255f
        }
        return u.toInt().coerceIn(0, 255)
    }

    private fun rgbToV(r: Float, g: Float, b: Float, cs: SkYUVAInfo.YUVColorSpace): Int {
        val v = when (cs) {
            SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace -> 128f + (0.5f * r - 0.418688f * g - 0.081312f * b)
            SkYUVAInfo.YUVColorSpace.kRec601_Limited_YUV_ColorSpace -> 128f + (112.0f * r - 93.786f * g - 18.214f * b) / 255f
            else -> 128f + (112.439f * r - 94.154f * g - 18.285f * b) / 255f
        }
        return v.toInt().coerceIn(0, 255)
    }
}
