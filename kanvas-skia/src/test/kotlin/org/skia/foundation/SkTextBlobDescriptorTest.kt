package org.skia.foundation

import org.graphiks.kanvas.font.TypefaceID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class SkTextBlobDescriptorTest {

    @Test
    fun `typed descriptor factory produces correct runs with FullPositions`() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440001"))

        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = typefaceId,
                glyphIds = listOf(65, 66),
                positions = listOf(0f, 10f, 25f, 30f),
            ),
        )

        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)
        assertNotNull(blob)
        assertEquals(1, blob!!.runs.size)

        val run = blob.runs[0] as SkTextBlob.Run.FullPositions
        assertEquals(2, run.glyphIds.size)
        assertEquals(65, run.glyphIds[0])
        assertEquals(66, run.glyphIds[1])
        assertEquals(4, run.positions.size)
        assertEquals(0f, run.positions[0])
        assertEquals(10f, run.positions[1])
        assertEquals(25f, run.positions[2])
        assertEquals(30f, run.positions[3])
    }

    @Test
    fun `typed descriptor factory handles HorizontalSpread via advances`() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440002"))

        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = typefaceId,
                glyphIds = listOf(10, 20, 30),
                advances = listOf(8f, 12f, 10f),
                baseX = 5f,
                baseY = 20f,
            ),
        )

        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)
        assertNotNull(blob)
        assertEquals(1, blob!!.runs.size)

        val run = blob.runs[0] as SkTextBlob.Run.HorizontalSpread
        assertEquals(3, run.glyphIds.size)
        assertEquals(10, run.glyphIds[0])
        assertEquals(5f, run.x)
        assertEquals(20f, run.y)
    }

    @Test
    fun `existing builder path still works as fallback`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, count = 2, x = 10f, y = 50f)
        rec.glyphs[0] = 65; rec.glyphs[1] = 66
        val blob = builder.make()!!

        assertEquals(1, blob.runs.size)
        val run = blob.runs[0] as SkTextBlob.Run.HorizontalSpread
        assertEquals(10f, run.x)
        assertEquals(50f, run.y)
        assertEquals(2, run.glyphIds.size)
        assertEquals(65, run.glyphIds[0])
    }

    @Test
    fun `descriptor dump shows expected values`() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440003"))

        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = typefaceId,
                glyphIds = listOf(65, 66),
                positions = listOf(0f, 10f, 25f, 30f),
            ),
        )

        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)!!
        val dump = blob.typedDescriptorDump()

        assertEquals(2, dump.glyphCount)
        assertEquals(1, dump.descriptorCount)
        assertTrue(dump.noSkLeakage)
        assertTrue(dump.legacyGate.contains("dftext"))
    }

    @Test
    fun `empty adapters list returns null blob`() {
        val blob = SkTextBlob.MakeFromTypedDescriptors(emptyList())
        assertNull(blob)
    }

    @Test
    fun `multiple typed adapters produce multiple runs`() {
        val t1 = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440004"))
        val t2 = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440005"))

        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = t1,
                glyphIds = listOf(65),
                positions = listOf(0f, 0f),
            ),
            SkTextBlobGlyphRunAdapter(
                typefaceId = t2,
                glyphIds = listOf(66),
                positions = listOf(10f, 0f),
            ),
        )

        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)!!
        assertEquals(2, blob.runs.size)
    }

    @Test
    fun `descriptor dump preserves deterministic glyph order`() {
        val typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440006"))

        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = typefaceId,
                glyphIds = listOf(72, 73, 74),
                positions = listOf(0f, 0f, 10f, 0f, 25f, 0f),
            ),
        )

        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)!!
        val run = blob.runs[0] as SkTextBlob.Run.FullPositions
        assertEquals(72, run.glyphIds[0])
        assertEquals(73, run.glyphIds[1])
        assertEquals(74, run.glyphIds[2])
    }

    @Test
    fun `noSkLeakage check passes when all fields are value objects`() {
        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440007")),
                glyphIds = listOf(65),
                positions = listOf(0f, 0f),
            ),
        )
        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)!!
        val dump = blob.typedDescriptorDump()
        assertTrue(dump.noSkLeakage)
    }

    @Test
    fun `dftext legacy gate is visible in descriptor dump`() {
        val adapters = listOf(
            SkTextBlobGlyphRunAdapter(
                typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440008")),
                glyphIds = listOf(65),
                positions = listOf(0f, 0f),
            ),
        )
        val blob = SkTextBlob.MakeFromTypedDescriptors(adapters)!!
        val dump = blob.typedDescriptorDump()
        assertTrue(dump.legacyGate.contains("dftext")) {
            "Expected legacy gate 'dftext' to be visible, got: ${dump.legacyGate}"
        }
    }
}
