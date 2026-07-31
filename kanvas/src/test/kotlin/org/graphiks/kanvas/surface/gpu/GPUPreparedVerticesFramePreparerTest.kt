package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedVerticesFramePreparerTest {
    @Test
    fun `canonical mapper consumes complete inventory and binds true command ids without legacy`() {
        val prepared = GPUPreparedVerticesFramePreparer.prepare(
                operations = listOf(
                    rectOp(),
                    DisplayOp.Annotation(Rect.fromLTRB(0f, 0f, 1f, 1f), "test", "state"),
                    verticesOp(1f),
                    verticesOp(2f),
                    rectOp(),
                ),
                target = target(), config = RenderConfig.DEFAULT, capabilities = capabilities(),
                limits = limits(),
            )
        val result = assertIs<GPUPreparedVerticesFramePreparation.Ready>(
            prepared,
            (prepared as? GPUPreparedVerticesFramePreparation.Refused)?.refusal?.facts.toString(),
        )

        assertEquals(listOf(2, 3), result.inventory.commands.map { it.operationIndex })
        assertEquals(mapOf(1 to result.inventory.commands[0].artifactKey,
            2 to result.inventory.commands[1].artifactKey), result.inventory.artifactKeyByCommandId)
        assertEquals(listOf(0, 3), result.mapping.visualCommands.map { it.normalized.commandId.value })
        assertEquals(0, result.mapping.legacyDump.invocationCount)
        assertTrue(result.mapping.preparedVerticesInventory === result.inventory)
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
            mappingBoundary = GPUPreparedFrameMappingBoundary { _, _, _, _, _, _ ->
                mapCount++
                error("mapper must not be called")
            },
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
            mappingBoundary = GPUPreparedFrameMappingBoundary { _, _, _, _, _, _ ->
                mapCount++
                error("unreachable")
            },
        )

        val refused = assertIs<GPUPreparedVerticesFramePreparation.Refused>(result)
        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, refused.refusal.code)
        assertEquals(0, mapCount)
    }

    @Test
    fun `mapper refusal publishes neither mapping nor inventory`() {
        val expected = GPUPreparedOperationRefusal(
            commandId = 0, operationIndex = 0,
            code = "invalid.surface.prepared.vertices-operation-ownership",
            facts = mapOf("authority" to "GPUOpMapper"),
        )
        val result = GPUPreparedVerticesFramePreparer.prepare(
            operations = listOf(verticesOp(1f)), target = target(),
            config = RenderConfig.DEFAULT, capabilities = capabilities(), limits = limits(),
            mappingBoundary = GPUPreparedFrameMappingBoundary { _, _, _, _, _, _ ->
                GPUOpMapping(
                    visualCommands = emptyList(), stateEvents = emptyList(),
                    legacyDump = GPULegacyImmediatePathDump(0, emptyMap()),
                    preparedRefusal = expected,
                )
            },
        )

        val refused = assertIs<GPUPreparedVerticesFramePreparation.Refused>(result)
        assertEquals(expected, refused.refusal)
    }

    @Test
    fun `expected mapper failures become stable transactional refusal while fatal errors pass through`() {
        listOf(
            IllegalStateException("expected") to "mapper_exception",
            LinkageError("linkage") to "mapper_linkage_error",
        ).forEach { (failure, reason) ->
            val result = GPUPreparedVerticesFramePreparer.prepare(
                operations = listOf(verticesOp(1f)), target = target(),
                config = RenderConfig.DEFAULT, capabilities = capabilities(), limits = limits(),
                mappingBoundary = GPUPreparedFrameMappingBoundary { _, _, _, _, _, _ -> throw failure },
            )
            val refused = assertIs<GPUPreparedVerticesFramePreparation.Refused>(result)
            assertEquals("invalid.surface.prepared.vertices-mapping", refused.refusal.code)
            assertEquals(reason, refused.refusal.facts["reason"])
            assertEquals("GPUOpMapper", refused.refusal.facts["authority"])
        }
        assertFailsWith<OutOfMemoryError> {
            GPUPreparedVerticesFramePreparer.prepare(
                listOf(verticesOp(1f)), target(), RenderConfig.DEFAULT, capabilities(), limits(),
                mappingBoundary = GPUPreparedFrameMappingBoundary { _, _, _, _, _, _ ->
                    throw OutOfMemoryError("fatal")
                },
            )
        }
        assertFailsWith<ThreadDeath> {
            GPUPreparedVerticesFramePreparer.prepare(
                listOf(verticesOp(1f)), target(), RenderConfig.DEFAULT, capabilities(), limits(),
                mappingBoundary = GPUPreparedFrameMappingBoundary { _, _, _, _, _, _ -> throw ThreadDeath() },
            )
        }
    }

    @Test
    fun `operations are snapshotted before lowering and mapping`() {
        val mutable = mutableListOf<DisplayOp>(verticesOp(1f))
        var mappedSize = -1
        val result = GPUPreparedVerticesFramePreparer.prepare(
            operations = mutable, target = target(), config = RenderConfig.DEFAULT,
            capabilities = capabilities(), limits = limits(),
            mappingBoundary = GPUPreparedFrameMappingBoundary { operations, target, config, capabilities, text, vertices ->
                mutable += verticesOp(2f)
                mappedSize = operations.size
                GPUOpMapper.mapOperations(operations, target, config, capabilities, text, vertices)
            },
        )
        assertIs<GPUPreparedVerticesFramePreparation.Ready>(result)
        assertEquals(1, mappedSize)
        assertEquals(2, mutable.size)
    }

    private fun verticesOp(scale: Float) = DisplayOp.DrawVertices(
        Vertices(
            VertexMode.TRIANGLES,
            listOf(Point(0f, 0f), Point(2f * scale, 0f), Point(0f, 2f * scale)),
        ),
        Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen,
    )

    private fun rectOp() = DisplayOp.DrawRect(
        Rect.fromLTRB(1f, 1f, 3f, 3f), Paint.fill(Color.RED),
        Matrix33.identity(), ClipStack.WideOpen,
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
