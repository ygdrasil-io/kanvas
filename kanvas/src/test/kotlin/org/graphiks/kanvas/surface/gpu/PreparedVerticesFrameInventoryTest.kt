package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class PreparedVerticesFrameInventoryTest {
    @Test
    fun `identical immutable geometry deduplicates while draw state and source order remain distinct`() {
        val first = draw(operationIndex = 4, transform = Matrix33.translate(2f, 3f), color = Color.RED)
        val second = draw(operationIndex = 9, transform = Matrix33.translate(8f, 5f), color = Color.BLUE)

        val inventory = buildReady(listOf(first, second))

        assertEquals(1, inventory.artifactsByKey.size)
        assertEquals(listOf(4, 9), inventory.commands.map { it.operationIndex })
        assertEquals(2, inventory.commands.size)
        assertEquals(setOf(4, 9), inventory.commandsByOperationIndex.keys)
        assertSame(inventory.commands[0], inventory.commandsByOperationIndex.getValue(4))
        assertSame(inventory.commands[1], inventory.commandsByOperationIndex.getValue(9))
        assertEquals(inventory.commands[0].artifactKey, inventory.commands[1].artifactKey)
        assertNotEquals(inventory.commands[0].draw.transform, inventory.commands[1].draw.transform)
        assertNotEquals(inventory.commands[0].draw.material.materialKey, inventory.commands[1].draw.material.materialKey)
        assertSame(inventory.artifactsByKey.getValue(inventory.commands[0].artifactKey), inventory.commands[0].artifact)
    }

    @Test
    fun `different bytes or structural layout never deduplicate and forced key collision refuses`() {
        val positionOnly = draw(0)
        val differentBytes = draw(1, positions = points(scale = 2f))
        val differentStructure = draw(2, colors = listOf(Color.RED, Color.GREEN, Color.BLUE))

        val ready = buildReady(listOf(positionOnly, differentBytes, differentStructure))
        assertEquals(3, ready.artifactsByKey.size)

        val collision = PreparedVerticesFrameInventoryBuilder.build(
            draws = listOf(positionOnly, differentBytes),
            limits = limits(),
            capabilities = capabilities(),
            artifactKeySelector = { "forced-collision" },
        )
        val refused = assertIs<PreparedVerticesFrameInventoryResult.Refused>(collision)
        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, refused.code)
        assertEquals("artifact_identity_collision", refused.facts["reason"])
        assertEquals(1, refused.operationIndex)
    }

    @Test
    fun `forced material bucket collision is refused by material structural authority`() {
        val first = draw(0, color = Color.RED)
        val second = draw(1, color = Color.BLUE)
        val collision = PreparedVerticesFrameInventoryBuilder.build(
            draws = listOf(first, second), limits = limits(), capabilities = capabilities(),
            artifactKeySelector = { it.key },
            materialBucketKeySelector = { "forced-collision" },
        )

        val refused = assertIs<PreparedVerticesFrameInventoryResult.Refused>(collision)
        assertEquals(GPUPreparedVerticesRefusalCodes.Material, refused.code)
        assertEquals("material_identity_collision", refused.facts["reason"])
        assertEquals("GPUPreparedMaterialProgram", refused.facts["authority"])
    }

    @Test
    fun `frame command and inventory reject material keys or programs outside their snapshots`() {
        val inventory = buildReady(listOf(draw(0, color = Color.RED)))
        val command = inventory.commands.single()

        assertFailsWith<IllegalArgumentException> {
            PreparedVerticesFrameCommand(
                operationIndex = command.operationIndex,
                artifactKey = command.artifactKey,
                artifact = command.artifact,
                materialKey = "sha256:${"f".repeat(64)}",
                materialFrameSnapshot = command.materialFrameSnapshot,
                draw = command.draw,
            )
        }

        val differentMaterial = draw(1, color = Color.BLUE).material
        listOf(
            emptyMap(),
            mapOf(command.materialKey to differentMaterial),
        ).forEach { malformedMaterials ->
            assertFailsWith<IllegalArgumentException> {
                PreparedVerticesFrameInventory(
                    commands = inventory.commands,
                    artifactsByKey = inventory.artifactsByKey,
                    materialsByKey = malformedMaterials,
                    artifactKeyByOperationIndex = inventory.artifactKeyByOperationIndex,
                    vertexUploadRanges = inventory.vertexUploadRanges,
                    indexUploadRanges = inventory.indexUploadRanges,
                    elidedVerticesOperationIndices = inventory.elidedVerticesOperationOrder,
                    mappedCommands = inventory.mappedCommands,
                    capabilitySnapshotHash = inventory.capabilitySnapshotHash,
                    metrics = inventory.metrics,
                    limitEvidence = inventory.limitEvidence,
                )
            }
        }
    }

    @Test
    fun `upload ranges are deterministic aligned separate non overlapping and aggregate checked`() {
        val indexed = draw(2, indices = listOf(0, 1, 2))
        val anotherIndexed = draw(5, positions = points(scale = 2f), indices = listOf(0, 2, 1))

        val inventory = buildReady(listOf(indexed, anotherIndexed))

        assertTrue(inventory.vertexUploadRanges.all { it.bufferKind == PreparedVerticesUploadBufferKind.Vertex })
        assertTrue(inventory.indexUploadRanges.all { it.bufferKind == PreparedVerticesUploadBufferKind.Index })
        (inventory.vertexUploadRanges + inventory.indexUploadRanges).forEach { range ->
            assertEquals(0L, range.offset % range.alignment)
            assertTrue(range.byteCount > 0L)
        }
        assertNonOverlapping(inventory.vertexUploadRanges)
        assertNonOverlapping(inventory.indexUploadRanges)
        assertEquals(
            inventory.vertexUploadRanges.sumOf { it.occupiedByteCount } +
                inventory.indexUploadRanges.sumOf { it.occupiedByteCount },
            inventory.metrics.totalUploadBytes,
        )
    }

    @Test
    fun `each exact frame budget refuses transactionally with stable facts`() {
        val draws = listOf(
            asMesh(draw(3, indices = listOf(0, 1, 2))),
            asMesh(draw(8, positions = points(2f), indices = listOf(0, 2, 1))),
        )
        val baseline = buildReady(draws)
        val cases = listOf(
            "maxDraws" to limits(maxDraws = 1),
            "maxUniqueArtifacts" to limits(maxUniqueArtifacts = 1),
            "maxVertexBytes" to limits(maxVertexBytes = baseline.metrics.vertexBytes - 1),
            "maxIndexBytes" to limits(maxIndexBytes = baseline.metrics.indexBytes - 1),
            "maxTotalUploadBytes" to limits(maxTotalUploadBytes = baseline.metrics.totalUploadBytes - 1),
            "maxRuntimeUniformBytes" to limits(maxRuntimeUniformBytes = baseline.metrics.runtimeUniformBytes - 1),
        )

        cases.forEach { (budget, constrained) ->
            val result = PreparedVerticesFrameInventoryBuilder.build(draws, constrained, capabilities())
            val refused = assertIs<PreparedVerticesFrameInventoryResult.Refused>(result, budget)
            assertEquals(GPUPreparedVerticesRefusalCodes.MeshBudget, refused.code, budget)
            assertEquals(budget, refused.facts["budget"], budget)
            assertEquals(refused.operationIndex.toString(), refused.facts["operationIndex"], budget)
            assertEquals("PreparedVerticesFrameInventory", refused.facts["authority"], budget)
            assertEquals("budget_exceeded", refused.facts["reason"], budget)
            assertTrue(refused.operationIndex == 3 || refused.operationIndex == 8, budget)
        }
    }

    @Test
    fun `mixed draw budget uses first operation that crosses the limit and its refusal family`() {
        val first = draw(2)
        val second = asMesh(draw(7, positions = points(2f)))
        val baseline = buildReady(listOf(first, second))

        val refusal = assertIs<PreparedVerticesFrameInventoryResult.Refused>(
            PreparedVerticesFrameInventoryBuilder.build(
                listOf(first, second),
                limits(maxTotalUploadBytes = baseline.vertexUploadRanges.first().occupiedByteCount),
                capabilities(),
            ),
        )

        assertEquals(7, refusal.operationIndex)
        assertEquals("7", refusal.facts["operationIndex"])
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshBudget, refusal.code)
    }

    @Test
    fun `culled draws retain ordered ownership but allocate and charge nothing`() {
        val visible = draw(1)
        val culledShared = asCulled(draw(4))
        val culledDifferent = asCulled(draw(9, positions = points(3f)))
        val noOp = asNoOp(draw(12, positions = points(4f)))

        val inventory = buildReady(listOf(visible, culledShared, culledDifferent, noOp))

        assertEquals(listOf(1), inventory.commands.map { it.operationIndex })
        assertIs<Set<Int>>(inventory.elidedVerticesOperationIndices)
        assertEquals(setOf(4, 9, 12), inventory.elidedVerticesOperationIndices)
        assertEquals(listOf(4, 9, 12), inventory.elidedVerticesOperationOrder)
        assertEquals(1, inventory.metrics.drawCount)
        assertEquals(1, inventory.metrics.uniqueArtifactCount)
        assertEquals(setOf(1), inventory.artifactKeyByOperationIndex.keys)

        val allCulled = buildReady(listOf(culledShared, culledDifferent))
        assertTrue(allCulled.commands.isEmpty())
        assertEquals(setOf(4, 9), allCulled.elidedVerticesOperationIndices)
        assertEquals(listOf(4, 9), allCulled.elidedVerticesOperationOrder)
        assertEquals(0, allCulled.metrics.drawCount)
        assertEquals(0, allCulled.metrics.totalUploadBytes)
        assertTrue(allCulled.artifactsByKey.isEmpty())
        assertTrue(allCulled.materialsByKey.isEmpty())
    }

    @Test
    fun `command binding rejects missing unexpected duplicate and negative ids without throwing`() {
        val inventory = buildReady(listOf(draw(4), draw(9, positions = points(2f))))
        val cases = listOf(
            Triple(mapOf(4 to 0), 9, "missing_operation_binding"),
            Triple(mapOf(4 to 0, 9 to 1, 11 to 2), 11, "unexpected_operation_binding"),
            Triple(mapOf(4 to 0, 9 to 0), 9, "duplicate_command_id"),
            Triple(mapOf(4 to 0, 9 to -1), 9, "negative_command_id"),
        )

        cases.forEach { (bindings, operationIndex, reason) ->
            val refusal = assertIs<PreparedVerticesCommandBindingResult.Refused>(
                inventory.bindCommandIds(
                    bindings,
                    bindings.values.associateWith { GPUFrameProvenance.None },
                ),
                reason,
            )
            assertEquals("invalid.surface.prepared.vertices-command-binding", refusal.code, reason)
            assertEquals(operationIndex, refusal.operationIndex, reason)
            assertEquals("PreparedVerticesFrameInventory", refusal.facts["authority"], reason)
            assertEquals(reason, refusal.facts["reason"], reason)
            assertEquals(operationIndex.toString(), refusal.facts["operationIndex"], reason)
        }
    }

    @Test
    fun `command provenance keys must exactly cover bound command ids`() {
        val inventory = buildReady(listOf(draw(4), draw(9, positions = points(2f))))
        val bindings = mapOf(4 to 7, 9 to 11)
        val cases = listOf(
            mapOf(7 to GPUFrameProvenance.None) to "missing_command_provenance",
            mapOf(
                7 to GPUFrameProvenance.None,
                11 to GPUFrameProvenance.None,
                13 to GPUFrameProvenance.None,
            ) to "unexpected_command_provenance",
        )

        cases.forEach { (provenance, reason) ->
            val refusal = assertIs<PreparedVerticesCommandBindingResult.Refused>(
                inventory.bindCommandIds(bindings, provenance),
            )
            assertEquals("invalid.surface.prepared.vertices-command-binding", refusal.code)
            assertEquals(reason, refusal.facts["reason"])
        }
    }

    @Test
    fun `negative limits are invalid before inventory work`() {
        val valid = limits()
        val invalidFactories = listOf<() -> PreparedVerticesFrameInventoryLimits>(
            { valid.copy(maxDraws = -1) },
            { valid.copy(maxUniqueArtifacts = -1) },
            { valid.copy(maxVertexBytes = -1) },
            { valid.copy(maxIndexBytes = -1) },
            { valid.copy(maxTotalUploadBytes = -1) },
            { valid.copy(maxRuntimeUniformBytes = -1) },
            { valid.copy(maxRuntimeChildren = -1) },
        )
        invalidFactories.forEach { factory ->
            assertFailsWith<IllegalArgumentException> { factory() }
        }
    }

    @Test
    fun `runtime child count and child uniform bytes are aggregate material budgets`() {
        val draw = draw(0)
        fun build(limits: PreparedVerticesFrameInventoryLimits) =
            PreparedVerticesFrameInventoryBuilder.build(
                draws = listOf(draw), limits = limits, capabilities = capabilities(),
                artifactKeySelector = { it.key },
                materialBudgetSelector = { 32L to 1 },
            )
        val baseline = assertIs<PreparedVerticesFrameInventoryResult.Ready>(build(limits())).inventory
        assertEquals(1, baseline.metrics.runtimeChildren)
        assertEquals(32, baseline.metrics.runtimeUniformBytes)

        val childRefusal = assertIs<PreparedVerticesFrameInventoryResult.Refused>(
            build(limits(maxRuntimeChildren = 0)),
        )
        assertEquals("maxRuntimeChildren", childRefusal.facts["budget"])

        val uniformRefusal = assertIs<PreparedVerticesFrameInventoryResult.Refused>(
            build(limits(maxRuntimeUniformBytes = 31)),
        )
        assertEquals("maxRuntimeUniformBytes", uniformRefusal.facts["budget"])
    }

    @Test
    fun `aggregate long addition overflow refuses before inventory publication`() {
        val result = PreparedVerticesFrameInventoryBuilder.build(
            draws = listOf(draw(0), asMesh(draw(1))),
            limits = limits(maxRuntimeUniformBytes = Long.MAX_VALUE),
            capabilities = capabilities(),
            artifactKeySelector = { it.key },
            materialBudgetSelector = { Long.MAX_VALUE to 0 },
        )

        val refused = assertIs<PreparedVerticesFrameInventoryResult.Refused>(result)
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshBudget, refused.code)
        assertEquals(1, refused.operationIndex)
        assertEquals("checked_arithmetic_overflow", refused.facts["reason"])
        assertEquals("runtimeUniformBytes", refused.facts["field"])
        assertEquals("1", refused.facts["operationIndex"])
    }

    @Test
    fun `configured and capability effective limits are retained with exact source`() {
        val configured = limits(maxVertexBytes = 1024, maxIndexBytes = 2048, maxTotalUploadBytes = 4096)
        val ready = assertIs<PreparedVerticesFrameInventoryResult.Ready>(
            PreparedVerticesFrameInventoryBuilder.build(
                draws = listOf(draw(0)), limits = configured,
                capabilities = capabilities(maxBufferSize = 128, source = "adapter.device.limits"),
            ),
        ).inventory

        assertEquals(configured, ready.limitEvidence.configured)
        assertEquals(128, ready.limitEvidence.effective.maxVertexBytes)
        assertEquals(128, ready.limitEvidence.effective.maxIndexBytes)
        assertEquals(256, ready.limitEvidence.effective.maxTotalUploadBytes)
        assertEquals("adapter.device.limits", ready.limitEvidence.capabilitySource)
    }

    @Test
    fun `published collections reject hostile JVM mutation`() {
        val inventory = buildReady(listOf(draw(0)))
        val command = inventory.commands.single()
        val publishedMaterial = inventory.materialsByKey.getValue(command.materialKey)

        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (inventory.commands as MutableList<PreparedVerticesFrameCommand>).clear()
        }
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (inventory.artifactsByKey as MutableMap<String, Any>).clear()
        }
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (inventory.commandsByOperationIndex as MutableMap<Int, PreparedVerticesFrameCommand>).clear()
        }
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (publishedMaterial.uniformBytes as MutableList<Int>).clear()
        }
        assertSame(command.material, publishedMaterial)
        assertNotSame(command.draw.material, publishedMaterial)
        val culled = buildReady(listOf(asCulled(draw(4))))
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (culled.elidedVerticesOperationIndices as MutableSet<Int>).clear()
        }
        assertEquals(1, inventory.commands.size)
    }

    private fun buildReady(draws: List<GPUPreparedVerticesDraw>): PreparedVerticesFrameInventory {
        val result = PreparedVerticesFrameInventoryBuilder.build(draws, limits(), capabilities())
        return assertIs<PreparedVerticesFrameInventoryResult.Ready>(
            result,
            (result as? PreparedVerticesFrameInventoryResult.Refused)?.facts.toString(),
        ).inventory
    }

    private fun draw(
        operationIndex: Int,
        transform: Matrix33 = Matrix33.identity(),
        color: Color = Color.WHITE,
        positions: List<Point> = points(),
        colors: List<Color>? = null,
        indices: List<Int>? = null,
    ): GPUPreparedVerticesDraw {
        val operation = DisplayOp.DrawVertices(
            vertices = Vertices(VertexMode.TRIANGLES, positions, colors = colors, indices = indices),
            paint = Paint.fill(color), transform = transform, clip = ClipStack.WideOpen,
        )
        return assertIs<GPUPreparedVerticesLowering.Ready>(
            GPUPreparedVerticesLowerer.lower(operation, operationIndex, target(), capabilities()),
        ).draw
    }

    private fun asMesh(draw: GPUPreparedVerticesDraw): GPUPreparedVerticesDraw = cloneDraw(
        draw = draw,
        operationKind = GPUPreparedVerticesOperationKind.DrawMesh,
    )

    private fun asCulled(draw: GPUPreparedVerticesDraw): GPUPreparedVerticesDraw = cloneDraw(
        draw = draw,
        culledByClip = true,
    )

    private fun asNoOp(draw: GPUPreparedVerticesDraw): GPUPreparedVerticesDraw = cloneDraw(
        draw = draw,
        blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.DST, "destination_preserved"),
    )

    private fun cloneDraw(
        draw: GPUPreparedVerticesDraw,
        operationKind: GPUPreparedVerticesOperationKind = draw.operationKind,
        culledByClip: Boolean = draw.culledByClip,
        blendPlan: GPUBlendPlan = draw.blendPlan,
    ): GPUPreparedVerticesDraw = GPUPreparedVerticesDraw.create(
        artifact = draw.artifact,
        operationKind = operationKind,
        material = draw.material,
        transform = draw.transform,
        clip = draw.clip,
        clipSnapshot = draw.clipSnapshot,
        finalBlend = draw.finalBlend,
        blendPlan = blendPlan,
        sourceBounds = draw.sourceBounds,
        deviceBounds = draw.deviceBounds,
        clippedBounds = if (culledByClip) null else draw.clippedBounds,
        culledByClip = culledByClip,
        meshBounds = draw.meshBounds,
        operationIndex = draw.operationIndex,
        provenance = draw.provenance,
        paintAlphaApplicationCount = draw.paintAlphaApplicationCount,
        primitiveColorPresent = draw.primitiveColorPresent,
        primitiveBlendPlan = draw.primitiveBlendPlan,
    )


    private fun limits(
        maxDraws: Int = 16,
        maxUniqueArtifacts: Int = 16,
        maxVertexBytes: Long = 1_000_000,
        maxIndexBytes: Long = 1_000_000,
        maxTotalUploadBytes: Long = 2_000_000,
        maxRuntimeUniformBytes: Long = 1_000_000,
        maxRuntimeChildren: Int = 16,
    ): PreparedVerticesFrameInventoryLimits {
        return PreparedVerticesFrameInventoryLimits(
            maxDraws, maxUniqueArtifacts, maxVertexBytes, maxIndexBytes,
            maxTotalUploadBytes, maxRuntimeUniformBytes, maxRuntimeChildren,
        )
    }

    private fun capabilities(
        maxBufferSize: Long = 1L shl 30,
        source: String = "test.device.limits",
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity("wgpu4k", "test", "adapter", "device"),
        facts = emptyList(), snapshotId = "fp06-task6",
        limits = GPULimits(8192, 256, 256, source, maxBufferSize, 1),
    )

    private fun target() = GPUTargetFacts(64, 64, "rgba8unorm-srgb")
    private fun points(scale: Float = 1f) = listOf(
        Point(0f, 0f), Point(2f * scale, 0f), Point(0f, 2f * scale),
    )

    private fun assertNonOverlapping(ranges: List<PreparedVerticesUploadRange>) {
        ranges.zipWithNext().forEach { (left, right) ->
            assertTrue(left.endExclusive <= right.offset)
        }
    }
}
