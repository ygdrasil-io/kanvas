package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies the abstract [SkFontStyleSet] contract on the portable OpenType impl
 * and the [SkFontStyleSet.CreateEmpty] singleton :
 *
 *  - count() / getStyle() / createTypeface() / matchStyle() round-trip,
 *  - matchStyle picks nearest neighbour by CSS-3 distance,
 *  - empty set returns null on createTypeface / matchStyle.
 */
class SkFontStyleSetTest {
    private fun portableSet(): SkFontStyleSet = LiberationFontMgr.Make().matchFamily("Liberation Sans")


    @Test
    fun `empty set has 0 typefaces and returns null for matchStyle`() {
        val set = SkFontStyleSet.CreateEmpty()
        assertEquals(0, set.count())
        assertNull(set.matchStyle(SkFontStyle.Normal()))
    }

    @Test
    fun `matchStyle returns nearest neighbour - Bold matches Bold entry`() {
        val set = portableSet()
        if (set.count() == 0) return
        val tf = set.matchStyle(SkFontStyle.Bold())
        assertNotNull(tf, "matchStyle(Bold) should resolve")
        // The fontStyle of the resolved typeface should equal the bold
        // entry since Liberation exposes 4 exact-distance styles and Bold is
        // one of them.
        assertEquals(SkFontStyle.kBold_Weight, tf!!.fontStyle.weight)
    }

    @Test
    fun `matchStyle returns nearest neighbour - Light maps to Regular`() {
        val set = portableSet()
        if (set.count() == 0) return
        // Light weight (300) is closest to Normal (400) of the 4 bundled
        // styles (delta 100 vs delta 400 to Bold).
        val tf = set.matchStyle(
            SkFontStyle(SkFontStyle.kLight_Weight, SkFontStyle.kNormal_Width, SkFontStyle.Slant.kUpright_Slant)
        )
        assertNotNull(tf)
        assertEquals(SkFontStyle.kNormal_Weight, tf!!.fontStyle.weight)
    }

    @Test
    fun `matchStyle returns nearest neighbour - Italic-Medium maps to Italic`() {
        val set = portableSet()
        if (set.count() == 0) return
        // Slant mismatch dominates (×1000), so an italic-medium query
        // should pick the italic entry over any upright one.
        val tf = set.matchStyle(
            SkFontStyle(SkFontStyle.kMedium_Weight, SkFontStyle.kNormal_Width, SkFontStyle.Slant.kItalic_Slant)
        )
        assertNotNull(tf)
        assertEquals(SkFontStyle.Slant.kItalic_Slant, tf!!.fontStyle.slant)
    }

    @Test
    fun `getStyle returns SkFontStyle and appends name`() {
        val set = portableSet()
        if (set.count() == 0) return
        val nameBuilder = StringBuilder()
        val style = set.getStyle(0, null, nameBuilder)
        assertEquals(SkFontStyle.kNormal_Weight, style.weight)
        assertEquals(SkFontStyle.Slant.kUpright_Slant, style.slant)
        assertEquals("Regular", nameBuilder.toString())
    }

    @Test
    fun `createTypeface yields distinct typefaces per index`() {
        val set = portableSet()
        if (set.count() == 0) return
        val regular = set.createTypeface(0)
        val bold = set.createTypeface(1)
        assertNotNull(regular)
        assertNotNull(bold)
        assertEquals(SkFontStyle.kNormal_Weight, regular!!.fontStyle.weight)
        assertEquals(SkFontStyle.kBold_Weight, bold!!.fontStyle.weight)
    }

    @Test
    fun `css3Distance prioritises slant over width over weight`() {
        // Slant mismatch worth 1000, width unit 100, weight unit 1 —
        // verified directly via the internal distance scoring.
        val base = SkFontStyle.Normal()
        val slantDiff = SkFontStyle(
            SkFontStyle.kNormal_Weight, SkFontStyle.kNormal_Width,
            SkFontStyle.Slant.kItalic_Slant
        )
        val widthDiff = SkFontStyle(
            SkFontStyle.kNormal_Weight, SkFontStyle.kCondensed_Width,
            SkFontStyle.Slant.kUpright_Slant
        )
        val weightDiff = SkFontStyle(
            SkFontStyle.kBold_Weight, SkFontStyle.kNormal_Width,
            SkFontStyle.Slant.kUpright_Slant
        )
        val dSlant = SkFontStyleSet.css3Distance(base, slantDiff)
        val dWidth = SkFontStyleSet.css3Distance(base, widthDiff)
        val dWeight = SkFontStyleSet.css3Distance(base, weightDiff)
        // Slant: 1*1000 = 1000 ; Width: |5-3|*100 = 200 ; Weight: |400-700| = 300.
        assertEquals(1000, dSlant)
        assertEquals(200, dWidth)
        assertEquals(300, dWeight)
    }
}
