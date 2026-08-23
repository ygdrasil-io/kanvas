package org.graphiks.kanvas.codec

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.Gamut
import org.graphiks.kanvas.color.TransferFunction
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.ColorMatrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.graphiks.kanvas.color.ImageColorSpace
import org.skia.foundation.SkColorType
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.skia.foundation.SkImageInfo
import org.graphiks.kanvas.color.icc.IccProfile

class KanvasCodecColorSpaceTest {
    @Test
    fun `sRGB source tag is preserved`() {
        val result = imageInfo(ImageColorSpace.sRGB()).toKanvasImageInfo()

        assertEquals(ColorSpace.SRGB, result.colorSpace)
    }

    @Test
    fun `sRGB bitmap tag and samples are preserved`() {
        assertBitmapTagAndSamples(ImageColorSpace.sRGB(), ColorSpace.SRGB)
    }

    @Test
    fun `linear sRGB source tag is preserved`() {
        val result = imageInfo(ImageColorSpace.linearSrgb()).toKanvasImageInfo()

        assertEquals(ColorSpace.LINEAR_SRGB, result.colorSpace)
    }

    @Test
    fun `linear sRGB bitmap tag and samples are preserved`() {
        assertBitmapTagAndSamples(ImageColorSpace.linearSrgb(), ColorSpace.LINEAR_SRGB)
    }

    @Test
    fun `Display P3 source tag is preserved`() {
        val result = imageInfo(sdrColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50))).toKanvasImageInfo()

        assertEquals(ColorSpace.DISPLAY_P3, result.colorSpace)
    }

    @Test
    fun `serialized Display P3 source tag is preserved`() {
        val source = serializedColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50))

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(ColorSpace.DISPLAY_P3, result.colorSpace)
    }

    @Test
    fun `serialized sRGB source tag is preserved`() {
        val source = serializedColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50))

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(ColorSpace.SRGB, result.colorSpace)
    }

    @Test
    fun `serialized linear sRGB source tag is preserved`() {
        val source = serializedColorSpace(org.graphiks.math.color.ColorTransferFunction.linear, requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50))

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(ColorSpace.LINEAR_SRGB, result.colorSpace)
    }

    @Test
    fun `serialized Rec2020 source with sRGB transfer is preserved`() {
        val source = serializedColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.rec2020().toXyzD50))

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(TransferFunction.SRGB, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `serialized Rec2020 source with linear transfer is preserved`() {
        val source = serializedColorSpace(org.graphiks.math.color.ColorTransferFunction.linear, requireNotNull(org.graphiks.kanvas.color.ColorProfiles.rec2020().toXyzD50))

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(TransferFunction.LINEAR, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `Display P3 bitmap tag and samples are preserved`() {
        assertBitmapTagAndSamples(sdrColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50)), ColorSpace.DISPLAY_P3)
    }

    @Test
    fun `Rec2020 source with sRGB SDR transfer is preserved`() {
        val result = imageInfo(sdrColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.rec2020().toXyzD50))).toKanvasImageInfo()

        assertEquals(TransferFunction.SRGB, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `Rec2020 source with unrepresentable BT2020 SDR transfer is refused`() {
        val source = requireNotNull(
            ImageColorSpace.fromMatrixTrc(
                requireNotNull(ColorProfiles.rec2020().transferFunction),
                requireNotNull(org.graphiks.kanvas.color.ColorProfiles.rec2020().toXyzD50),
            ),
        )

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported ImageColorSpace for Kanvas conversion: transfer",
            failure.message,
        )
    }

    @Test
    fun `Rec2020 PQ source tag is preserved`() {
        val result = imageInfo(hdrColorSpace(transfer = 16)).toKanvasImageInfo()

        assertEquals(TransferFunction.PQ, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `Rec2020 HLG source tag is preserved`() {
        val result = imageInfo(hdrColorSpace(transfer = 18)).toKanvasImageInfo()

        assertEquals(TransferFunction.HLG, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `unknown SDR transfer is refused instead of retagged`() {
        val unknownTransfer = ColorTransferFunction.parametric(
            g = 1.8f,
            a = 1f,
            b = 0f,
            c = 1f,
            d = 0f,
            e = 0f,
            f = 0f,
        )
        val source = requireNotNull(ImageColorSpace.fromMatrixTrc(unknownTransfer, requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50)))

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported ImageColorSpace for Kanvas conversion: transfer",
            failure.message,
        )
    }

    @Test
    fun `unknown gamut is refused instead of retagged`() {
        val unknownGamut = ColorMatrix3x3F32.of(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val source = requireNotNull(ImageColorSpace.fromMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), unknownGamut))

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported ImageColorSpace for Kanvas conversion: gamut",
            failure.message,
        )
    }

    @Test
    fun `nearby unknown gamut is refused instead of retagged as sRGB`() {
        val unknownGamut = ColorMatrix3x3F32.of(
            requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[0, 0] + 3f / 65_536f, requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[0, 1] - 3f / 65_536f, requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[0, 2],
            requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[1, 0], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[1, 1], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[1, 2],
            requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[2, 0], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[2, 1], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[2, 2],
        )
        val source = serializedColorSpace(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), unknownGamut)

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported ImageColorSpace for Kanvas conversion: gamut",
            failure.message,
        )
    }

    @Test
    fun `named gamut classification is isolated from public matrix mutation`() {
        val publicGamut = requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)
        val originalGamut = ColorMatrix3x3F32.fromRowMajor(publicGamut.toFloatArray())
        val stableSource = sdrColorSpace(originalGamut)
        assertEquals(ColorSpace.SRGB, imageInfo(stableSource).toKanvasImageInfo().colorSpace)

        val mutatedSource = sdrColorSpace(ColorMatrix3x3F32.of(
            publicGamut[0, 0] + 0.25f, publicGamut[0, 1], publicGamut[0, 2],
            publicGamut[1, 0], publicGamut[1, 1], publicGamut[1, 2],
            publicGamut[2, 0], publicGamut[2, 1], publicGamut[2, 2],
        ))

        val failure = assertThrows<UnsupportedKanvasColorSpaceException> {
            imageInfo(mutatedSource).toKanvasImageInfo()
        }

        assertEquals("gamut", failure.reason)
        assertEquals(ColorSpace.SRGB, imageInfo(stableSource).toKanvasImageInfo().colorSpace)

        assertEquals(ColorSpace.SRGB, imageInfo(stableSource).toKanvasImageInfo().colorSpace)
    }

    private fun imageInfo(colorSpace: ImageColorSpace): SkImageInfo = SkImageInfo.Make(
        width = 1,
        height = 1,
        colorType = SkColorType.kRGBA_8888,
        alphaType = SkAlphaType.kUnpremul,
        colorSpace = colorSpace,
    )

    private fun sdrColorSpace(gamut: ColorMatrix3x3F32): ImageColorSpace =
        requireNotNull(ImageColorSpace.fromMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), gamut))

    private fun serializedColorSpace(
        transferFunction: ColorTransferFunction.Parametric,
        gamut: ColorMatrix3x3F32,
    ): ImageColorSpace = ImageColorSpace.fromIccProfile(
        IccProfile.parse(IccProfileWriter.writeMatrixTrc(transferFunction, gamut)).getOrThrow(),
    )

    private fun hdrColorSpace(transfer: Int): ImageColorSpace =
        ImageColorSpace.fromColorProfile(cicpProfile(transfer))

    private fun assertBitmapTagAndSamples(sourceColorSpace: ImageColorSpace, expectedColorSpace: ColorSpace) {
        val source = SkBitmap(width = 1, height = 1, colorSpace = sourceColorSpace)
        source.pixels8888[0] = SAMPLE_ARGB

        val result = source.toKanvasBitmap()

        assertEquals(expectedColorSpace, result.colorSpace)
        assertEquals(SAMPLE_ARGB, result.getArgb(0, 0))
    }

    private fun cicpProfile(transfer: Int): ColorProfile = when (
        val result = CicpColorInfo(
            primaries = 9,
            transfer = transfer,
            matrix = 0,
            fullRange = true,
        ).toColorProfile()
    ) {
        is ColorProfileParseResult.Success -> result.profile
        is ColorProfileParseResult.Failure -> error(result.code)
    }

    private companion object {
        const val SAMPLE_ARGB: Int = 0x7F123456
    }
}
