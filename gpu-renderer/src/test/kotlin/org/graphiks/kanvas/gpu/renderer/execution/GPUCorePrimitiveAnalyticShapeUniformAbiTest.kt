package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey

class GPUCorePrimitiveAnalyticShapeUniformAbiTest {
    @Test
    fun `analytic shape uniform v1 packs exact reflected 80 byte layout`() {
        val packed = GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = 32f,
            targetHeight = 24f,
            antiAlias = true,
            premultipliedRgba = listOf(0.1f, 0.2f, 0.3f, 0.4f),
            deviceBounds = listOf(1.25f, 2.5f, 29.75f, 21.5f),
            normalizedRadii = listOf(3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f),
        ).packedBytes()

        assertEquals(80, CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES)
        assertEquals(80, packed.size)
        assertEquals(
            "dynamic-uniform80-analytic-shape-v1",
            CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_BINDING_LAYOUT_IDENTITY,
        )

        val bytes = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32f, bytes.getFloat(0))
        assertEquals(24f, bytes.getFloat(4))
        assertEquals(1, bytes.getInt(8))
        assertEquals(0, bytes.getInt(12))
        assertContentEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), bytes.floatsAt(16, 4))
        assertContentEquals(listOf(1.25f, 2.5f, 29.75f, 21.5f), bytes.floatsAt(32, 4))
        assertContentEquals(listOf(3f, 4f, 5f, 6f), bytes.floatsAt(48, 4))
        assertContentEquals(listOf(7f, 8f, 9f, 10f), bytes.floatsAt(64, 4))
    }

    @Test
    fun `analytic shape uniform snapshots caller owned vectors before packing`() {
        val color = mutableListOf(0.1f, 0.2f, 0.3f, 0.4f)
        val bounds = mutableListOf(1f, 2f, 9f, 10f)
        val radii = mutableListOf(1f, 2f, 3f, 4f, 2f, 2f, 2f, 2f)
        val block = GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = 16f,
            targetHeight = 12f,
            antiAlias = false,
            premultipliedRgba = color,
            deviceBounds = bounds,
            normalizedRadii = radii,
        )
        val expected = block.packedBytes()

        color.fill(99f)
        bounds.fill(99f)
        radii.fill(99f)

        assertContentEquals(expected, block.packedBytes())
        assertEquals(0, ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN).getInt(8))
    }

    @Test
    fun `analytic shape uniform rejects invalid target color geometry and normalized radii`() {
        val invalidBlocks = listOf(
            { validBlock(targetWidth = Float.NaN) },
            { validBlock(targetHeight = Float.POSITIVE_INFINITY) },
            { validBlock(targetWidth = 0f) },
            { validBlock(premultipliedRgba = listOf(-0.1f, 0f, 0f, 1f)) },
            { validBlock(premultipliedRgba = listOf(Float.NaN, 0f, 0f, 1f)) },
            { validBlock(premultipliedRgba = listOf(0.8f, 0f, 0f, 0.5f)) },
            { validBlock(deviceBounds = listOf(9f, 2f, 1f, 10f)) },
            { validBlock(deviceBounds = listOf(1f, 10f, 9f, 2f)) },
            { validBlock(deviceBounds = listOf(1f, 2f, Float.POSITIVE_INFINITY, 10f)) },
            { validBlock(normalizedRadii = listOf(-1f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)) },
            { validBlock(normalizedRadii = listOf(Float.NaN, 2f, 0f, 0f, 0f, 0f, 0f, 0f)) },
            { validBlock(normalizedRadii = listOf(5f, 1f, 5f, 1f, 0f, 0f, 0f, 0f)) },
        ) + (0 until 4).flatMap { corner ->
            listOf(
                { validBlock(normalizedRadii = mixedZeroRadii(corner, 0f, 1f)) },
                { validBlock(normalizedRadii = mixedZeroRadii(corner, 1f, 0f)) },
            )
        }

        invalidBlocks.forEach { construct ->
            assertFailsWith<IllegalArgumentException> { construct() }
        }
    }

    @Test
    fun `analytic shape uniform80 route closure is stable at preflight and materialization boundaries`() {
        val (code, message) = requireNotNull(
            corePrimitiveAnalyticShapeClosedRouteDiagnostic(
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
            ),
        )

        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_CODE, code)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_MESSAGE, message)
        assertNull(
            corePrimitiveAnalyticShapeClosedRouteDiagnostic(
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2,
            ),
        )
    }

    private fun validBlock(
        targetWidth: Float = 16f,
        targetHeight: Float = 12f,
        premultipliedRgba: List<Float> = listOf(0.25f, 0.125f, 0f, 0.5f),
        deviceBounds: List<Float> = listOf(1f, 2f, 9f, 10f),
        normalizedRadii: List<Float> = listOf(2f, 2f, 3f, 2f, 1f, 1f, 1f, 1f),
    ) = GPUCorePrimitiveAnalyticShapeUniformBlock(
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        antiAlias = true,
        premultipliedRgba = premultipliedRgba,
        deviceBounds = deviceBounds,
        normalizedRadii = normalizedRadii,
    )

    private fun ByteBuffer.floatsAt(offset: Int, count: Int): List<Float> =
        List(count) { index -> getFloat(offset + index * Float.SIZE_BYTES) }

    private fun mixedZeroRadii(corner: Int, radiusX: Float, radiusY: Float): List<Float> =
        MutableList(8) { 0f }.apply {
            this[corner * 2] = radiusX
            this[corner * 2 + 1] = radiusY
        }
}
