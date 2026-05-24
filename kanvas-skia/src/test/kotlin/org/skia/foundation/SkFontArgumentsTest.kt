package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.opentype.LiberationOpenTypeFontMgr
import org.skia.foundation.opentype.OpenTypeTypeface

/**
 * R-final.9 — sanity tests for the variable-font argument plumbing.
 * Verifies the data-class round-trip on [SkFontArguments] /
 * [SkFontArguments.VariationPosition] and the
 * [SkTypeface.makeClone] base-class identity contract.
 */
class SkFontArgumentsTest {

    @Test
    fun `VariationPosition round-trips coordinate list`() {
        val wght = SkFontVariation.Tag.of("wght")
        val wdth = SkFontVariation.Tag.of("wdth")
        val pos = SkFontArguments.VariationPosition(
            listOf(
                SkFontArguments.VariationPosition.Coordinate.of(wght, 700f),
                SkFontArguments.VariationPosition.Coordinate.of(wdth, 125f),
            )
        )
        assertEquals(2, pos.coordinates.size)
        assertEquals(wght.raw, pos.coordinates[0].axis)
        assertEquals(700f, pos.coordinates[0].value)
        assertEquals(wdth.raw, pos.coordinates[1].axis)
    }

    @Test
    fun `SkFontArguments fluent setters round-trip`() {
        val coord = SkFontArguments.VariationPosition.Coordinate.of(
            SkFontVariation.Tag.of("opsz"), 12f,
        )
        val args = SkFontArguments()
            .setVariationDesignPosition(SkFontArguments.VariationPosition(listOf(coord)))
            .setCollectionIndex(2)
        assertEquals(1, args.variationDesignPosition.coordinates.size)
        assertEquals(2, args.collectionIndex)
    }

    @Test
    fun `Base SkTypeface makeClone returns this unchanged`() {
        val empty = SkTypeface.MakeEmpty()
        val cloned = empty.makeClone(SkFontArguments())
        // Base-class identity contract — no axes to apply, returns this.
        assertSame(empty, cloned)
    }

    @Test
    fun `OpenTypeTypeface makeClone returns a new instance for empty args`() {
        val src = portableTypeface()
        val cloned = src.makeClone(SkFontArguments())
        assertNotNull(cloned)
        // OpenType clone path rebuilds the wrapper to match Skia's
        // "fresh sk_sp" contract. Identity differs but the typeface is valid.
        assertNotSame(src, cloned)
    }

    @Test
    fun `OpenTypeTypeface makeClone accepts wght axis without crashing`() {
        val src = portableTypeface()
        val args = SkFontArguments().setVariationDesignPosition(
            SkFontArguments.VariationPosition(
                listOf(
                    SkFontArguments.VariationPosition.Coordinate.of(
                        SkFontVariation.Tag.of("wght"), 700f,
                    )
                )
            )
        )
        val cloned = src.makeClone(args)
        assertNotNull(cloned, "makeClone should not return null for the bundled OpenType typeface")
        // The clone's typeface should be measurable — sanity: zero-width
        // text returns 0 advance, finite text returns positive.
        val font = SkFont(cloned!!, 12f)
        val advance = font.measureText("abc")
        assertTrue(advance > 0f, "Cloned typeface should still measure text")
    }

    @Test
    fun `OpenTypeTypeface makeClone silently drops unknown axis`() {
        val src = portableTypeface()
        val args = SkFontArguments().setVariationDesignPosition(
            SkFontArguments.VariationPosition(
                listOf(
                    SkFontArguments.VariationPosition.Coordinate.of(
                        SkFontVariation.Tag.of("opsz"), 24f,
                    ),
                    SkFontArguments.VariationPosition.Coordinate.of(
                        SkFontVariation.Tag.of("GRAD"), 100f,
                    ),
                )
            )
        )
        val cloned = src.makeClone(args)
        assertNotNull(cloned, "Unmappable axes should not block clone")
    }

    private fun portableTypeface(): OpenTypeTypeface =
        requireNotNull(
            LiberationOpenTypeFontMgr.Create().matchFamilyStyle("Liberation Sans", SkFontStyle.Normal())
        ) as OpenTypeTypeface
}
