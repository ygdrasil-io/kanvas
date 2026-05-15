package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `AwtTypeface makeClone returns a new instance for empty args`() {
        val src = org.skia.foundation.awt.AwtTypeface.DEFAULT
        val cloned = src.makeClone(SkFontArguments())
        assertNotNull(cloned)
        // AWT clone path always rebuilds the wrapper to match Skia's
        // "always a fresh sk_sp" contract. Identity differs but the
        // typeface is valid.
        assertNotSame(src, cloned)
    }

    @Test
    fun `AwtTypeface makeClone honours wght axis without crashing`() {
        val src = org.skia.foundation.awt.AwtTypeface.DEFAULT
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
        assertNotNull(cloned, "makeClone should never return null for AwtTypeface")
        // The clone's typeface should be measurable — sanity: zero-width
        // text returns 0 advance, finite text returns positive.
        val font = SkFont(cloned!!, 12f)
        val advance = font.measureText("abc")
        assertTrue(advance > 0f, "Cloned typeface should still measure text")
    }

    @Test
    fun `AwtTypeface makeClone silently drops unknown axis`() {
        val src = org.skia.foundation.awt.AwtTypeface.DEFAULT
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
}
