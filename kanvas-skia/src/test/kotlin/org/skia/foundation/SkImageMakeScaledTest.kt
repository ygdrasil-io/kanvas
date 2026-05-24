package org.skia.foundation

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SkImageMakeScaledTest {

    @Test
    fun `makeScaled returns nearest-neighbour image with requested dimensions`() {
        val src = SkImage(
            2,
            2,
            intArrayOf(
                SK_ColorRED, SK_ColorGREEN,
                SK_ColorBLUE, SK_ColorWHITE,
            ),
        )

        val scaled = src.makeScaled(
            SkImageInfo.MakeN32Premul(4, 4),
            SkSamplingOptions.Default,
        )

        assertNotNull(scaled)
        scaled!!
        assertNotSame(src, scaled)
        assertEquals(4, scaled.width)
        assertEquals(4, scaled.height)
        assertEquals(SK_ColorRED, scaled.peekPixel(0, 0))
        assertEquals(SK_ColorGREEN, scaled.peekPixel(3, 0))
        assertEquals(SK_ColorBLUE, scaled.peekPixel(0, 3))
        assertEquals(SK_ColorWHITE, scaled.peekPixel(3, 3))
    }

    @Test
    fun `makeScaled supports linear filtering`() {
        val src = SkImage(
            2,
            1,
            intArrayOf(SK_ColorRED, SK_ColorBLUE),
        )

        val scaled = src.makeScaled(
            SkImageInfo.MakeN32Premul(1, 1),
            SkSamplingOptions(SkFilterMode.kLinear),
        )

        assertNotNull(scaled)
        assertEquals(0xFF800080.toInt(), scaled!!.peekPixel(0, 0))
    }

    @Test
    fun `makeScaled rejects empty unknown or unsupported target info`() {
        val src = SkImage(2, 2, IntArray(4))

        assertNull(src.makeScaled(SkImageInfo.MakeN32Premul(0, 2), SkSamplingOptions.Default))
        assertNull(
            src.makeScaled(
                SkImageInfo.Make(2, 2, SkColorType.kUnknown, SkAlphaType.kUnknown),
                SkSamplingOptions.Default,
            ),
        )
        assertNull(
            src.makeScaled(
                SkImageInfo.Make(2, 2, SkColorType.kRGB_565, SkAlphaType.kOpaque),
                SkSamplingOptions.Default,
            ),
        )
    }
}
