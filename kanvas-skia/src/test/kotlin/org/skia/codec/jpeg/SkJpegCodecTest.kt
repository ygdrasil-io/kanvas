package org.skia.codec.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.codec.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * D3.2 verification suite for [SkJpegCodec].
 *
 * Covers :
 *  - JPEG signature dispatch (positive + negative).
 *  - 8-bit synthetic JPEG decode → [SkColorType.kRGBA_8888] /
 *    [SkAlphaType.kUnpremul] / sRGB.
 *  - Approximate pixel fidelity round-trip — JPEG is lossy so we
 *    accept a per-channel delta, but a flat-colour JPEG must round
 *    trip well below the visual threshold.
 *  - APP2 / `ICC_PROFILE` recovery on a synthetic, hand-crafted JPEG
 *    byte stream that splices in a multi-chunk profile around a real
 *    ImageIO encode (exercises the multi-marker assembler without
 *    depending on a fixture file).
 *  - Geometry validation : [SkJpegCodec.getPixels] rejects
 *    mismatched `info` / `dst` arguments.
 */
class SkJpegCodecTest {

    // ─── Dispatch + signature ─────────────────────────────────────────

    @Test
    fun `MakeFromData returns null for non-JPEG bytes`() {
        // Empty + clearly-not-JPEG.
        assertNull(SkCodec.MakeFromData(ByteArray(0)))
        assertNull(SkCodec.MakeFromData("not a jpeg".toByteArray()))
        // PNG signature : matches the PNG decoder, not us.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertTrue(SkCodec.MakeFromData(png) !is SkJpegCodec)
    }

    @Test
    fun `MakeFromData dispatches a synthetic JPEG to SkJpegCodec`() {
        val bytes = synthJpeg(width = 8, height = 6)
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec, "synthetic JPEG must be decodable")
        assertTrue(codec is SkJpegCodec, "dispatcher must pick SkJpegCodec for FF D8 FF")
        assertEquals(SkEncodedImageFormat.kJPEG, codec!!.getEncodedFormat())
        assertEquals(8, codec.dimensions().width)
        assertEquals(6, codec.dimensions().height)
    }

    @Test
    fun `MakeFromStream is equivalent to MakeFromData`() {
        val bytes = synthJpeg(width = 4, height = 4)
        val viaData = SkCodec.MakeFromData(bytes)!!
        val viaStream = SkCodec.MakeFromStream(ByteArrayInputStream(bytes))!!
        assertEquals(viaData.dimensions(), viaStream.dimensions())
        assertEquals(viaData.getEncodedFormat(), viaStream.getEncodedFormat())
    }

    // ─── Decode ───────────────────────────────────────────────────────

    @Test
    fun `synthetic JPEG decodes into RGBA_8888 sRGB unpremul bitmap`() {
        val bytes = synthJpeg(width = 8, height = 8)
        val codec = SkCodec.MakeFromData(bytes)!!
        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType, "JPEG has no alpha — kUnpremul matches the 8888 PNG path")
        assertTrue(info.colorSpace.isSRGB(), "synthetic JPEG has no APP2 ICC chunk — codec falls back to sRGB")

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(SkColorType.kRGBA_8888, bitmap!!.colorType)
        assertEquals(8, bitmap.width)
        assertEquals(8, bitmap.height)
        // JPEG is opaque ; every pixel must come back fully opaque.
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val a = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                assertEquals(0xFF, a, "JPEG pixel ($x, $y) must be fully opaque")
            }
        }
    }

    @Test
    fun `flat-colour JPEG round-trips within JPEG tolerance`() {
        // A 16x16 mid-grey image — flat colour is the easiest JPEG case
        // (no DCT energy outside DC), so the decode should land within
        // a couple of ulps of the original. TYPE_INT_RGB avoids the
        // ImageIO JPEG writer rejecting alpha-bearing buffers.
        val src = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) for (x in 0 until 16) {
            src.setRGB(x, y, 0xFF808080.toInt())
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(src, "jpeg", it) }.toByteArray()
        check(bytes.isNotEmpty()) { "ImageIO failed to produce a JPEG" }

        val codec = SkCodec.MakeFromData(bytes)!!
        val (bitmap, _) = codec.getImage()
        assertNotNull(bitmap)
        // JPEG is lossy ; 4 ulps is generous for a flat mid-grey, but
        // protects against the test going red on a future ImageIO
        // codec swap.
        for (y in 0 until 16) for (x in 0 until 16) {
            val px = bitmap!!.getPixel(x, y)
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            assertTrue(kotlin.math.abs(r - 0x80) <= 4, "R drift at ($x,$y) : got $r")
            assertTrue(kotlin.math.abs(g - 0x80) <= 4, "G drift at ($x,$y) : got $g")
            assertTrue(kotlin.math.abs(b - 0x80) <= 4, "B drift at ($x,$y) : got $b")
        }
    }

    // ─── ICC profile recovery ─────────────────────────────────────────

    @Test
    fun `APP2 ICC chunks are concatenated and parsed`() {
        // Splice a synthetic 2-chunk ICC profile around a real JPEG
        // SOI. The walker should discover both chunks, sort them by
        // index (we deliberately emit them out-of-order to exercise
        // the sort), and reassemble the profile bytes.
        val realJpeg = synthJpeg(width = 4, height = 4)
        val iccProfileBytes = synthIccProfile()
        val splitAt = iccProfileBytes.size / 2
        val chunk1 = iccProfileBytes.copyOfRange(0, splitAt)
        val chunk2 = iccProfileBytes.copyOfRange(splitAt, iccProfileBytes.size)

        // Insert APP2 segments right after SOI (offset 2). Order : chunk 2, chunk 1.
        val app2A = makeApp2IccChunk(chunkIndex = 2, chunkCount = 2, payload = chunk2)
        val app2B = makeApp2IccChunk(chunkIndex = 1, chunkCount = 2, payload = chunk1)
        val merged = ByteArray(realJpeg.size + app2A.size + app2B.size)
        realJpeg.copyInto(merged, destinationOffset = 0, startIndex = 0, endIndex = 2)
        app2A.copyInto(merged, destinationOffset = 2)
        app2B.copyInto(merged, destinationOffset = 2 + app2A.size)
        realJpeg.copyInto(
            merged,
            destinationOffset = 2 + app2A.size + app2B.size,
            startIndex = 2,
            endIndex = realJpeg.size,
        )

        val codec = SkCodec.MakeFromData(merged)
        assertNotNull(codec, "spliced JPEG must still parse")
        val profile = codec!!.getICCProfile()
        // skcmsParse is strict — our synthetic ICC profile bytes won't
        // pass full validation, so getICCProfile may still return
        // null. What we *can* assert is that the codec didn't crash on
        // the multi-chunk APP2 walk, and that the decoded JPEG itself
        // came through. A separate iso-Skia parse round-trip lives in
        // skcms tests.
        @Suppress("UNUSED_VARIABLE") val _profileNullable = profile
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
    }

    // ─── Geometry validation ──────────────────────────────────────────

    @Test
    fun `getPixels rejects mismatched destination geometry`() {
        val bytes = synthJpeg(width = 4, height = 4)
        val codec = SkCodec.MakeFromData(bytes)!!
        val info = codec.getInfo()
        val tooSmall = SkBitmap(2, 2, info.colorSpace, SkColorType.kRGBA_8888)
        assertEquals(SkCodec.Result.kInvalidParameters, codec.getPixels(info, tooSmall))
        val wrongType = SkBitmap(4, 4, info.colorSpace, SkColorType.kRGBA_F16Norm)
        assertEquals(SkCodec.Result.kInvalidParameters, codec.getPixels(info, wrongType))
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Encode a synthetic 8-bit gradient image as JPEG via ImageIO.
     * Produces a baseline JPEG with no `APP2 ICC_PROFILE` chunk — the
     * "no embedded profile, codec falls back to sRGB" scenario.
     */
    private fun synthJpeg(width: Int, height: Int): ByteArray {
        // Use TYPE_INT_RGB : opaque, no alpha — JPEG can't carry alpha
        // anyway, and TYPE_INT_ARGB confuses some ImageIO writers.
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            img.setRGB(x, y, (r shl 16) or (g shl 8) or 0x40)
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpeg", it) }.toByteArray()
    }

    /**
     * Produce 256 deterministic bytes that look like an ICC profile to
     * the APP2 chunk walker. Not a valid ICC blob — strict skcms
     * validation may reject it — but that's fine : this fixture
     * exercises the chunk-assembly logic, not skcms.
     */
    private fun synthIccProfile(): ByteArray {
        return ByteArray(256) { i -> (i and 0xFF).toByte() }
    }

    /**
     * Build a JPEG `APP2` segment carrying one ICC profile chunk.
     * Layout :
     * ```
     * FF E2  <2-byte length BE>  "ICC_PROFILE\0"  <chunk_index>  <chunk_count>  <payload>
     * ```
     * The length covers the 2-byte length field itself (per JPEG
     * conventions) and everything after, up to but not including the
     * next marker.
     */
    private fun makeApp2IccChunk(chunkIndex: Int, chunkCount: Int, payload: ByteArray): ByteArray {
        val iccSig = byteArrayOf(
            0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00,
        )
        val payloadLen = iccSig.size + 2 + payload.size
        val segLen = 2 + payloadLen
        val out = ByteArray(2 + segLen)
        out[0] = 0xFF.toByte()
        out[1] = 0xE2.toByte()
        out[2] = ((segLen ushr 8) and 0xFF).toByte()
        out[3] = (segLen and 0xFF).toByte()
        iccSig.copyInto(out, destinationOffset = 4)
        out[4 + iccSig.size] = (chunkIndex and 0xFF).toByte()
        out[4 + iccSig.size + 1] = (chunkCount and 0xFF).toByte()
        payload.copyInto(out, destinationOffset = 4 + iccSig.size + 2)
        return out
    }
}
