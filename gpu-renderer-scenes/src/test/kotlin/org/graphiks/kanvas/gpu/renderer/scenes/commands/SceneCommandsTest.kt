package org.graphiks.kanvas.gpu.renderer.scenes.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand

class SceneCommandsTest {
    @Test
    fun `fill rect command converts to gpu renderer normalized command`() {
        val target = SceneTarget(width = 320, height = 200, colorFormat = "bgra8unorm")
        val command = SceneCommand.FillRect(
            label = "front-card",
            rect = SceneRect(24f, 32f, 180f, 112f),
            color = SceneColor(0.1f, 0.35f, 0.9f, 0.88f),
            paintOrder = 2,
        )

        val normalized = command.toNormalizedFillRect(commandIndex = 7, target = target)

        assertIs<NormalizedDrawCommand.FillRect>(normalized)
        assertEquals(7, normalized.commandId.value)
        assertEquals(GPURect(24f, 32f, 180f, 112f), normalized.rect)
        assertEquals(320, normalized.layer.target.width)
        assertEquals(200, normalized.layer.target.height)
        assertEquals("bgra8unorm", normalized.layer.target.colorFormat)
        assertEquals("gpu-renderer-scenes", normalized.source.adapter)
        assertEquals("front-card", normalized.source.operation)
        assertEquals(2, normalized.ordering.paintOrder)
        val material = assertIs<GPUMaterialDescriptor.SolidColor>(normalized.material)
        assertEquals(0.1f, material.r)
        assertEquals(0.35f, material.g)
        assertEquals(0.9f, material.b)
        assertEquals(0.88f, material.a)
    }

    @Test
    fun `scene command family exposes business friendly names`() {
        assertEquals("fill-rect", SceneCommand.FillRect("card", SceneRect(0f, 0f, 8f, 8f), SceneColor.red()).family)
        assertEquals(
            "linear-gradient-rect",
            SceneCommand.LinearGradientRect(
                "gradient",
                SceneRect(0f, 0f, 8f, 8f),
                SceneColor.red(),
                SceneColor.blue(),
            ).family,
        )
        assertEquals("clip", SceneCommand.Clip("clip", SceneRect(0f, 0f, 8f, 8f)).family)
        assertEquals("runtime-effect", SceneCommand.RuntimeEffectTile("simple-rt").family)
        assertEquals("vertices", SceneCommand.MeshRibbon("mesh").family)
    }

    @Test
    fun `label bearing scene commands reject blank labels`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FillRect(" ", SceneRect(0f, 0f, 8f, 8f), SceneColor.red())
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.LinearGradientRect(
                " ",
                SceneRect(0f, 0f, 8f, 8f),
                SceneColor.red(),
                SceneColor.blue(),
            )
        }
        assertFailsWith<IllegalArgumentException> { SceneCommand.Clip("", SceneRect(0f, 0f, 8f, 8f)) }
        assertFailsWith<IllegalArgumentException> { SceneCommand.RuntimeEffectTile("") }
        assertFailsWith<IllegalArgumentException> { SceneCommand.MeshRibbon("\t") }
    }

    @Test
    fun `fill rect command rejects negative paint order`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FillRect("card", SceneRect(0f, 0f, 8f, 8f), SceneColor.red(), paintOrder = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.LinearGradientRect(
                "gradient",
                SceneRect(0f, 0f, 8f, 8f),
                SceneColor.red(),
                SceneColor.blue(),
                paintOrder = -1,
            )
        }
    }

    @Test
    fun `scene rect rejects non finite coordinates`() {
        val infinite = assertFailsWith<IllegalArgumentException> {
            SceneRect(0f, 0f, Float.POSITIVE_INFINITY, 8f)
        }
        val nan = assertFailsWith<IllegalArgumentException> {
            SceneRect(0f, Float.NaN, 8f, 8f)
        }

        assertTrue(infinite.message.orEmpty().contains("finite"))
        assertTrue(nan.message.orEmpty().contains("finite"))
    }

    @Test
    fun `scene color rejects channels outside normalized range`() {
        assertFailsWith<IllegalArgumentException> { SceneColor(1.1f, 0f, 0f) }
    }
}
