package org.graphiks.kanvas.gpu.renderer.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NormalizedDrawCommandTest {
    @Test
    fun `fill rect command captures state required by GPU renderer core`() {
        val command = NormalizedDrawCommand.FillRect(
            commandId = GPUCommandId(7),
            rect = GPURect(left = 1f, top = 2f, right = 11f, bottom = 22f),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 64f, 64f)),
            layer = GPULayerFacts.root(
                target = GPUTargetFacts(
                    width = 64,
                    height = 64,
                    colorFormat = "rgba8unorm",
                ),
            ),
            material = GPUMaterialDescriptor.SolidColor(
                r = 1f,
                g = 0.25f,
                b = 0.5f,
                a = 1f,
            ),
            bounds = GPUBounds(left = 1f, top = 2f, right = 11f, bottom = 22f),
            ordering = GPUOrderingFacts(
                paintOrder = 3,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = GPUCommandSource(adapter = "unit-test", operation = "fillRect"),
        )

        assertEquals(GPUDrawKind.FillRect, command.drawKind)
        assertEquals(GPUTransformType.Identity, command.transform.type)
        assertEquals(GPUClipKind.WideOpen, command.clip.kind)
        assertEquals(GPUMaterialKind.SolidColor, command.material.kind)
        assertFalse(command.ordering.dependsOnDestination)
        assertEquals("unit-test:fillRect#7", command.diagnosticName)
    }
}
