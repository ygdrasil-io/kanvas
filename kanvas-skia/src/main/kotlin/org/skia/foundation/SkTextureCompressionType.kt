package org.skia.foundation

/**
 * Mirrors Skia's `SkTextureCompressionType` enum
 * (`include/core/SkTextureCompressionType.h`).
 *
 * Identifies the block-compression format used by a compressed-texture
 * payload (KTX, DDS, …). In upstream Skia these drive GPU texture-upload
 * paths (`SkImages::TextureFromCompressedTextureData`,
 * `SkImages::RasterFromCompressedTextureData`).
 *
 * In `:kanvas-skia` the compressed-texture upload path is **not yet
 * implemented** (see `STUB.COMPRESSED_TEXTURES`). This enum is
 * introduced so that the `exoticformats` GM can reference the type and
 * the compile contract holds ; the runtime path throws.
 */
public enum class SkTextureCompressionType {
    kNone,

    /** ETC1 RGB8 (a.k.a. ETC2 RGB8_UNORM — ETC2 is a superset of ETC1). */
    kETC2_RGB8_UNORM,

    /** S3TC / DXT1 RGB (BC1 without 1-bit alpha). */
    kBC1_RGB8_UNORM,

    /** S3TC / DXT1 RGBA (BC1 with 1-bit alpha punchthrough). */
    kBC1_RGBA8_UNORM,
}
