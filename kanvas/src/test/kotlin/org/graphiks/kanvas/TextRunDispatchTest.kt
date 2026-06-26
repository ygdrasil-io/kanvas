package org.graphiks.kanvas

import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hermetic (no-GPU) coverage of the A8 text-run dispatch planner + CPU oracle.
 * The real GPU pixel + parity evidence is produced by the `kanvasTextGpuEvidence`
 * JavaExec task (native WebGPU), kept separate from these headless tests.
 */
class TextRunDispatchTest {
    private val fontResource = "/fonts/liberation/LiberationSans-Regular.ttf"
    private val surfaceWidth = 320
    private val surfaceHeight = 240

    private fun fontBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream(fontResource)
        assertNotNull(stream, "Liberation Sans test font must be on the :kanvas test classpath")
        return stream.readBytes()
    }

    private fun abcBlob(size: Float = 32f): TextBlob {
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

    private fun recordTextRun(blob: TextBlob, paint: Paint = Paint().color(1f, 1f, 1f, 1f)): NormalizedDrawCommand.DrawTextRun {
        val surface = Surface(width = surfaceWidth, height = surfaceHeight)
        Canvas(surface).drawTextBlob(blob, 10f, 50f, paint)
        return surface.recorder.recordedCommands()
            .filterIsInstance<NormalizedDrawCommand.DrawTextRun>()
            .single()
    }

    @Test
    fun `planner produces one quad per drawable glyph with 48-byte uniforms`() {
        val plan = TextRunDispatchPlanner.plan(recordTextRun(abcBlob()), surfaceWidth, surfaceHeight)
        val draws = assertIs<TextRunDispatchPlan.Draws>(plan)

        assertEquals(3, draws.placements.size, "ABC must produce 3 glyph quads")
        assertTrue(draws.atlasBytes.isNotEmpty(), "atlas bytes must be uploaded")
        assertTrue(draws.atlasWidth > 0 && draws.atlasHeight > 0)
        for (placement in draws.placements) {
            assertEquals(48, placement.uniformBytes().size, "uniform must be 48 bytes (3 vec4f)")
            assertTrue(placement.scissorX in 0 until surfaceWidth, "scissor X within surface")
            assertTrue(placement.scissorY in 0 until surfaceHeight, "scissor Y within surface")
            assertTrue(placement.scissorX + placement.scissorWidth <= surfaceWidth)
            assertTrue(placement.scissorY + placement.scissorHeight <= surfaceHeight)
            assertTrue(placement.uvWidth > 0f && placement.uvHeight > 0f, "atlas UV sub-rect must be non-empty")
        }
    }

    @Test
    fun `planner refuses a text run with no rasterized atlas`() {
        val noTypefaceBlob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphs = listOf(65u, 66u, 67u), positions = listOf(KanvasPoint(0f, 0f), KanvasPoint(20f, 0f), KanvasPoint(40f, 0f)))),
        )
        val plan = TextRunDispatchPlanner.plan(recordTextRun(noTypefaceBlob), surfaceWidth, surfaceHeight)
        val refused = assertIs<TextRunDispatchPlan.Refused>(plan)
        assertEquals("empty_atlas", refused.reason)
    }

    @Test
    fun `planner refuses a non-solid text material`() {
        val cmd = recordTextRun(abcBlob()).copy(
            material = GPUMaterialDescriptor.ImageDraw(imageSourceId = "img", imageWidth = 1, imageHeight = 1),
        )
        val plan = TextRunDispatchPlanner.plan(cmd, surfaceWidth, surfaceHeight)
        val refused = assertIs<TextRunDispatchPlan.Refused>(plan)
        assertTrue(refused.reason.startsWith("unsupported_material:"), "got ${refused.reason}")
    }

    @Test
    fun `CPU oracle composites non-transparent coverage for real glyphs`() {
        val plan = TextRunDispatchPlanner.plan(recordTextRun(abcBlob()), surfaceWidth, surfaceHeight)
        val draws = assertIs<TextRunDispatchPlan.Draws>(plan)
        val rgba = TextRunCpuOracle.composite(draws, surfaceWidth, surfaceHeight)

        assertEquals(surfaceWidth * surfaceHeight * 4, rgba.size)
        assertTrue(
            TextRunCpuOracle.nonTransparentPixels(rgba) > 0,
            "CPU oracle must paint visible glyph coverage",
        )
    }
}
