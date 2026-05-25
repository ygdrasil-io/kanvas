package org.skia.foundation

import org.graphiks.math.SkISize
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR

/**
 * Mirrors Skia's
 * [`SkTextureCompressionType`](https://github.com/google/skia/blob/main/include/core/SkTextureCompressionType.h).
 *
 * Enumerates the GPU block-compressed texture formats Skia knows how to
 * upload (or, for the raster path, synthesise as decompressed bitmaps).
 * The set matches upstream one-for-one — anything that lands here can
 * appear in `SkImages::RasterFromCompressedTextureData(...)` or in the
 * Ganesh GPU upload path.
 *
 * **Status.** Flag-planting only — `:kanvas-skia` does not yet implement
 * any block-decompression routine. The matching [SkImages]
 * `RasterFromCompressedTextureData` factory + [SkCompressedDataUtils]
 * helpers throw at runtime (`STUB.COMPRESSED_TEXTURES`). Wired up so
 * upstream GMs (`bc1_transparency`, `compressed_textures`, …) compile
 * and stay `@Disabled` with the precise reason.
 */
public enum class SkTextureCompressionType {
    /** No compression — sentinel used as a "this image isn't compressed" marker. */
    kNone,

    /**
     * ETC2 RGB8 (a.k.a. `GL_COMPRESSED_RGB8_ETC2`). 4×4 block, 64 bits
     * per block (4 bpp).
     */
    kETC2_RGB8_UNORM,

    /**
     * BC1 RGB (a.k.a. DXT1 — `GL_COMPRESSED_RGB_S3TC_DXT1_EXT`). 4×4
     * block, 64 bits per block (4 bpp). The alpha bit in BC1 is ignored
     * — transparent pixels render as the second endpoint colour.
     */
    kBC1_RGB8_UNORM,

    /**
     * BC1 RGBA (a.k.a. DXT1A — `GL_COMPRESSED_RGBA_S3TC_DXT1_EXT`).
     * Same 64-bit block layout as [kBC1_RGB8_UNORM] but honours the
     * 1-bit punch-through alpha (the "transparent black" code in the
     * 4-colour palette).
     */
    kBC1_RGBA8_UNORM,
}

/**
 * Mirrors Skia's `SkTextureCompressionType_ETC1_RGB8` legacy alias —
 * upstream collapses ETC1 onto [SkTextureCompressionType.kETC2_RGB8_UNORM]
 * (ETC2 is backwards-compatible with ETC1 for the RGB8 subset).
 */
public val SkTextureCompressionType.kETC1_RGB8_UNORM: SkTextureCompressionType
    get() = SkTextureCompressionType.kETC2_RGB8_UNORM

/**
 * Mirrors Skia's [`SkCompressedDataUtils`](https://github.com/google/skia/blob/main/src/core/SkCompressedDataUtils.h)
 * — helpers that report block geometry / payload size for a given
 * [SkTextureCompressionType].
 *
 * **Status.** Flag-planting STUB — every method throws
 * `NotImplementedError("STUB.COMPRESSED_TEXTURES: …")`. Wire-up only,
 * so upstream GMs compile against the surface and tests stay `@Disabled`
 * with the precise reason.
 */
public object SkCompressedDataUtils {

    /**
     * Mirrors Skia's `SkCompressedDataSize(compression, dimensions,
     * mipMapOffsetsAndSizes, mipMapped)` — total byte count needed to
     * store [dimensions]-sized image in the [compression] block format,
     * optionally including a mip pyramid (mip-offset table written into
     * [mipMapOffsetsAndSizes] when supplied).
     *
     * `:kanvas-skia` has no compressed-texture path yet ; this is wired
     * as a `TODO("STUB.COMPRESSED_TEXTURES")` so callers (e.g. the
     * `bc1_transparency` GM port) compile against the live surface.
     */
    public fun SkCompressedDataSize(
        compression: SkTextureCompressionType,
        dimensions: SkISize,
        mipMapOffsetsAndSizes: IntArray? = null,
        mipMapped: Boolean = false,
    ): Long {
        if (dimensions.width <= 0 || dimensions.height <= 0) return 0L
        val blockBytes = when (compression) {
            SkTextureCompressionType.kBC1_RGB8_UNORM,
            SkTextureCompressionType.kBC1_RGBA8_UNORM,
            SkTextureCompressionType.kETC2_RGB8_UNORM -> 8
            SkTextureCompressionType.kNone -> return 0L
        }

        var total = 0L
        var level = 0
        var w = dimensions.width
        var h = dimensions.height
        while (true) {
            val bw = (w + 3) / 4
            val bh = (h + 3) / 4
            val levelSize = bw.toLong() * bh.toLong() * blockBytes.toLong()
            if (mipMapOffsetsAndSizes != null && level * 2 + 1 < mipMapOffsetsAndSizes.size) {
                mipMapOffsetsAndSizes[level * 2] = total.toInt()
                mipMapOffsetsAndSizes[level * 2 + 1] = levelSize.toInt()
            }
            total += levelSize
            if (!mipMapped || (w == 1 && h == 1)) break
            w = maxOf(1, w / 2)
            h = maxOf(1, h / 2)
            level++
        }
        return total
    }

    /**
     * Mirrors Skia's `SkCompressedBlockWidth(compression)` — for all
     * currently-enumerated formats the answer is 4 (BC1 and ETC2 both
     * use 4×4 blocks). Exposed for parity with the upstream helper ;
     * callers normally hard-code `4`.
     */
    public fun SkCompressedBlockWidth(compression: SkTextureCompressionType): Int {
        return when (compression) {
            SkTextureCompressionType.kBC1_RGB8_UNORM,
            SkTextureCompressionType.kBC1_RGBA8_UNORM,
            SkTextureCompressionType.kETC2_RGB8_UNORM -> 4
            SkTextureCompressionType.kNone -> 0
        }
    }

    /**
     * Mirrors Skia's `SkCompressedBlockHeight(compression)` — symmetric
     * with [SkCompressedBlockWidth].
     */
    public fun SkCompressedBlockHeight(compression: SkTextureCompressionType): Int {
        return SkCompressedBlockWidth(compression)
    }

    /**
     * Mirrors upstream's `etc1_encode_image(src, w, h, pixelSize,
     * rowBytes, dst)` (`third_party/etc1/etc1.h`) — encodes [srcBitmap]
     * (which must be [SkColorType.kRGB_565] + opaque) into a sequence
     * of 64-bit ETC1 / ETC2-RGB8 blocks written to [dst] starting at
     * [dstOffset]. The destination block grid is rounded up to the
     * next 4×4 multiple so a 13×7 source still produces a 16×8 grid.
     *
     * **Status.** Flag-planting STUB — throws
     * `NotImplementedError("STUB.COMPRESSED_TEXTURES")`. Used by the
     * `compressed_textures` GM port to mirror the upstream encode
     * path ; the actual ETC1 / ETC2 block-builder isn't ported yet.
     */
    public fun Etc1EncodeImage(
        srcBitmap: SkBitmap,
        dst: ByteArray,
        dstOffset: Int,
    ) {
        throw NotImplementedError(
            "STUB.COMPRESSED_TEXTURES.ETC: SkCompressedDataUtils.Etc1EncodeImage(" +
                "${srcBitmap.width}x${srcBitmap.height} ${srcBitmap.colorType}, " +
                "dst[${dst.size} bytes], offset=$dstOffset) not implemented."
        )
    }

    /**
     * Mirrors upstream's
     * [`sk_gpu_test::TwoColorBC1Compress(pixmap, otherColor, dst)`](https://github.com/google/skia/blob/main/tools/gpu/CompressedTexture.h)
     * — encodes [srcBitmap] (which must be [SkColorType.kRGBA_8888])
     * into a sequence of 64-bit BC1 blocks written to [dst] starting at
     * [dstOffset]. The "two colour" variant assumes the source is
     * either black + [otherColor] (opaque pixels) or transparent +
     * [otherColor] (transparent pixels — fed to the BC1A
     * punch-through-alpha path).
     *
     * **Status.** Flag-planting STUB — throws
     * `NotImplementedError("STUB.COMPRESSED_TEXTURES")`. Used by the
     * `compressed_textures` GM port to mirror the upstream encode
     * path ; the actual BC1 block-builder isn't ported yet (BC1's
     * static block layout *is* honoured by the hand-rolled byte stream
     * in `BC1TransparencyGM`, but a generic-input compressor doesn't
     * exist).
     */
    public fun TwoColorBC1Compress(
        srcBitmap: SkBitmap,
        otherColor: Int,
        dst: ByteArray,
        dstOffset: Int,
    ) {
        require(dstOffset >= 0 && dstOffset < dst.size) { "dstOffset out of range: $dstOffset" }
        val blockWidth = (srcBitmap.width + 3) / 4
        val blockHeight = (srcBitmap.height + 3) / 4
        val needed = blockWidth * blockHeight * 8
        require(dstOffset + needed <= dst.size) {
            "destination too small for BC1 payload: need=${dstOffset + needed}, have=${dst.size}"
        }

        val other565 = to565(otherColor)
        val black565 = 0
        var write = dstOffset
        for (by in 0 until blockHeight) {
            for (bx in 0 until blockWidth) {
                var hasTransparent = false
                var indices = 0
                var bitPos = 0
                for (ly in 0 until 4) {
                    for (lx in 0 until 4) {
                        val x = bx * 4 + lx
                        val y = by * 4 + ly
                        val c = if (x < srcBitmap.width && y < srcBitmap.height) srcBitmap.getPixel(x, y) else 0
                        val a = SkColorGetA(c)
                        val idx = if (a < 128) {
                            hasTransparent = true
                            3
                        } else {
                            val isOther = approxSameRgb(c, otherColor)
                            if (isOther) 1 else 0
                        }
                        indices = indices or (idx shl bitPos)
                        bitPos += 2
                    }
                }

                val c0: Int
                val c1: Int
                if (hasTransparent) {
                    c0 = minOf(black565, other565)
                    c1 = maxOf(black565, other565)
                } else {
                    c0 = maxOf(black565, other565)
                    c1 = minOf(black565, other565)
                }
                put16(dst, write, c0)
                put16(dst, write + 2, c1)
                put32(dst, write + 4, indices)
                write += 8
            }
        }
    }

    private fun approxSameRgb(a: Int, b: Int): Boolean {
        val dr = kotlin.math.abs(SkColorGetR(a) - SkColorGetR(b))
        val dg = kotlin.math.abs(SkColorGetG(a) - SkColorGetG(b))
        val db = kotlin.math.abs(SkColorGetB(a) - SkColorGetB(b))
        return dr <= 2 && dg <= 2 && db <= 2
    }

    private fun to565(c: Int): Int {
        val r5 = (SkColorGetR(c) * 31 + 127) / 255
        val g6 = (SkColorGetG(c) * 63 + 127) / 255
        val b5 = (SkColorGetB(c) * 31 + 127) / 255
        return (r5 shl 11) or (g6 shl 5) or b5
    }

    private fun put16(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun put32(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v ushr 8) and 0xFF).toByte()
        dst[off + 2] = ((v ushr 16) and 0xFF).toByte()
        dst[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}
