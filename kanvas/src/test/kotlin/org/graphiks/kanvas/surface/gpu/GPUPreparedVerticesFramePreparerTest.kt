package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedVerticesFramePreparerTest {
    @Test
    fun `preparer lowers all vertices builds one inventory then maps exactly once`() {
        val events = mutableListOf<String>()
        var mappedInventory: PreparedVerticesFrameInventory? = null
        val mapper = GPUPreparedVerticesOperationMapper { operations, target, config, capabilities, inventory ->
            events += "map"
            mappedInventory = inventory
            GPUOpMapper.mapOperations(operations, target, config, capabilities)
        }

        val prepared = GPUPreparedVerticesFramePreparer.prepare(
                operations = listOf(verticesOp(1f), verticesOp(2f)),
                target = target(), config = RenderConfig.DEFAULT, capabilities = capabilities(),
                limits = limits(), mapper = mapper,
                inventoryObserver = { events += "inventory" },
            )
        val result = assertIs<GPUPreparedVerticesFramePreparation.Ready>(
            prepared,
            (prepared as? GPUPreparedVerticesFramePreparation.Refused)?.refusal?.facts.toString(),
        )

        assertEquals(listOf("inventory", "map"), events)
        assertEquals(listOf(0, 1), result.inventory.commands.map { it.operationIndex })
        assertTrue(mappedInventory === result.inventory)
    }

    @Test
    fun `first lowering refusal publishes no inventory and never invokes mapper`() {
        var mapCount = 0
        var inventoryCount = 0
        val invalid = verticesOp(1f).copy(
            vertices = Vertices(
                VertexMode.TRIANGLES,
                listOf(Point(Float.NaN, 0f), Point(1f, 0f), Point(0f, 1f)),
            ),
        )

        val result = GPUPreparedVerticesFramePreparer.prepare(
            operations = listOf(verticesOp(1f), invalid, verticesOp(2f)),
            target = target(), config = RenderConfig.DEFAULT, capabilities = capabilities(),
            limits = limits(),
            mapper = GPUPreparedVerticesOperationMapper { _, _, _, _, _ ->
                mapCount++
                error("mapper must not be called")
            },
            inventoryObserver = { inventoryCount++ },
        )

        val refused = assertIs<GPUPreparedVerticesFramePreparation.Refused>(result)
        assertEquals(GPUPreparedVerticesRefusalCodes.NonFinite, refused.refusal.code)
        assertEquals(1, refused.refusal.operationIndex)
        assertEquals(0, mapCount)
        assertEquals(0, inventoryCount)
    }

    @Test
    fun `inventory budget refusal never invokes mapper or exposes partial mapping`() {
        var mapCount = 0
        val result = GPUPreparedVerticesFramePreparer.prepare(
            operations = listOf(verticesOp(1f), verticesOp(2f)),
            target = target(), config = RenderConfig.DEFAULT, capabilities = capabilities(),
            limits = limits().copy(maxDraws = 1),
            mapper = GPUPreparedVerticesOperationMapper { _, _, _, _, _ -> mapCount++; error("unreachable") },
        )

        val refused = assertIs<GPUPreparedVerticesFramePreparation.Refused>(result)
        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, refused.refusal.code)
        assertEquals(0, mapCount)
    }

    private fun verticesOp(scale: Float) = DisplayOp.DrawVertices(
        Vertices(
            VertexMode.TRIANGLES,
            listOf(Point(0f, 0f), Point(2f * scale, 0f), Point(0f, 2f * scale)),
        ),
        Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen,
    )

    private fun limits() = PreparedVerticesFrameInventoryLimits(
        maxDraws = 16, maxUniqueArtifacts = 16,
        maxVertexBytes = 1_000_000, maxIndexBytes = 1_000_000,
        maxTotalUploadBytes = 2_000_000, maxRuntimeUniformBytes = 1_000_000,
        maxRuntimeChildren = 16,
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("wgpu4k", "test", "adapter", "device"),
        facts = emptyList(), snapshotId = "fp06-task6-preparer",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
    )

    private fun target() = GPUTargetFacts(64, 64, "rgba8unorm-srgb")
}
