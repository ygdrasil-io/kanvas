package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.ImageDecodeResult
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Gamut
import org.graphiks.kanvas.types.TransferFunction
import org.graphiks.math.SkcmsTransferFunction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkICC
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse

class CodecImageDecoderColorSpaceTest {
    @Test
    fun `decoder preserves Display P3 tag without transforming RGBA samples`() {
        val source = requireNotNull(
            SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3),
        )

        val result = decodeWith(source)

        assertTrue(result is ImageDecodeResult.Success)
        val image = (result as ImageDecodeResult.Success).image
        assertEquals(ColorSpace.DISPLAY_P3, image.colorSpace)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x7F), image.pixels)
    }

    @Test
    fun `decoder preserves serialized sRGB tag without transforming RGBA samples`() {
        val profile = requireNotNull(
            skcmsParse(SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)),
        )
        val source = requireNotNull(SkColorSpace.make(profile))

        val result = decodeWith(source)

        assertTrue(result is ImageDecodeResult.Success)
        val image = (result as ImageDecodeResult.Success).image
        assertEquals(ColorSpace.SRGB, image.colorSpace)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x7F), image.pixels)
    }

    @Test
    fun `decoder reuses common refusal for an unrepresentable transfer`() {
        val source = requireNotNull(
            SkColorSpace.makeRGB(
                SkcmsTransferFunction(
                    g = 1.8f,
                    a = 1f,
                    b = 0f,
                    c = 1f,
                    d = 0f,
                    e = 0f,
                    f = 0f,
                ),
                SkNamedGamut.kDisplayP3,
            ),
        )

        val result = decodeWith(source)

        assertEquals(
            ImageDecodeResult.Failure("codec.color-space-unsupported:transfer"),
            result,
        )
    }

    private fun decodeWith(colorSpace: SkColorSpace): ImageDecodeResult {
        val data = "kanvas-color-space-test".toByteArray()
        val decoder = object : Codec.Decoder {
            override val name: String = TEST_DECODER_NAME
            override fun matches(data: ByteArray): Boolean = data.contentEquals(TEST_DATA)
            override fun make(data: ByteArray): Codec = FakeCodec(colorSpace)
        }
        Codec.Decoders.register(decoder)
        return try {
            CodecImageDecoder().decode(data)
        } finally {
            Codec.Decoders.unregister(TEST_DECODER_NAME)
        }
    }

    private class FakeCodec(
        private val colorSpace: SkColorSpace,
    ) : Codec() {
        override fun getInfo(): SkImageInfo = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = colorSpace,
        )

        override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG

        override fun getICCProfile(): SkcmsICCProfile? = null

        override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
            dst.pixels8888[0] = SAMPLE_ARGB
            return Result.kSuccess
        }
    }

    private companion object {
        const val TEST_DECODER_NAME: String = "kanvas-color-space-test"
        const val SAMPLE_ARGB: Int = 0x7F123456
        val TEST_DATA: ByteArray = "kanvas-color-space-test".toByteArray()
    }
}
