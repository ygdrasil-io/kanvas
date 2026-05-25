package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkShaperTest {
    @Test
    fun `primitive shaper returns empty result for empty text`() {
        val result = SkShaper.MakePrimitive().shape("", SkFont(TestTypeface(mapOf('A'.code to 1)), 12f))

        assertEquals(0, result.glyphCount)
        assertTrue(result.runs.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `bidi segmentation emits rtl run with stable original clusters`() {
        val font = SkFont(TestTypeface(mapAll = true), 12f)
        val result = SkShaper.MakePrimitive().shape("abc \u05D0\u05D1\u05D2", font)

        val latinRun = result.runs.first { it.script == Character.UnicodeScript.LATIN }
        assertEquals(SkShaper.Direction.Ltr, latinRun.direction)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), latinRun.clusters)

        val hebrewRun = result.runs.first { it.script == Character.UnicodeScript.HEBREW }
        assertEquals(SkShaper.Direction.Rtl, hebrewRun.direction)
        assertArrayEquals(intArrayOf(6, 5, 4), hebrewRun.clusters)
    }

    @Test
    fun `script itemization splits Arabic and Devanagari baselines`() {
        val font = SkFont(TestTypeface(mapAll = true), 12f)
        val result = SkShaper.MakePrimitive().shape("\u0628\u0905", font)

        assertTrue(result.runs.any { it.script == Character.UnicodeScript.ARABIC })
        assertTrue(result.runs.any { it.script == Character.UnicodeScript.DEVANAGARI })
        assertEquals(2, result.glyphCount)
    }

    @Test
    fun `arabic joining feature maps to presentation forms when available`() {
        val font = SkFont(
            TestTypeface(
                mapOf(
                    0x0628 to 10,
                    0xFE91 to 91,
                    0xFE90 to 90,
                ),
            ),
            12f,
        )
        val result = SkShaper.MakePrimitive().shape(
            "\u0628\u0628",
            font,
            SkShaper.Features(arabicJoining = true),
        )

        assertEquals(Character.UnicodeScript.ARABIC, result.runs.single().script)
        assertEquals(SkShaper.Direction.Rtl, result.runs.single().direction)
        assertArrayEquals(intArrayOf(91, 90), result.runs.single().glyphs)
        assertArrayEquals(intArrayOf(1, 0), result.runs.single().clusters)
    }

    @Test
    fun `indic reordering moves Devanagari prebase vowel before consonant glyph`() {
        val font = SkFont(TestTypeface(mapOf(0x0915 to 15, 0x093F to 39)), 12f)
        val result = SkShaper.MakePrimitive().shape(
            "\u0915\u093F",
            font,
            SkShaper.Features(indicReordering = true),
        )

        assertEquals(Character.UnicodeScript.DEVANAGARI, result.runs.single().script)
        assertArrayEquals(intArrayOf(39, 15), result.runs.single().glyphs)
        assertArrayEquals(intArrayOf(1, 0), result.runs.single().clusters)
    }

    @Test
    fun `script language policy only applies to matching script`() {
        val font = SkFont(TestTypeface(mapOf('f'.code to 20, 'i'.code to 21, 0xFB01 to 222, 0x0628 to 8)), 12f)
        val result = SkShaper.MakePrimitive().shape(
            "fi\u0628",
            font,
            SkShaper.Features(
                standardLigatures = true,
                scriptLanguage = SkShaper.ScriptLanguage(
                    script = Character.UnicodeScript.ARABIC,
                    language = "ARA",
                ),
            ),
        )

        assertEquals(
            listOf(SkShaper.Diagnostic.Kind.ScriptLanguageMismatch),
            result.diagnostics.map { it.kind },
        )
        assertEquals(0, result.diagnostics.single().utf16Index)
        assertArrayEquals(intArrayOf(20, 21), result.runs.first().glyphs)
    }

    @Test
    fun `positioning provider applies mark and cursive deltas when features are enabled`() {
        val font = SkFont(TestTypeface(mapOf(0x0628 to 10, 0x0650 to 50)), 12f)
        val shaper = SkShaper.MakeWithProviders { run, features ->
            assertTrue(features.markPositioning)
            assertTrue(features.cursiveAttachment)
            Array(run.glyphs.size) { i ->
                if (i == 0) org.graphiks.math.SkPoint(2f, -3f) else org.graphiks.math.SkPoint(4f, 0f)
            }
        }

        val result = shaper.shape(
            "\u0628\u0650",
            font,
            SkShaper.Features(markPositioning = true, cursiveAttachment = true),
        )

        val run = result.runs.single()
        assertEquals(2f, run.positions[0].fX)
        assertEquals(-3f, run.positions[0].fY)
        assertEquals(16f, run.positions[1].fX)
        assertEquals(0f, run.positions[1].fY)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `positioning feature without provider fails closed with diagnostics`() {
        val font = SkFont(TestTypeface(mapOf('A'.code to 1, 0x0628 to 10, 0x0650 to 50)), 12f)
        val result = SkShaper.MakePrimitive().shape(
            "A\u0628\u0650",
            font,
            SkShaper.Features(markPositioning = true, cursiveAttachment = true),
        )

        assertEquals(
            listOf(
                SkShaper.Diagnostic.Kind.UnsupportedFeature,
                SkShaper.Diagnostic.Kind.UnsupportedFeature,
                SkShaper.Diagnostic.Kind.UnsupportedFeature,
                SkShaper.Diagnostic.Kind.UnsupportedFeature,
            ),
            result.diagnostics.map { it.kind },
        )
    }

    @Test
    fun `fallback splits runs without losing cluster continuity`() {
        val baseTypeface = TestTypeface(
            mapOf('A'.code to 10, 'B'.code to 11),
        )
        val fallbackTypeface = TestTypeface(
            mapOf(0x1F600 to 900),
        )
        val baseFont = SkFont(baseTypeface, 12f)
        val fallbackFont = SkFont(fallbackTypeface, 12f)
        val shaper = SkShaper.MakeWithFallback { _, _ -> fallbackFont }

        val result = shaper.shape("A\uD83D\uDE00B", baseFont)

        assertEquals(3, result.runs.size)
        assertSame(baseTypeface, result.runs[0].font.typeface)
        assertArrayEquals(intArrayOf(0), result.runs[0].clusters)
        assertSame(fallbackTypeface, result.runs[1].font.typeface)
        assertArrayEquals(intArrayOf(1), result.runs[1].clusters)
        assertArrayEquals(intArrayOf(900), result.runs[1].glyphs)
        assertSame(baseTypeface, result.runs[2].font.typeface)
        assertArrayEquals(intArrayOf(3), result.runs[2].clusters)
        assertEquals(listOf(SkShaper.Diagnostic.Kind.FallbackUsed), result.diagnostics.map { it.kind })
    }

    @Test
    fun `missing glyph fails closed with notdef and diagnostic`() {
        val font = SkFont(TestTypeface(mapOf('A'.code to 10)), 12f)
        val result = SkShaper.MakePrimitive().shape("AZ", font)

        assertEquals(2, result.glyphCount)
        assertArrayEquals(intArrayOf(10, 0), result.runs.single().glyphs)
        assertEquals(SkShaper.Diagnostic.Kind.MissingGlyph, result.diagnostics.single().kind)
        assertEquals(1, result.diagnostics.single().utf16Index)
    }

    @Test
    fun `standard ligature feature is opt in`() {
        val font = SkFont(
            TestTypeface(
                mapOf('f'.code to 20, 'i'.code to 21, 0xFB01 to 222),
            ),
            12f,
        )
        val shaper = SkShaper.MakePrimitive()

        val plain = shaper.shape("fi", font)
        assertEquals(2, plain.glyphCount)
        assertArrayEquals(intArrayOf(20, 21), plain.runs.single().glyphs)

        val ligatured = shaper.shape("fi", font, SkShaper.Features(standardLigatures = true))
        assertEquals(1, ligatured.glyphCount)
        assertArrayEquals(intArrayOf(222), ligatured.runs.single().glyphs)
        assertArrayEquals(intArrayOf(0), ligatured.runs.single().clusters)
    }

    private class TestTypeface(
        private val glyphs: Map<Int, Int> = emptyMap(),
        private val mapAll: Boolean = false,
    ) : SkTypeface() {
        override fun unicharsToGlyphsInternal(unichars: IntArray, count: Int, glyphs: ShortArray) {
            for (i in 0 until count) {
                val glyph = if (mapAll) {
                    (unichars[i] and 0x7FFF).coerceAtLeast(1)
                } else {
                    this.glyphs[unichars[i]] ?: 0
                }
                glyphs[i] = glyph.toShort()
            }
        }

        override fun getGlyphWidthInternal(
            glyphId: Int,
            size: Float,
            scaleX: Float,
            skewX: Float,
        ): Float = if (glyphId == 0) 0f else size * scaleX
    }
}
