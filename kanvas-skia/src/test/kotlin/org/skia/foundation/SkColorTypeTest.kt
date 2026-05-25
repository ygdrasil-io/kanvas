package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColorChannelFlag

class SkColorTypeTest {

    @Test
    fun `bytesPerPixel matches the bit-layout comments`() {
        assertEquals(0, SkColorType.kUnknown.bytesPerPixel)
        assertEquals(1, SkColorType.kAlpha_8.bytesPerPixel)
        assertEquals(1, SkColorType.kGray_8.bytesPerPixel)
        assertEquals(1, SkColorType.kR8_unorm.bytesPerPixel)
        assertEquals(2, SkColorType.kRGB_565.bytesPerPixel)
        assertEquals(2, SkColorType.kARGB_4444.bytesPerPixel)
        assertEquals(2, SkColorType.kR8G8_unorm.bytesPerPixel)
        assertEquals(2, SkColorType.kA16_float.bytesPerPixel)
        assertEquals(2, SkColorType.kA16_unorm.bytesPerPixel)
        assertEquals(2, SkColorType.kR16_unorm.bytesPerPixel)
        assertEquals(4, SkColorType.kRGBA_8888.bytesPerPixel)
        assertEquals(4, SkColorType.kBGRA_8888.bytesPerPixel)
        assertEquals(4, SkColorType.kRGB_888x.bytesPerPixel)
        assertEquals(4, SkColorType.kRGBA_1010102.bytesPerPixel)
        assertEquals(4, SkColorType.kSRGBA_8888.bytesPerPixel)
        assertEquals(4, SkColorType.kR16G16_unorm.bytesPerPixel)
        assertEquals(4, SkColorType.kR16G16_float.bytesPerPixel)
        assertEquals(8, SkColorType.kRGBA_F16Norm.bytesPerPixel)
        assertEquals(8, SkColorType.kRGBA_F16.bytesPerPixel)
        assertEquals(8, SkColorType.kRGB_F16F16F16x.bytesPerPixel)
        assertEquals(8, SkColorType.kR16G16B16A16_unorm.bytesPerPixel)
        assertEquals(8, SkColorType.kBGRA_10101010_XR.bytesPerPixel)
        assertEquals(8, SkColorType.kRGBA_10x6.bytesPerPixel)
        assertEquals(16, SkColorType.kRGBA_F32.bytesPerPixel)
    }

    @Test
    fun `kUnknown is the first entry`() {
        assertEquals(SkColorType.kUnknown, SkColorType.entries.first())
        assertFalse(SkColorType.kUnknown.isValid())
    }

    @Test
    fun `kR8_unorm is the last upstream entry`() {
        // Mirrors upstream `kLastEnum_SkColorType = kR8_unorm_SkColorType`.
        assertEquals(SkColorType.kR8_unorm, SkColorType.entries.last())
    }

    @Test
    fun `isAlwaysOpaque flags the colour types where alpha is forced opaque`() {
        val opaqueByConstruction = setOf(
            SkColorType.kRGB_565,
            SkColorType.kRGB_888x,
            SkColorType.kRGB_101010x,
            SkColorType.kBGR_101010x,
            SkColorType.kBGR_101010x_XR,
            SkColorType.kRGB_F16F16F16x,
            SkColorType.kGray_8,
            SkColorType.kR8G8_unorm,
            SkColorType.kR16_unorm,
            SkColorType.kR16G16_unorm,
            SkColorType.kR16G16_float,
            SkColorType.kR8_unorm,
        )
        for (ct in SkColorType.entries) {
            assertEquals(
                ct in opaqueByConstruction,
                ct.isAlwaysOpaque(),
                "isAlwaysOpaque mismatch on $ct",
            )
        }
    }

    @Test
    fun `kAlpha_8 and kGray_8 share single-byte storage but different semantics`() {
        // kAlpha_8 stores alpha (with RGB forced 0); kGray_8 stores grayscale
        // (replicated to RGB, alpha forced opaque). Same size, different roles.
        assertEquals(1, SkColorType.kAlpha_8.bytesPerPixel)
        assertEquals(1, SkColorType.kGray_8.bytesPerPixel)
        assertFalse(SkColorType.kAlpha_8.isAlwaysOpaque())
        assertTrue(SkColorType.kGray_8.isAlwaysOpaque())
    }

    @Test
    fun `SkColorTypeChannelFlags mirrors upstream channel presence mapping`() {
        val expected = mapOf(
            SkColorType.kUnknown to 0,
            SkColorType.kAlpha_8 to SkColorChannelFlag.kAlpha_SkColorChannelFlag,
            SkColorType.kRGB_565 to SkColorChannelFlag.kRGB_SkColorChannelFlags,
            SkColorType.kARGB_4444 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGBA_8888 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGB_888x to SkColorChannelFlag.kRGB_SkColorChannelFlags,
            SkColorType.kBGRA_8888 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGBA_1010102 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kBGRA_1010102 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGB_101010x to SkColorChannelFlag.kRGB_SkColorChannelFlags,
            SkColorType.kBGR_101010x to SkColorChannelFlag.kRGB_SkColorChannelFlags,
            SkColorType.kBGR_101010x_XR to SkColorChannelFlag.kRGB_SkColorChannelFlags,
            SkColorType.kBGRA_10101010_XR to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGBA_10x6 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kGray_8 to SkColorChannelFlag.kGray_SkColorChannelFlag,
            SkColorType.kRGBA_F16Norm to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGBA_F16 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kRGB_F16F16F16x to SkColorChannelFlag.kRGB_SkColorChannelFlags,
            SkColorType.kRGBA_F32 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kR8G8_unorm to SkColorChannelFlag.kRG_SkColorChannelFlags,
            SkColorType.kA16_float to SkColorChannelFlag.kAlpha_SkColorChannelFlag,
            SkColorType.kR16G16_float to SkColorChannelFlag.kRG_SkColorChannelFlags,
            SkColorType.kA16_unorm to SkColorChannelFlag.kAlpha_SkColorChannelFlag,
            SkColorType.kR16_unorm to SkColorChannelFlag.kRed_SkColorChannelFlag,
            SkColorType.kR16G16_unorm to SkColorChannelFlag.kRG_SkColorChannelFlags,
            SkColorType.kR16G16B16A16_unorm to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kSRGBA_8888 to SkColorChannelFlag.kRGBA_SkColorChannelFlags,
            SkColorType.kR8_unorm to SkColorChannelFlag.kRed_SkColorChannelFlag,
        )
        for (ct in SkColorType.entries) {
            assertEquals(expected[ct], SkColorTypeChannelFlags(ct), "channel flags mismatch on $ct")
        }
    }
}
