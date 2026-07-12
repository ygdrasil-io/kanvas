package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter

class GPURefusalGuardsTest {

    @Test
    fun `nonidentity rect blur refuses before direct dispatch`() {
        assertEquals(
            "unsupported.mask-filter.blur.executor_unavailable",
            rect(maskFilter = blur(1f)).fillGuardRefusalReasonOrNull(),
        )
    }

    @Test
    fun `nonidentity rrect blur refuses before direct dispatch`() {
        assertEquals(
            "unsupported.mask-filter.blur.executor_unavailable",
            rrect(maskFilter = blur(1f)).fillGuardRefusalReasonOrNull(),
        )
    }

    @Test
    fun `non solid blur reports its material kind`() {
        val material = GPUMaterialDescriptor.RuntimeEffect()
        val command = rect(material = material, maskFilter = blur(1f))

        assertEquals(
            "unsupported.mask-filter.blur.material.${material.kind.name}",
            command.fillGuardRefusalReasonOrNull(),
        )
    }

    @Test
    fun `zero sigma blur leaves rect and rrect eligible for direct dispatch`() {
        assertNull(rect(maskFilter = blur(0f)).fillGuardRefusalReasonOrNull())
        assertNull(rrect(maskFilter = blur(0f)).fillGuardRefusalReasonOrNull())
    }

    private fun rect(
        material: GPUMaterialDescriptor = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
        maskFilter: NormalizedMaskFilter? = null,
    ) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(1),
        rect = GPURect(0f, 0f, 8f, 8f),
        target = target,
        material = material,
    ).copy(maskFilter = maskFilter)

    private fun rrect(
        maskFilter: NormalizedMaskFilter? = null,
    ) = GPUFillRRectCommandBuilder.build(
        commandId = GPUDrawCommandID(2),
        rrect = GPURRect(GPURect(0f, 0f, 8f, 8f), radiusX = 2f, radiusY = 2f),
        target = target,
        material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
    ).copy(maskFilter = maskFilter)

    private fun blur(sigma: Float) = NormalizedMaskFilter.Blur(
        style = NormalizedBlurStyle.NORMAL,
        sigma = sigma,
    )

    private companion object {
        val target = GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm")
    }
}
