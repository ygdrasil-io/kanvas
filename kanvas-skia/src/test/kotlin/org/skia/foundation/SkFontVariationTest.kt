package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase I2.2 (light) — covers [SkFontVariation], [SkFontVariation.Tag],
 * and the new `variations` property on [SkFont].
 *
 * Backend integration (rasterising at a specific design position) is
 * deferred ; these tests pin the data-shape contract so future ports
 * that read upstream binary blobs round-trip the tag layout
 * (big-endian 4-byte ASCII).
 */
class SkFontVariationTest {

    @Test
    fun `Tag Make packs ASCII into big-endian uint32`() {
        // 'wght' = 0x77 0x67 0x68 0x74
        val tag = SkFontVariation.Tag.Make('w', 'g', 'h', 't')
        assertEquals(0x77676874, tag.raw)
    }

    @Test
    fun `Tag of parses 4-character string`() {
        assertEquals(0x77676874, SkFontVariation.Tag.of("wght").raw)
        assertEquals(0x77647468, SkFontVariation.Tag.of("wdth").raw)
        assertEquals(0x736C6E74, SkFontVariation.Tag.of("slnt").raw)
        assertEquals(0x6F70737A, SkFontVariation.Tag.of("opsz").raw)
        assertEquals(0x6974616C, SkFontVariation.Tag.of("ital").raw)
    }

    @Test
    fun `Tag of rejects non-4-character input`() {
        assertThrows(IllegalArgumentException::class.java) { SkFontVariation.Tag.of("wgh") }
        assertThrows(IllegalArgumentException::class.java) { SkFontVariation.Tag.of("wghts") }
        assertThrows(IllegalArgumentException::class.java) { SkFontVariation.Tag.of("") }
    }

    @Test
    fun `Tag toString round-trips via 4 ASCII characters`() {
        assertEquals("wght", SkFontVariation.Tag.of("wght").toString())
        assertEquals("wdth", SkFontVariation.Tag.of("wdth").toString())
        // Round-trip via the raw int form.
        val raw = SkFontVariation.Tag.of("opsz").raw
        assertEquals("opsz", SkFontVariation.Tag(raw).toString())
    }

    @Test
    fun `companion constants point at the canonical OpenType tags`() {
        assertEquals("wght", SkFontVariation.WEIGHT.toString())
        assertEquals("wdth", SkFontVariation.WIDTH.toString())
        assertEquals("slnt", SkFontVariation.SLANT.toString())
        assertEquals("ital", SkFontVariation.ITALIC.toString())
        assertEquals("opsz", SkFontVariation.OPTICAL_SIZE.toString())
    }

    @Test
    fun `data class equality keys on (axis, value)`() {
        val a = SkFontVariation.of(SkFontVariation.WEIGHT, 700f)
        val b = SkFontVariation.of(SkFontVariation.WEIGHT, 700f)
        val c = SkFontVariation.of(SkFontVariation.WEIGHT, 400f)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `Tag-based factory delegates to Int-based ctor`() {
        val byTag = SkFontVariation.of(SkFontVariation.WIDTH, 125f)
        val byInt = SkFontVariation(SkFontVariation.Tag.of("wdth").raw, 125f)
        assertEquals(byTag, byInt)
    }

    @Test
    fun `SkFont default variations list is empty`() {
        assertTrue(SkFont().variations.isEmpty())
    }

    @Test
    fun `SkFont copy ctor propagates variations list`() {
        val src = SkFont().also {
            it.variations = listOf(
                SkFontVariation.of(SkFontVariation.WEIGHT, 850f),
                SkFontVariation.of(SkFontVariation.WIDTH, 110f),
            )
        }
        val dst = SkFont(src)
        assertEquals(src.variations, dst.variations)
    }
}
