package org.graphiks.kanvas.gpu.renderer.commands

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the shared predicate used by analysis and both Kanvas lowering consumers. */
class NativePathHairlineContractTest {
    @Test
    fun `accepts horizontal and vertical bounded hairlines`() {
        assertTrue(command(listOf(4f, 8f, 20f, 8f)).isBoundedNativePathHairline())
        assertTrue(command(listOf(12f, 4f, 12f, 20f)).isBoundedNativePathHairline())
    }

    @Test
    fun `rejects scale antialiasing and diagonal hairlines`() {
        assertFalse(command(listOf(4f, 8f, 20f, 8f), transform = GPUTransformFacts.scale(2f, 2f))
            .isBoundedNativePathHairline())
        assertFalse(command(listOf(4f, 8f, 20f, 8f), antiAlias = true)
            .isBoundedNativePathHairline())
        assertFalse(command(listOf(4f, 8f, 20f, 20f)).isBoundedNativePathHairline())
    }

    private fun command(
        vertices: List<Float>,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        antiAlias: Boolean = false,
    ): NormalizedDrawCommand.FillPath = GPUFillPathCommandBuilder.build(
        commandId = GPUDrawCommandID(701),
        pathKey = "path:hairline-contract:v1",
        pathDescriptor = GPUPathFacts(
            pathKey = "path:hairline-contract:v1",
            verbCount = 2,
            pointCount = 2,
            fillRule = "NonZero",
            inverseFill = false,
            finiteProof = "finite",
            volatility = "immutable",
            transformClass = "identity",
            edgeCount = 1,
        ),
        tessellatedVertices = vertices,
        contourStarts = listOf(0),
        edgeCount = 1,
        target = GPUTargetFacts(width = 32, height = 32, colorFormat = "rgba8unorm"),
        material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f),
        transform = transform,
        blend = GPUBlendFacts.srcOver(),
        stroke = true,
        strokeWidth = 0f,
        strokeCap = "butt",
        strokeJoin = "miter",
        antiAlias = antiAlias,
    )
}
