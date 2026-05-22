package org.skia.foundation

import org.graphiks.math.SkISize

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
        TODO(
            "STUB.COMPRESSED_TEXTURES: SkCompressedDataSize(${compression}, " +
                "${dimensions.width}x${dimensions.height}, mipMapped=$mipMapped) " +
                "not implemented — kanvas-skia raster backend lacks the block-" +
                "compressed texture pipeline (see CompressedTexturesGM / " +
                "BC1TransparencyGM)."
        )
    }

    /**
     * Mirrors Skia's `SkCompressedBlockWidth(compression)` — for all
     * currently-enumerated formats the answer is 4 (BC1 and ETC2 both
     * use 4×4 blocks). Exposed for parity with the upstream helper ;
     * callers normally hard-code `4`.
     */
    public fun SkCompressedBlockWidth(compression: SkTextureCompressionType): Int {
        TODO(
            "STUB.COMPRESSED_TEXTURES: SkCompressedBlockWidth($compression) not " +
                "implemented — see SkCompressedDataSize."
        )
    }

    /**
     * Mirrors Skia's `SkCompressedBlockHeight(compression)` — symmetric
     * with [SkCompressedBlockWidth].
     */
    public fun SkCompressedBlockHeight(compression: SkTextureCompressionType): Int {
        TODO(
            "STUB.COMPRESSED_TEXTURES: SkCompressedBlockHeight($compression) not " +
                "implemented — see SkCompressedDataSize."
        )
    }
}
