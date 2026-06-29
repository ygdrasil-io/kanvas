package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkEncodedImageFormat
import java.io.ByteArrayInputStream

/**
 * R3.5 verification suite for [AndroidCodec]. Covers :
 *  - factory dispatch (MakeFromData, MakeFromStream, SkData overload) ;
 *  - computeSampleSize round-trips with getSampledDimensions ;
 *  - getSampledDimensions(2) returns half of the source ;
 *  - getSupportedSubset clamps + rejects empty intersections.
 */
class AndroidCodecTest {

    // ─── Factories ────────────────────────────────────────────────────

    @Test
    fun `MakeFromData returns non-null for a small PNG`() {
        val bytes = synthPng(width = 16, height = 12)
        val codec = AndroidCodec.MakeFromData(bytes)
        assertNotNull(codec, "synthetic 16x12 PNG must be decodable through AndroidCodec")
        assertEquals(16, codec!!.getInfo().width)
        assertEquals(12, codec.getInfo().height)
        assertEquals(SkEncodedImageFormat.kPNG, codec.getEncodedFormat())
    }

    @Test
    fun `MakeFromData returns null for non-image bytes`() {
        assertNull(AndroidCodec.MakeFromData(ByteArray(0)))
        assertNull(AndroidCodec.MakeFromData("not an image".toByteArray()))
    }

    @Test
    fun `MakeFromStream is equivalent to MakeFromData`() {
        val bytes = synthPng(width = 8, height = 8)
        val viaData = AndroidCodec.MakeFromData(bytes)!!
        val viaStream = AndroidCodec.MakeFromStream(ByteArrayInputStream(bytes))!!
        assertEquals(viaData.getInfo().width, viaStream.getInfo().width)
        assertEquals(viaData.getInfo().height, viaStream.getInfo().height)
    }

    @Test
    fun `MakeFromData accepts SkData overload`() {
        val bytes = synthPng(width = 4, height = 4)
        val data = SkData.MakeWithCopy(bytes)
        val codec = AndroidCodec.MakeFromData(data)
        assertNotNull(codec)
        assertEquals(4, codec!!.getInfo().width)
    }

    @Test
    fun `MakeFromCodec wraps an existing codec`() {
        val bytes = synthPng(width = 2, height = 2)
        val base = Codec.MakeFromData(bytes)!!
        val android = AndroidCodec.MakeFromCodec(base)
        assertEquals(base.getInfo().width, android.getInfo().width)
        assertEquals(base.getEncodedFormat(), android.getEncodedFormat())
        // The wrapped codec is reachable.
        assertEquals(base, android.codec())
    }

    // ─── computeSampleSize / getSampledDimensions ─────────────────────

    @Test
    fun `getSampledDimensions returns half at sampleSize=2`() {
        val codec = AndroidCodec.MakeFromData(synthPng(64, 32))!!
        val sampled = codec.getSampledDimensions(2)
        assertEquals(32, sampled.width)
        assertEquals(16, sampled.height)
    }

    @Test
    fun `getSampledDimensions returns full size at sampleSize=1`() {
        val codec = AndroidCodec.MakeFromData(synthPng(64, 32))!!
        val sampled = codec.getSampledDimensions(1)
        assertEquals(64, sampled.width)
        assertEquals(32, sampled.height)
    }

    @Test
    fun `getSampledDimensions clamps to 1 for over-sized requests`() {
        val codec = AndroidCodec.MakeFromData(synthPng(4, 4))!!
        val sampled = codec.getSampledDimensions(16)
        assertEquals(1, sampled.width)
        assertEquals(1, sampled.height)
    }

    @Test
    fun `computeSampleSize round-trips with getSampledDimensions`() {
        val codec = AndroidCodec.MakeFromData(synthPng(64, 64))!!
        // Ask for a half-size output.
        val s = codec.computeSampleSize(SkISize.Make(32, 32))
        assertEquals(2, s, "half of 64 → sample size 2")
        val sampled = codec.getSampledDimensions(s)
        assertTrue(sampled.width >= 32, "round-trip dim must be >= requested")
        assertTrue(sampled.height >= 32)
    }

    @Test
    fun `computeSampleSize picks larger pow2 when request fits`() {
        val codec = AndroidCodec.MakeFromData(synthPng(64, 64))!!
        val s = codec.computeSampleSize(SkISize.Make(8, 8))
        // Largest pow2 that still gets >= 8 → 64/8 = 8.
        assertEquals(8, s)
        val sampled = codec.getSampledDimensions(s)
        assertEquals(8, sampled.width)
        assertEquals(8, sampled.height)
    }

    @Test
    fun `computeSampleSize returns 1 when request is at-or-larger than source`() {
        val codec = AndroidCodec.MakeFromData(synthPng(16, 16))!!
        assertEquals(1, codec.computeSampleSize(SkISize.Make(16, 16)))
        assertEquals(1, codec.computeSampleSize(SkISize.Make(32, 32)))
    }

    // ─── getSupportedSubset ───────────────────────────────────────────

    @Test
    fun `getSupportedSubset clamps to source bounds`() {
        val codec = AndroidCodec.MakeFromData(synthPng(32, 32))!!
        val clamped = codec.getSupportedSubset(SkIRect.MakeLTRB(-4, -4, 20, 20))
        assertNotNull(clamped)
        assertEquals(0, clamped!!.left)
        assertEquals(0, clamped.top)
        assertEquals(20, clamped.right)
        assertEquals(20, clamped.bottom)
    }

    @Test
    fun `getSupportedSubset rejects an out-of-bounds rect`() {
        val codec = AndroidCodec.MakeFromData(synthPng(32, 32))!!
        // Fully outside source bounds — no intersection.
        assertNull(codec.getSupportedSubset(SkIRect.MakeLTRB(40, 40, 60, 60)))
    }

    @Test
    fun `getSupportedSubset passes through an in-bounds rect`() {
        val codec = AndroidCodec.MakeFromData(synthPng(32, 32))!!
        val in_ = SkIRect.MakeLTRB(4, 4, 20, 20)
        val out = codec.getSupportedSubset(in_)
        assertNotNull(out)
        assertEquals(in_.left, out!!.left)
        assertEquals(in_.right, out.right)
    }

    // ─── getSampledSubsetDimensions ───────────────────────────────────

    @Test
    fun `getSampledSubsetDimensions divides subset by sample size`() {
        val codec = AndroidCodec.MakeFromData(synthPng(64, 64))!!
        val subset = SkIRect.MakeLTRB(0, 0, 32, 16)
        val sampled = codec.getSampledSubsetDimensions(2, subset)
        assertEquals(16, sampled.width)
        assertEquals(8, sampled.height)
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private fun synthPng(width: Int, height: Int): ByteArray {
        val img = SkBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                img.setPixel(x, y, (0xFF shl 24) or (x * 8) or ((y * 8) shl 8))
            }
        }
        return SkPngEncoder.Encode(img) ?: error("Synthetic PNG encode failed")
    }

    @Test
    fun `MakeFromData unused warning suppressor`() {
        // Sanity-check that the AndroidOptions default is "no sampling, no subset".
        val opts = AndroidCodec.AndroidOptions()
        assertEquals(1, opts.sampleSize)
        assertNull(opts.subset)
        assertEquals(AndroidCodec.ZeroInitialized.kNo, opts.zeroInitialized)
        // ExifOrientationBehavior enum reachable.
        assertFalse(AndroidCodec.ExifOrientationBehavior.kIgnore == AndroidCodec.ExifOrientationBehavior.kRespect)
    }
}
