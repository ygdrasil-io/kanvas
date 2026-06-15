package org.graphiks.kanvas.gpu.renderer.scenes.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand

class SceneCommandsTest {
    @Test
    fun `fill rect command converts to gpu renderer normalized command`() {
        val target = SceneTarget(width = 320, height = 200)
        val command = SceneCommand.FillRect(
            label = "front-card",
            rect = SceneRect(24f, 32f, 180f, 112f),
            color = SceneColor(0.1f, 0.35f, 0.9f, 0.88f),
            paintOrder = 2,
        )

        val normalized = command.toNormalizedFillRect(commandIndex = 7, target = target)

        assertIs<NormalizedDrawCommand.FillRect>(normalized)
        assertEquals(7, normalized.commandId.value)
        assertEquals("front-card", normalized.source.operation)
        assertEquals(2, normalized.ordering.paintOrder)
        assertEquals(
            0.88f,
            (normalized.material as org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor).a,
        )
    }

    @Test
    fun `scene command family exposes business friendly names`() {
        assertEquals("fill-rect", SceneCommand.FillRect("card", SceneRect(0f, 0f, 8f, 8f), SceneColor.red()).family)
        assertEquals("runtime-effect", SceneCommand.RuntimeEffectTile("simple-rt").family)
        assertEquals("vertices", SceneCommand.MeshRibbon("mesh").family)
    }
}
