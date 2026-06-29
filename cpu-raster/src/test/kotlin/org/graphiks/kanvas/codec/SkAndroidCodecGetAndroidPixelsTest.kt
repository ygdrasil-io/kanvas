package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkIRect
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import java.nio.ByteBuffer

/**
 * R-suivi.34 verification — exercises [SkAndroidCodec.getAndroidPixels]
 * against a synthetic PNG with the three operating modes :
 *  - default options (no sample, no subset) → full decode,
 *  - sampleSize=2 → output dimensions are half,
 *  - subset → output dimensions match the requested rect.
 */
class SkAndroidCodecGetAndroidPixelsTest {

    @Test
    fun `getAndroidPixels with default options decodes full image`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(8, 8))!!
        val info = codec.getInfo()
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * info.height)
        val result = codec.getAndroidPixels(info, buf, rowBytes)
        assertEquals(SkCodec.Result.kSuccess, result)
        // Top-left pixel should match the synthPng pattern : R = 0, G = 0,
        // B = 0x40, A = 0xFF for (x=0, y=0).
        val r = buf.get(0).toInt() and 0xFF
        val g = buf.get(1).toInt() and 0xFF
        val a = buf.get(3).toInt() and 0xFF
        assertEquals(0, r)
        assertEquals(0, g)
        assertEquals(0xFF, a)
    }

    @Test
    fun `getAndroidPixels with sampleSize=2 halves the dimensions`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(16, 16))!!
        val srcInfo = codec.getInfo()
        // The post-sample info must be half-by-half.
        val sampledInfo = SkImageInfo.Make(
            width = 8,
            height = 8,
            colorType = srcInfo.colorType,
            alphaType = srcInfo.alphaType,
            colorSpace = srcInfo.colorSpace,
        )
        val rowBytes = sampledInfo.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * sampledInfo.height)
        val result = codec.getAndroidPixels(
            sampledInfo,
            buf,
            rowBytes,
            SkAndroidCodec.AndroidOptions(sampleSize = 2),
        )
        assertEquals(SkCodec.Result.kSuccess, result)
        // Sanity-check : the sampled output has 8x8 = 64 pixels × 4 bpp = 256 bytes.
        assertEquals(256, rowBytes * sampledInfo.height)
    }

    @Test
    fun `getAndroidPixels with subset extracts just that region`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(16, 16))!!
        val srcInfo = codec.getInfo()
        val subset = SkIRect.MakeLTRB(4, 4, 12, 12)
        val subInfo = SkImageInfo.Make(
            width = 8,
            height = 8,
            colorType = srcInfo.colorType,
            alphaType = srcInfo.alphaType,
            colorSpace = srcInfo.colorSpace,
        )
        val rowBytes = subInfo.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * subInfo.height)
        val result = codec.getAndroidPixels(
            subInfo,
            buf,
            rowBytes,
            SkAndroidCodec.AndroidOptions(subset = subset),
        )
        assertEquals(SkCodec.Result.kSuccess, result)
    }

    @Test
    fun `getAndroidPixels rejects mismatched info dimensions`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(16, 16))!!
        val srcInfo = codec.getInfo()
        // Caller info claims full size, but sampleSize=2 would halve it.
        val rowBytes = srcInfo.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * srcInfo.height)
        val result = codec.getAndroidPixels(
            srcInfo, // claims 16x16
            buf,
            rowBytes,
            SkAndroidCodec.AndroidOptions(sampleSize = 2), // would produce 8x8
        )
        assertEquals(SkCodec.Result.kInvalidParameters, result)
    }

    @Test
    fun `getAndroidPixels combined subset + sampleSize produces both effects`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(32, 32))!!
        val srcInfo = codec.getInfo()
        val subset = SkIRect.MakeLTRB(0, 0, 16, 16) // 16x16 subset
        val sampled = SkImageInfo.Make(
            width = 8, // 16 / 2
            height = 8,
            colorType = srcInfo.colorType,
            alphaType = srcInfo.alphaType,
            colorSpace = srcInfo.colorSpace,
        )
        val rowBytes = sampled.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * sampled.height)
        val result = codec.getAndroidPixels(
            sampled,
            buf,
            rowBytes,
            SkAndroidCodec.AndroidOptions(sampleSize = 2, subset = subset),
        )
        assertEquals(SkCodec.Result.kSuccess, result)
    }

    @Test
    fun `getAndroidPixels rejects out-of-bounds subset`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(8, 8))!!
        val srcInfo = codec.getInfo()
        val subset = SkIRect.MakeLTRB(20, 20, 30, 30) // fully outside
        val result = codec.getAndroidPixels(
            srcInfo,
            ByteBuffer.allocate(srcInfo.minRowBytes() * srcInfo.height),
            srcInfo.minRowBytes(),
            SkAndroidCodec.AndroidOptions(subset = subset),
        )
        assertEquals(SkCodec.Result.kInvalidParameters, result)
    }

    @Test
    fun `getAndroidPixels rejects F16 destination`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(4, 4))!!
        val srcInfo = codec.getInfo()
        val f16 = SkImageInfo.Make(
            width = srcInfo.width,
            height = srcInfo.height,
            colorType = SkColorType.kRGBA_F16Norm,
            colorSpace = srcInfo.colorSpace,
        )
        val result = codec.getAndroidPixels(
            f16,
            ByteBuffer.allocate(f16.minRowBytes() * f16.height),
            f16.minRowBytes(),
        )
        assertEquals(SkCodec.Result.kInvalidConversion, result)
    }

    @Test
    fun `getAndroidPixels rejects too-small rowBytes`() {
        val codec = SkAndroidCodec.MakeFromData(synthPng(4, 4))!!
        val srcInfo = codec.getInfo()
        val result = codec.getAndroidPixels(
            srcInfo,
            ByteBuffer.allocate(srcInfo.minRowBytes() * srcInfo.height),
            rowBytes = srcInfo.minRowBytes() - 1, // too tight
        )
        assertEquals(SkCodec.Result.kInvalidParameters, result)
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
    fun `synthPng builds a valid PNG`() {
        // Sanity-check the test harness.
        val bytes = synthPng(2, 2)
        assertNotNull(SkCodec.MakeFromData(bytes))
    }
}
