package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkCompressedDataUtils
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextureCompressionType

/**
 * Port of Skia's
 * [`gm/bc1_transparency.cpp`](https://github.com/google/skia/blob/main/gm/bc1_transparency.cpp).
 *
 * Exercises BC1 (DXT1) per-block transparency : builds a hand-rolled
 * 16×8 BC1 payload whose top 4 rows are "transparent" blocks
 * (`fColor0 <= fColor1`, transparent black in code 3) and whose bottom
 * 4 rows are "opaque" blocks (`fColor0 > fColor1`, light-grey in code 3).
 * The same payload is uploaded twice — once as
 * [SkTextureCompressionType.kBC1_RGB8_UNORM] (transparent code rendered
 * as opaque black) and once as
 * [SkTextureCompressionType.kBC1_RGBA8_UNORM] (transparent code lets
 * the green background show through).
 *
 * **Ganesh-only paths skipped.** Upstream branches on `SK_GANESH` to
 * upload the BC1 payload as a real GPU texture and stamps an outline
 * around the raster-fallback strip. `:kanvas-skia` is raster-only, so
 * we follow the pure `SkImages::RasterFromCompressedTextureData` path
 * and always stamp the red outline (mirrors upstream's `!isCompressed`
 * branch when no GPU context is available).
 *
 * ## Port status
 *
 * Body fully ported against the freshly-introduced
 * [SkImages.RasterFromCompressedTextureData] /
 * [SkCompressedDataUtils.SkCompressedDataSize] /
 * [SkTextureCompressionType] surface (all three resolve to
 * `TODO("STUB.COMPRESSED_TEXTURES")` at runtime — the kanvas-skia
 * raster backend has no block-decompression routine yet). The matching
 * `BC1TransparencyTest` is `@Disabled("STUB.COMPRESSED_TEXTURES")` until
 * a BC1 decode lands.
 */
public class BC1TransparencyGM : GM() {

    init {
        setBGColor(SK_ColorGREEN)
    }

    override fun getName(): String = "bc1_transparency"

    override fun getISize(): SkISize =
        SkISize.Make(kImgWidth + 2 * kPad, 2 * kImgHeight + 3 * kPad)

    private var rgbImage: SkImage? = null
    private var rgbaImage: SkImage? = null

    private fun ensureImages() {
        if (rgbImage != null && rgbaImage != null) return
        val bc1Data = makeCompressedData()
        rgbImage = SkImages.RasterFromCompressedTextureData(
            bc1Data, kImgWidth, kImgHeight, SkTextureCompressionType.kBC1_RGB8_UNORM,
        )
        rgbaImage = SkImages.RasterFromCompressedTextureData(
            bc1Data, kImgWidth, kImgHeight, SkTextureCompressionType.kBC1_RGBA8_UNORM,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        ensureImages()
        drawImage(canvas, rgbImage, kPad, kPad)
        drawImage(canvas, rgbaImage, kPad, 2 * kPad + kImgHeight)
    }

    /**
     * Mirrors upstream's `draw_image(canvas, image, x, y)` — paints
     * [image] at `(x, y)` and stamps a red 2-px stroked outline around
     * it. Upstream only outlines the raster-fallback path
     * (`!isCompressed`) ; `:kanvas-skia` has no GPU compressed-texture
     * path at all, so the outline is always stamped.
     */
    private fun drawImage(canvas: SkCanvas, image: SkImage?, x: Int, y: Int) {
        if (image != null) {
            canvas.drawImage(image, x.toFloat(), y.toFloat())
        }

        val r = SkRect.MakeXYWH(
            x.toFloat(), y.toFloat(), kImgWidth.toFloat(), kImgHeight.toFloat(),
        )
        r.outset(1f, 1f)

        val redStroke = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }
        canvas.drawRect(r, redStroke)
    }

    /**
     * Mirrors upstream's `make_compressed_data()` — synthesises a 16×8
     * BC1 payload (= 8 blocks of 4×4 = 4 blocks across × 2 blocks down)
     * whose top row of blocks is "transparent" and whose bottom row is
     * "opaque" (see [createBC1Block]'s KDoc for the per-block encoding).
     */
    private fun makeCompressedData(): SkData {
        val dim = SkISize.Make(kImgWidth, kImgHeight)
        // `SkCompressedDataSize` is a STUB ; the size we'd want here is
        // `numXBlocks * numYBlocks * 8` bytes (BC1 packs 8 bytes per
        // 4×4 block). Wired-through to keep the surface honest — the
        // call will TODO() at runtime, which is fine because the test
        // is @Disabled.
        val totalSize = SkCompressedDataUtils.SkCompressedDataSize(
            SkTextureCompressionType.kBC1_RGB8_UNORM, dim, null, false,
        )

        // Allocate the byte buffer we'd hand off to the GPU. BC1 blocks
        // are 8 bytes : 2 bytes for color0, 2 for color1, 4 for the
        // 16×2-bit index table.
        val numXBlocks = num4x4Blocks(kImgWidth)
        val numYBlocks = num4x4Blocks(kImgHeight)
        val bytes = ByteArray(totalSize.toInt())

        val transBlock = createBC1Block(transparent = true)
        val opaqueBlock = createBC1Block(transparent = false)

        for (y in 0 until numYBlocks) {
            for (x in 0 until numXBlocks) {
                val block = if (y < numYBlocks / 2) transBlock else opaqueBlock
                val off = (y * numXBlocks + x) * BC1_BLOCK_SIZE
                writeBlock(bytes, off, block)
            }
        }

        return SkData.MakeWithCopy(bytes)
    }

    /**
     * BC1 block layout :
     *
     * ```
     * struct BC1Block {
     *   uint16_t fColor0;   // little-endian RGB565
     *   uint16_t fColor1;   // little-endian RGB565
     *   uint32_t fIndices;  // 16 × 2-bit indices into the 4-colour palette
     * };
     * ```
     *
     * **Per-block transparency rule.** If `fColor0 <= fColor1` the
     * block is "transparent" — code 3 in the palette decodes to
     * `(0, 0, 0, 0)` for the BC1A path and to opaque black for the
     * plain BC1 path. If `fColor0 > fColor1` the block is opaque and
     * code 3 decodes to the 1/3-mix between color0 and color1.
     *
     * The GM picks endpoints + indices so the rendered rows read,
     * left-to-right :
     *  - Transparent block : opaque black, mid-grey, **transparent
     *    black (RGBA) or opaque black (RGB)**, white.
     *  - Opaque block : opaque black, dark grey, light grey, white.
     */
    private fun createBC1Block(transparent: Boolean): BC1Block {
        val byte: Int
        val color0: Int
        val color1: Int
        if (transparent) {
            color0 = to565(SK_ColorBLACK)
            color1 = to565(SK_ColorWHITE)
            check(color0 <= color1) { "transparent block requires fColor0 <= fColor1" }
            // opaque black (col0=0x0), medium grey (col2=0x2),
            // transparent black (col3=0x3), white (col1=0x1).
            byte = (0x0 shl 0) or (0x2 shl 2) or (0x3 shl 4) or (0x1 shl 6)
        } else {
            color0 = to565(SK_ColorWHITE)
            color1 = to565(SK_ColorBLACK)
            check(color0 > color1) { "opaque block requires fColor0 > fColor1" }
            // opaque black (col1=0x1), dark grey (col3=0x3),
            // light grey (col2=0x2), white (col0=0x0).
            byte = (0x1 shl 0) or (0x3 shl 2) or (0x2 shl 4) or (0x0 shl 6)
        }
        val indices = (byte shl 24) or (byte shl 16) or (byte shl 8) or byte
        return BC1Block(color0, color1, indices)
    }

    /** Write a [BC1Block] into [dst] starting at [off] in little-endian. */
    private fun writeBlock(dst: ByteArray, off: Int, block: BC1Block) {
        dst[off + 0] = (block.color0 and 0xFF).toByte()
        dst[off + 1] = ((block.color0 ushr 8) and 0xFF).toByte()
        dst[off + 2] = (block.color1 and 0xFF).toByte()
        dst[off + 3] = ((block.color1 ushr 8) and 0xFF).toByte()
        dst[off + 4] = (block.indices and 0xFF).toByte()
        dst[off + 5] = ((block.indices ushr 8) and 0xFF).toByte()
        dst[off + 6] = ((block.indices ushr 16) and 0xFF).toByte()
        dst[off + 7] = ((block.indices ushr 24) and 0xFF).toByte()
    }

    private data class BC1Block(val color0: Int, val color1: Int, val indices: Int)

    private companion object {
        const val kImgWidth = 16
        const val kImgHeight = 8
        const val kPad = 4
        const val BC1_BLOCK_SIZE = 8

        /** Mirrors upstream's `num_4x4_blocks` — rounds up to the nearest 4×4 block grid. */
        private fun num4x4Blocks(size: Int): Int = ((size + 3) and 3.inv()) shr 2

        /**
         * Quantise an opaque [SkColor] to an RGB565 (5-6-5) packed word.
         * Mirrors upstream's `to565(SkColor)`. Identical math to
         * `colorToRGB565` from `:math` but returns a raw 16-bit word
         * instead of an ARGB Int.
         */
        private fun to565(col: SkColor): Int {
            val r5 = mulDiv255Round(31, SkColorGetR(col))
            val g6 = mulDiv255Round(63, SkColorGetG(col))
            val b5 = mulDiv255Round(31, SkColorGetB(col))
            return ((r5 and 0x1F) shl 11) or ((g6 and 0x3F) shl 5) or (b5 and 0x1F)
        }

        /**
         * Mirrors Skia's `SkMulDiv255Round(a, b) = (a * b + 127) / 255`.
         * Inlined here ; consider promoting to `:math` if a second
         * caller appears.
         */
        private fun mulDiv255Round(a: Int, b: Int): Int = (a * b + 127) / 255
    }
}
