package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkBlendModeTest {

    @Test
    fun `29 modes in upstream declaration order`() {
        val expected = listOf(
            // Porter-Duff coefficient modes (kClear..kScreen)
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kDst,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
            SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kXor,
            SkBlendMode.kPlus,
            SkBlendMode.kModulate,
            SkBlendMode.kScreen,
            // Separable component modes (kOverlay..kMultiply)
            SkBlendMode.kOverlay,
            SkBlendMode.kDarken,
            SkBlendMode.kLighten,
            SkBlendMode.kColorDodge,
            SkBlendMode.kColorBurn,
            SkBlendMode.kHardLight,
            SkBlendMode.kSoftLight,
            SkBlendMode.kDifference,
            SkBlendMode.kExclusion,
            SkBlendMode.kMultiply,
            // HSL modes (kHue..kLuminosity)
            SkBlendMode.kHue,
            SkBlendMode.kSaturation,
            SkBlendMode.kColor,
            SkBlendMode.kLuminosity,
        )
        assertEquals(expected, SkBlendMode.entries)
        assertEquals(29, SkBlendMode.entries.size)
        assertEquals(29, SkBlendMode.kSkBlendModeCount)
    }

    @Test
    fun `kLastCoeffMode is kScreen`() {
        // Upstream: kLastCoeffMode = kScreen. Index 14, last of the
        // Porter-Duff coefficient family.
        assertEquals(SkBlendMode.kScreen, SkBlendMode.kLastCoeffMode)
        assertEquals(14, SkBlendMode.kScreen.ordinal)
    }

    @Test
    fun `kLastSeparableMode is kMultiply`() {
        // Upstream: kLastSeparableMode = kMultiply. Index 24.
        assertEquals(SkBlendMode.kMultiply, SkBlendMode.kLastSeparableMode)
        assertEquals(24, SkBlendMode.kMultiply.ordinal)
    }

    @Test
    fun `kLastMode is kLuminosity`() {
        // Upstream: kLastMode = kLuminosity. Last enum value, index 28.
        assertEquals(SkBlendMode.kLuminosity, SkBlendMode.kLastMode)
        assertEquals(28, SkBlendMode.kLuminosity.ordinal)
        assertEquals(SkBlendMode.entries.last(), SkBlendMode.kLastMode)
    }

    @Test
    fun `kSrcOver is the canonical default and falls within Porter-Duff range`() {
        assertEquals(3, SkBlendMode.kSrcOver.ordinal)
        // It must be a coeff mode (<= kLastCoeffMode).
        assert(SkBlendMode.kSrcOver.ordinal <= SkBlendMode.kLastCoeffMode.ordinal) {
            "kSrcOver (${SkBlendMode.kSrcOver.ordinal}) should be <= kLastCoeffMode (${SkBlendMode.kLastCoeffMode.ordinal})"
        }
    }
}
