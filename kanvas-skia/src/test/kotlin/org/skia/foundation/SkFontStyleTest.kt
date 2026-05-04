package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SkFontStyleTest {

    @Test
    fun `default ctor is Normal`() {
        val s = SkFontStyle()
        assertEquals(SkFontStyle.kNormal_Weight, s.weight)
        assertEquals(SkFontStyle.kNormal_Width, s.width)
        assertEquals(SkFontStyle.Slant.kUpright_Slant, s.slant)
    }

    @Test
    fun `Normal Bold Italic BoldItalic match upstream constants`() {
        val n = SkFontStyle.Normal()
        val b = SkFontStyle.Bold()
        val i = SkFontStyle.Italic()
        val bi = SkFontStyle.BoldItalic()

        assertEquals(SkFontStyle.kNormal_Weight, n.weight)
        assertEquals(SkFontStyle.kBold_Weight, b.weight)
        assertEquals(SkFontStyle.kNormal_Weight, i.weight)
        assertEquals(SkFontStyle.kBold_Weight, bi.weight)

        assertEquals(SkFontStyle.Slant.kUpright_Slant, n.slant)
        assertEquals(SkFontStyle.Slant.kUpright_Slant, b.slant)
        assertEquals(SkFontStyle.Slant.kItalic_Slant, i.slant)
        assertEquals(SkFontStyle.Slant.kItalic_Slant, bi.slant)
    }

    @Test
    fun `weight is clamped to kInvisible_Weight kExtraBlack_Weight per upstream`() {
        // Upstream: SkTPin<int>(weight, kInvisible_Weight, kExtraBlack_Weight).
        assertEquals(SkFontStyle.kInvisible_Weight, SkFontStyle(weight = -100).weight)
        assertEquals(SkFontStyle.kExtraBlack_Weight, SkFontStyle(weight = 9999).weight)
    }

    @Test
    fun `width is clamped to kUltraCondensed_Width kUltraExpanded_Width per upstream`() {
        assertEquals(SkFontStyle.kUltraCondensed_Width, SkFontStyle(width = 0).width)
        assertEquals(SkFontStyle.kUltraExpanded_Width, SkFontStyle(width = 99).width)
    }

    @Test
    fun `equals and hashCode honour all three axes`() {
        val a = SkFontStyle(SkFontStyle.kBold_Weight, SkFontStyle.kNormal_Width, SkFontStyle.Slant.kUpright_Slant)
        val b = SkFontStyle(SkFontStyle.kBold_Weight, SkFontStyle.kNormal_Width, SkFontStyle.Slant.kUpright_Slant)
        val c = SkFontStyle(SkFontStyle.kBold_Weight, SkFontStyle.kNormal_Width, SkFontStyle.Slant.kItalic_Slant)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `weight constants match upstream enum values`() {
        assertEquals(0, SkFontStyle.kInvisible_Weight)
        assertEquals(100, SkFontStyle.kThin_Weight)
        assertEquals(200, SkFontStyle.kExtraLight_Weight)
        assertEquals(300, SkFontStyle.kLight_Weight)
        assertEquals(400, SkFontStyle.kNormal_Weight)
        assertEquals(500, SkFontStyle.kMedium_Weight)
        assertEquals(600, SkFontStyle.kSemiBold_Weight)
        assertEquals(700, SkFontStyle.kBold_Weight)
        assertEquals(800, SkFontStyle.kExtraBold_Weight)
        assertEquals(900, SkFontStyle.kBlack_Weight)
        assertEquals(1000, SkFontStyle.kExtraBlack_Weight)
    }

    @Test
    fun `width constants match upstream enum values`() {
        assertEquals(1, SkFontStyle.kUltraCondensed_Width)
        assertEquals(5, SkFontStyle.kNormal_Width)
        assertEquals(9, SkFontStyle.kUltraExpanded_Width)
    }
}
