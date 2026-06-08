package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.tools.ToolUtils
import java.io.File

class SkCpuGlyphCacheTest {
    @Test
    fun `simple latin scope builds deterministic cpu glyph masks and diagnostics`() {
        val font = ToolUtils.DefaultPortableFont(32f)
        val text = "Kanvas Latin 0123456789 ABC xyz."
        val cache = SkCpuGlyphCache.build(
            scopeId = SimpleLatinScopeId,
            fontSourceId = LiberationSansRegularSourceId,
            font = font,
            text = text,
        )

        assertEquals(SimpleLatinScopeId, cache.scopeId)
        assertEquals(LiberationSansRegularSourceId, cache.fontSourceId)
        assertEquals("Liberation Sans", cache.fontFamily)
        assertEquals(text.codePoints().count().toInt(), cache.inventory.size)
        assertTrue(cache.glyphs.size < cache.inventory.size, "cache should dedupe repeated glyph keys")
        assertEquals(cache.glyphs.distinctBy { it.key }.size, cache.glyphs.size)

        val firstInventory = cache.inventory.first()
        val first = cache.glyphs.first { it.key == firstInventory.key }
        assertEquals('K'.code, first.codePoint)
        assertTrue(first.glyphId > 0)
        assertTrue(first.advance > 0f)
        assertTrue(first.mask.width > 0)
        assertTrue(first.mask.height > 0)
        assertTrue(first.mask.nonZeroPixels > 0)
        assertEquals(first.mask.width * first.mask.height, first.mask.pixels.size)
        assertNotEquals(SkCpuGlyphMask.EmptyHash, first.mask.sha256)

        val space = cache.glyphs.first { it.codePoints.contains(' '.code) }
        assertTrue(space.advance > 0f)
        assertEquals(0, space.mask.width)
        assertEquals(0, space.mask.height)
        assertEquals(0, space.mask.nonZeroPixels)
        assertEquals(SkCpuGlyphMask.EmptyHash, space.mask.sha256)

        val rebuilt = SkCpuGlyphCache.build(
            scopeId = SimpleLatinScopeId,
            fontSourceId = LiberationSansRegularSourceId,
            font = font,
            text = text,
        )
        assertEquals(cache, rebuilt)
        assertEquals(cache.dumpSha256, rebuilt.dumpSha256)
        assertEquals(cache.toJson(), rebuilt.toJson())

        val out = File("build/reports/kan-010-glyph-cache/simple-latin-glyph-cache.json")
        out.parentFile.mkdirs()
        out.writeText(cache.toJson())
        assertTrue(out.readText().contains("\"representation\": \"font.glyph.alpha-mask\""))
    }

    @Test
    fun `missing glyph scene records notdef diagnostic without fallback font`() {
        val font = ToolUtils.DefaultPortableFont(32f)
        val cache = SkCpuGlyphCache.build(
            scopeId = SimpleLatinScopeId,
            fontSourceId = LiberationSansRegularSourceId,
            font = font,
            text = "ABC\uE000XYZ",
        )

        val missing = cache.glyphs.single { it.codePoint == 0xE000 }
        assertEquals(0, missing.glyphId)
        assertEquals("font.missing-glyph.notdef-used", missing.diagnostic)
        assertTrue(cache.diagnostics.contains("font.missing-glyph.notdef-used"))
        assertFalse(cache.diagnostics.contains("font.fallback-family-used"))

        val out = File("build/reports/kan-010-glyph-cache/simple-latin-missing-glyph-cache.json")
        out.parentFile.mkdirs()
        out.writeText(cache.toJson())
        assertTrue(out.readText().contains("\"diagnostic\": \"font.missing-glyph.notdef-used\""))
    }

    private companion object {
        const val SimpleLatinScopeId = "text.simple-latin.liberation-sans-regular.v1"
        const val LiberationSansRegularSourceId =
            "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf#sha256=76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8"
    }
}
