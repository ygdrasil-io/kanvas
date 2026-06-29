package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class VariableFontHandoffRouteTest {

    private fun resolvedVariableInstanceRun(): GPUGlyphRunDescriptor = GPUGlyphRunDescriptor(
        runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655440003")),
        typefaceID = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440013")),
        glyphIDs = listOf(10, 11, 12),
        advances = listOf(8f, 8f, 8f),
        script = "Latn",
        bidiLevel = 0,
    )

    @Test
    fun `variable font instance resolves to a static glyph run on the gpu handoff`() {
        val run = resolvedVariableInstanceRun()

        assertNotNull(run.typefaceID)
        assertEquals(listOf(10, 11, 12), run.glyphIDs)
        assertEquals(3, run.textRangeEnd)
        assertEquals(0, run.bidiLevel)
    }

    @Test
    fun `gpu handoff descriptor carries no variable font axis fields`() {
        val fieldNames = GPUGlyphRunDescriptor::class.java.declaredFields
            .map { field -> field.name.lowercase() }

        assertTrue(fieldNames.isNotEmpty())
        assertFalse(fieldNames.any { name -> name.contains("axis") })
        assertFalse(fieldNames.any { name -> name.contains("variation") })
        assertFalse(fieldNames.any { name -> name.contains("fvar") || name.contains("gvar") })
    }
}
