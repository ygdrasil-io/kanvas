package org.graphiks.kanvas

import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Brick 1: the bridge carries a real typeface + size into [TextBlob.lower] and
 * produces a real (rasterized) glyph atlas artifact + non-placeholder command
 * bounds, while keeping the typeface OUT of the normalized command.
 */
class TextBlobTypefaceTest {
    private val fontResource = "/fonts/liberation/LiberationSans-Regular.ttf"

    private fun fontBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream(fontResource)
        assertNotNull(stream, "Liberation Sans test font must be on the :kanvas test classpath")
        return stream.readBytes()
    }

    /** Builds a one-run blob for "ABC" using real glyph IDs from the font. */
    private fun abcBlob(size: Float): TextBlob {
        val scaler = GlyphScaler.fromBytes(fontBytes())
        val glyphIds = "ABC".map { ch ->
            val id = scaler.glyphIdForCodepoint(ch.code)
            assertNotNull(id, "font must map codepoint ${ch.code}")
            id.toUShort()
        }
        val positions = glyphIds.indices.map { KanvasPoint(it * 20f, 0f) }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphs = glyphIds, positions = positions)),
            typeface = KanvasTypeface(fontResource),
            fontSize = size,
        )
    }

    @Test
    fun `lower with typeface rasterizes a real non-empty A8 glyph atlas`() {
        val plan = abcBlob(32f).lower().atlasPlan
        val accepted = assertIs<GlyphAtlasUploadPlan.Accepted>(plan)
        assertTrue(accepted.atlasBytes.isNotEmpty(), "atlas bytes must be non-empty for real glyphs")
        assertTrue(accepted.placements.isNotEmpty(), "atlas must contain glyph placements")
        assertTrue(
            accepted.placements.any { it.region.width > 0 && it.region.height > 0 },
            "at least one placement must have a real (non-zero) atlas region",
        )
    }

    @Test
    fun `lower with typeface carries the real font size on every glyph strike`() {
        val descriptor = abcBlob(32f).lower()
        assertTrue(descriptor.glyphs.isNotEmpty(), "descriptor must carry glyph descriptors")
        assertTrue(
            descriptor.glyphs.all { it.strikeKey.size == 32f },
            "every glyph strike size must equal the real font size (32f), not a hardcoded default",
        )
    }

    @Test
    fun `drawTextBlob emits non-placeholder bounds derived from glyph metrics`() {
        val surface = Surface(width = 320, height = 240)
        val canvas = Canvas(surface)
        canvas.drawTextBlob(abcBlob(32f), 10f, 50f, Paint().color(1f, 1f, 1f, 1f))

        val cmd = surface.recorder.recordedCommands()
            .filterIsInstance<NormalizedDrawCommand.DrawTextRun>()
            .single()

        assertTrue(
            cmd.bounds != GPUBounds(10f, 50f, 110f, 70f),
            "bounds must not be the hardcoded x+100,y+20 placeholder",
        )
        assertTrue(
            cmd.bounds.right - cmd.bounds.left > 0f && cmd.bounds.bottom - cmd.bounds.top > 0f,
            "bounds must have a real positive extent derived from glyph metrics: ${cmd.bounds}",
        )
    }

    @Test
    fun `DrawTextRun does not leak the typeface or font resource into the command`() {
        val surface = Surface(width = 320, height = 240)
        val canvas = Canvas(surface)
        canvas.drawTextBlob(abcBlob(32f), 10f, 50f, Paint().color(1f, 1f, 1f, 1f))

        val cmd = surface.recorder.recordedCommands()
            .filterIsInstance<NormalizedDrawCommand.DrawTextRun>()
            .single()
        val dump = cmd.toString()

        assertFalse(dump.contains("KanvasTypeface"), "command must not carry a typeface")
        assertFalse(dump.contains("LiberationSans"), "command must not carry a font identity")
        assertFalse(dump.contains("/fonts/liberation"), "command must not carry a font resource path")
    }
}
