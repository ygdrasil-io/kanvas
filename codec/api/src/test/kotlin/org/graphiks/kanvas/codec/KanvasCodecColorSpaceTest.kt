package org.graphiks.kanvas.codec

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Gamut
import org.graphiks.kanvas.types.TransferFunction
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkICC
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse

class KanvasCodecColorSpaceTest {
    @Test
    fun `sRGB source tag is preserved`() {
        val result = imageInfo(SkColorSpace.makeSRGB()).toKanvasImageInfo()

        assertEquals(ColorSpace.SRGB, result.colorSpace)
    }

    @Test
    fun `sRGB bitmap tag and samples are preserved`() {
        assertBitmapTagAndSamples(SkColorSpace.makeSRGB(), ColorSpace.SRGB)
    }

    @Test
    fun `linear sRGB source tag is preserved`() {
        val result = imageInfo(SkColorSpace.makeSRGBLinear()).toKanvasImageInfo()

        assertEquals(ColorSpace.LINEAR_SRGB, result.colorSpace)
    }

    @Test
    fun `linear sRGB bitmap tag and samples are preserved`() {
        assertBitmapTagAndSamples(SkColorSpace.makeSRGBLinear(), ColorSpace.LINEAR_SRGB)
    }

    @Test
    fun `Display P3 source tag is preserved`() {
        val result = imageInfo(sdrColorSpace(SkNamedGamut.kDisplayP3)).toKanvasImageInfo()

        assertEquals(ColorSpace.DISPLAY_P3, result.colorSpace)
    }

    @Test
    fun `serialized Display P3 source tag is preserved`() {
        val source = serializedColorSpace(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(ColorSpace.DISPLAY_P3, result.colorSpace)
    }

    @Test
    fun `serialized sRGB source tag is preserved`() {
        val source = serializedColorSpace(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(ColorSpace.SRGB, result.colorSpace)
    }

    @Test
    fun `serialized linear sRGB source tag is preserved`() {
        val source = serializedColorSpace(SkNamedTransferFn.kLinear, SkNamedGamut.kSRGB)

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(ColorSpace.LINEAR_SRGB, result.colorSpace)
    }

    @Test
    fun `serialized Rec2020 source with sRGB transfer is preserved`() {
        val source = serializedColorSpace(SkNamedTransferFn.kSRGB, SkNamedGamut.kRec2020)

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(TransferFunction.SRGB, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `serialized Rec2020 source with linear transfer is preserved`() {
        val source = serializedColorSpace(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)

        val result = imageInfo(source).toKanvasImageInfo()

        assertEquals(TransferFunction.LINEAR, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `Display P3 bitmap tag and samples are preserved`() {
        assertBitmapTagAndSamples(sdrColorSpace(SkNamedGamut.kDisplayP3), ColorSpace.DISPLAY_P3)
    }

    @Test
    fun `Rec2020 source with sRGB SDR transfer is preserved`() {
        val result = imageInfo(sdrColorSpace(SkNamedGamut.kRec2020)).toKanvasImageInfo()

        assertEquals(TransferFunction.SRGB, result.colorSpace.transferFunction)
        assertEquals(Gamut.REC2020, result.colorSpace.gamut)
    }

    @Test
    fun `Rec2020 source with unrepresentable BT2020 SDR transfer is refused`() {
        val source = requireNotNull(
            SkColorSpace.makeRGB(
                requireNotNull(ColorProfiles.rec2020().transferFunction),
                SkNamedGamut.kRec2020,
            ),
        )

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported SkColorSpace for Kanvas conversion: transfer",
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
        val unknownTransfer = SkcmsTransferFunction(
            g = 1.8f,
            a = 1f,
            b = 0f,
            c = 1f,
            d = 0f,
            e = 0f,
            f = 0f,
        )
        val source = requireNotNull(SkColorSpace.makeRGB(unknownTransfer, SkNamedGamut.kDisplayP3))

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported SkColorSpace for Kanvas conversion: transfer",
            failure.message,
        )
    }

    @Test
    fun `unknown gamut is refused instead of retagged`() {
        val unknownGamut = SkcmsMatrix3x3.of(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val source = requireNotNull(SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, unknownGamut))

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported SkColorSpace for Kanvas conversion: gamut",
            failure.message,
        )
    }

    @Test
    fun `nearby unknown gamut is refused instead of retagged as sRGB`() {
        val unknownGamut = SkNamedGamut.kSRGB.copy().also {
            for (row in 0 until 3) {
                it.vals[row][0] += 3f / 65_536f
                it.vals[row][1] -= 3f / 65_536f
            }
        }
        val source = serializedColorSpace(SkNamedTransferFn.kSRGB, unknownGamut)

        val failure = assertThrows<IllegalArgumentException> {
            imageInfo(source).toKanvasImageInfo()
        }

        assertEquals(
            "Unsupported SkColorSpace for Kanvas conversion: gamut",
            failure.message,
        )
    }

    @Test
    fun `named gamut classification is isolated from public matrix mutation`() {
        val publicGamut = SkNamedGamut.kSRGB
        val originalGamut = publicGamut.copy()
        val stableSource = sdrColorSpace(originalGamut)
        assertEquals(ColorSpace.SRGB, imageInfo(stableSource).toKanvasImageInfo().colorSpace)

        try {
            publicGamut.vals[0][0] += 0.25f
            val mutatedSource = sdrColorSpace(publicGamut.copy())

            val failure = assertThrows<UnsupportedKanvasColorSpaceException> {
                imageInfo(mutatedSource).toKanvasImageInfo()
            }

            assertEquals("gamut", failure.reason)
            assertEquals(ColorSpace.SRGB, imageInfo(stableSource).toKanvasImageInfo().colorSpace)
        } finally {
            restoreMatrix(publicGamut, originalGamut)
        }

        assertEquals(ColorSpace.SRGB, imageInfo(stableSource).toKanvasImageInfo().colorSpace)
    }

    private fun imageInfo(colorSpace: SkColorSpace): SkImageInfo = SkImageInfo.Make(
        width = 1,
        height = 1,
        colorType = SkColorType.kRGBA_8888,
        alphaType = SkAlphaType.kUnpremul,
        colorSpace = colorSpace,
    )

    private fun sdrColorSpace(gamut: SkcmsMatrix3x3): SkColorSpace =
        requireNotNull(SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, gamut))

    private fun serializedColorSpace(
        transferFunction: SkcmsTransferFunction,
        gamut: SkcmsMatrix3x3,
    ): SkColorSpace = requireNotNull(
        SkColorSpace.make(
            requireNotNull(skcmsParse(SkICC.WriteToICC(transferFunction, gamut))),
        ),
    )

    private fun hdrColorSpace(transfer: Int): SkColorSpace = SkColorSpace.makeProfileAware(
        SkcmsICCProfile.fromColorProfile(cicpProfile(transfer)),
    )

    private fun assertBitmapTagAndSamples(sourceColorSpace: SkColorSpace, expectedColorSpace: ColorSpace) {
        val source = SkBitmap(width = 1, height = 1, colorSpace = sourceColorSpace)
        source.pixels8888[0] = SAMPLE_ARGB

        val result = source.toKanvasBitmap()

        assertEquals(expectedColorSpace, result.colorSpace)
        assertEquals(SAMPLE_ARGB, result.getArgb(0, 0))
    }

    private fun restoreMatrix(target: SkcmsMatrix3x3, source: SkcmsMatrix3x3) {
        for (row in 0 until 3) for (column in 0 until 3) {
            target.vals[row][column] = source[row, column]
        }
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
