package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter

class GPURefusalGuardsTest {

    @Test
    fun `direct fill guard delegates gradient facts to renderer authority`() {
        val source = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURefusalGuards.kt",
        ).readText()

        assertTrue("gradientFactsRefusalReasonOrNull()" in source)
        assertFalse("DIRECT_GRADIENT_IDENTITY_MATRIX" in source)
        assertFalse("gradientDispatchRefusalReasonOrNull" in source)
        assertTrue(
            source.indexOf("if (material is GPUMaterialDescriptor.Unsupported)") <
                source.indexOf("gradientFactsRefusalReasonOrNull()"),
        )
    }

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

    @Test
    fun `direct fill guard refuses radial and sweep non srgb facts before dispatch`() {
        listOf(radial().withGradientFacts(GPUMaterialDescriptor.GradientFacts("linear")), sweep().withGradientFacts(GPUMaterialDescriptor.GradientFacts("linear")))
            .forEach { material ->
                assertEquals(
                    "unsupported.material.mapping.gradient_interpolation",
                    rect(material = material).fillGuardRefusalReasonOrNull(),
                )
                assertEquals(
                    "unsupported.material.mapping.gradient_interpolation",
                    path(material).fillGuardRefusalReasonOrNull(),
                )
            }
    }

    @Test
    fun `direct fill guard refuses radial and sweep non identity matrix facts before dispatch`() {
        val facts = GPUMaterialDescriptor.GradientFacts(
            localMatrix = listOf(
                1f, 0f, 2f,
                0f, 1f, 3f,
                0f, 0f, 1f,
            ),
        )
        listOf(radial().withGradientFacts(facts), sweep().withGradientFacts(facts))
            .forEach { material ->
                assertEquals(
                    "unsupported.material.mapping.local_matrix",
                    rect(material = material).fillGuardRefusalReasonOrNull(),
                )
                assertEquals(
                    "unsupported.material.mapping.local_matrix",
                    path(material).fillGuardRefusalReasonOrNull(),
                )
            }
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

    private fun path(material: GPUMaterialDescriptor) = GPUFillPathCommandBuilder.build(
        commandId = GPUDrawCommandID(3),
        pathKey = "path:triangle:v1",
        pathDescriptor = GPUPathFacts(
            pathKey = "path:triangle:v1",
            verbCount = 4,
            pointCount = 3,
            fillRule = "NonZero",
            inverseFill = false,
            finiteProof = "finite",
            volatility = "immutable",
            transformClass = "identity",
            edgeCount = 3,
        ),
        tessellatedVertices = listOf(0f, 0f, 8f, 0f, 4f, 8f),
        contourStarts = listOf(0),
        edgeCount = 3,
        target = target,
        material = material,
    )

    private fun radial() = GPUMaterialDescriptor.RadialGradient(
        centerX = 4f,
        centerY = 4f,
        radius = 4f,
        startR = 1f,
        startG = 0f,
        startB = 0f,
        startA = 1f,
        endR = 0f,
        endG = 0f,
        endB = 1f,
        endA = 1f,
    )

    private fun sweep() = GPUMaterialDescriptor.SweepGradient(
        centerX = 4f,
        centerY = 4f,
        startAngle = 0f,
        endAngle = 360f,
        startR = 1f,
        startG = 0f,
        startB = 0f,
        startA = 1f,
        endR = 0f,
        endG = 0f,
        endB = 1f,
        endA = 1f,
    )

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
