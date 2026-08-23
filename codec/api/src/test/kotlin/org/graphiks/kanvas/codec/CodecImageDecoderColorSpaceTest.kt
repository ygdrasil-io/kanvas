package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.ImageDecodeResult
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.Gamut
import org.graphiks.kanvas.color.TransferFunction
import org.graphiks.math.color.ColorTransferFunction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.kanvas.color.icc.IccProfile
import org.graphiks.kanvas.image.ImageInfo

class CodecImageDecoderColorSpaceTest {
    @Test
    fun `decoder preserves Display P3 tag without transforming RGBA samples`() {
        val source = requireNotNull(
            ImageColorSpace.fromMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50)),
        )

        val result = decodeWith(source)

        assertTrue(result is ImageDecodeResult.Success)
        val image = (result as ImageDecodeResult.Success).image
        assertEquals(ColorSpace.DISPLAY_P3, image.colorSpace)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x7F), image.pixels)
    }

    @Test
    fun `decoder preserves serialized sRGB tag without transforming RGBA samples`() {
        val profile = IccProfile.parse(
            IccProfileWriter.writeMatrixTrc(
                requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction),
                requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50),
            ),
        ).getOrThrow()
        val source = ImageColorSpace.fromIccProfile(profile)

        val result = decodeWith(source)

        assertTrue(result is ImageDecodeResult.Success)
        val image = (result as ImageDecodeResult.Success).image
        assertEquals(ColorSpace.SRGB, image.colorSpace)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x7F), image.pixels)
    }

    @Test
    fun `decoder preserves premultiplied alpha metadata for canonical RGBA samples`() {
        val result = decodeWith(ImageColorSpace.sRGB(), alphaType = AlphaType.PREMUL)

        assertTrue(result is ImageDecodeResult.Success)
        assertEquals(AlphaType.PREMUL, (result as ImageDecodeResult.Success).image.alphaType)
    }

    @Test
    fun `decoder reuses common refusal for an unrepresentable transfer`() {
        val source = requireNotNull(
            ImageColorSpace.fromMatrixTrc(
                ColorTransferFunction.parametric(
                    g = 1.8f,
                    a = 1f,
                    b = 0f,
                    c = 1f,
                    d = 0f,
                    e = 0f,
                    f = 0f,
                ),
                requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50),
            ),
        )

        val result = decodeWith(source)

        assertEquals(
            ImageDecodeResult.Failure("codec.color-space-unsupported:transfer"),
            result,
        )
    }

    @Test
    fun `codec exposes an immutable native ICC profile`() {
        val embedded = IccProfile.fromMatrixTrc(ColorProfiles.displayP3())
        val codec = FakeCodec(ImageColorSpace.sRGB(), embedded)
        val expected = requireNotNull(codec.getICCProfile()).bytes

        codec.getICCProfile()!!.bytes[0] = 0

        assertArrayEquals(expected, codec.getICCProfile()!!.bytes)
    }

    @Test
    fun `codec info preserves its ImageColorSpace without an adapter`() {
        val colorSpace = ImageColorSpace.linearSrgb()

        assertEquals(colorSpace, FakeCodec(colorSpace).getInfo().colorSpace)
    }

    @Test
    fun `decoder refuses a non canonical RGBA bitmap instead of reinterpreting it`() {
        val decoder = object : Codec.Decoder {
            override val name: String = TEST_DECODER_NAME
            override fun matches(data: ByteArray): Boolean = data.contentEquals(TEST_DATA)
            override fun make(data: ByteArray): Codec = FakeCodec(
                colorSpace = ImageColorSpace.sRGB(),
                colorType = ColorType.BGRA_8888,
            )
        }
        Codec.Decoders.register(decoder)

        val result = try {
            CodecImageDecoder().decode(TEST_DATA)
        } finally {
            Codec.Decoders.unregister(TEST_DECODER_NAME)
        }

        assertEquals(ImageDecodeResult.Failure("codec.decode-failed:kInvalidConversion"), result)
    }

    @Test
    fun `getImage refuses an unavailable output conversion`() {
        val (bitmap, result) = FakeCodec(
            colorSpace = ImageColorSpace.sRGB(),
            colorType = ColorType.R8_UNORM,
        ).getImage()

        assertEquals(Codec.Result.kInvalidConversion, result)
        assertEquals(null, bitmap)
    }

    private fun decodeWith(
        colorSpace: ImageColorSpace,
        alphaType: AlphaType = AlphaType.UNPREMUL,
    ): ImageDecodeResult {
        val data = "kanvas-color-space-test".toByteArray()
        val decoder = object : Codec.Decoder {
            override val name: String = TEST_DECODER_NAME
            override fun matches(data: ByteArray): Boolean = data.contentEquals(TEST_DATA)
            override fun make(data: ByteArray): Codec = FakeCodec(colorSpace, alphaType = alphaType)
        }
        Codec.Decoders.register(decoder)
        return try {
            CodecImageDecoder().decode(data)
        } finally {
            Codec.Decoders.unregister(TEST_DECODER_NAME)
        }
    }

    private class FakeCodec(
        private val colorSpace: ImageColorSpace,
        private val iccProfile: IccProfile? = null,
        private val colorType: ColorType = ColorType.RGBA_8888,
        private val alphaType: AlphaType = AlphaType.UNPREMUL,
    ) : Codec() {
        override fun getInfo(): ImageInfo = ImageInfo.make(
            width = 1,
            height = 1,
            colorType = colorType,
            alphaType = alphaType,
            colorSpace = colorSpace,
        )

        override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.PNG

        override fun getICCProfile(): IccProfile? = iccProfile

        override fun getPixels(info: ImageInfo, dst: Bitmap): Result {
            dst.setArgb(0, 0, SAMPLE_ARGB)
            return Result.kSuccess
        }
    }

    private companion object {
        const val TEST_DECODER_NAME: String = "kanvas-color-space-test"
        const val SAMPLE_ARGB: Int = 0x7F123456
        val TEST_DATA: ByteArray = "kanvas-color-space-test".toByteArray()
    }
}
