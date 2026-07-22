package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot

class GPUCorePrimitivePathStencilNativeRouteTest {
    @Test
    fun `accepted pair snapshots its edge fan and derives a conservative cover quad`() {
        val producerVertices = floatArrayOf(
            -1f, -1f, 2f, 3f, 9f, 3f,
            -1f, -1f, 9f, 3f, 5f, 11f,
        )
        val producerIndices = intArrayOf(0, 1, 2, 3, 4, 5)
        val accepted = GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(
            producerPacketId = GPUDrawPacketID("packet.path.producer"),
            coverPacketId = GPUDrawPacketID("packet.path.cover"),
            producerVertices = producerVertices,
            producerIndices = producerIndices,
            coverBounds = GPUPixelBounds(2, 3, 9, 11),
            targetBounds = GPUPixelBounds(0, 0, 16, 16),
            inverseFill = false,
        )

        producerVertices[0] = 99f
        producerIndices[0] = 99

        val copiedProducerVertices = FloatArray(accepted.producer.vertexCount * 2)
        val copiedProducerIndices = IntArray(accepted.producer.indexCount)
        val copiedCoverVertices = FloatArray(accepted.cover.vertexCount * 2)
        val copiedCoverIndices = IntArray(accepted.cover.indexCount)
        accepted.producer.copyVerticesInto(copiedProducerVertices)
        accepted.producer.copyIndicesInto(copiedProducerIndices)
        accepted.cover.copyVerticesInto(copiedCoverVertices)
        accepted.cover.copyIndicesInto(copiedCoverIndices)

        assertContentEquals(
            floatArrayOf(-1f, -1f, 2f, 3f, 9f, 3f, -1f, -1f, 9f, 3f, 5f, 11f),
            copiedProducerVertices,
        )
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5), copiedProducerIndices)
        assertContentEquals(floatArrayOf(2f, 3f, 9f, 3f, 9f, 11f, 2f, 11f), copiedCoverVertices)
        assertContentEquals(intArrayOf(0, 2, 1, 0, 3, 2), copiedCoverIndices)
        assertEquals(4, accepted.cover.vertexCount)
        assertEquals(6, accepted.cover.indexCount)
        assertEquals(3, accepted.cover.maxLocalIndex)
    }

    @Test
    fun `inverse pair covers the whole target and leaves clipping to the sealed scissor`() {
        val accepted = acceptedPair(
            suffix = "inverse",
            coverBounds = GPUPixelBounds(4, 5, 8, 9),
            targetBounds = GPUPixelBounds(0, 0, 16, 12),
            inverseFill = true,
        )
        val vertices = FloatArray(accepted.cover.vertexCount * 2)

        accepted.cover.copyVerticesInto(vertices)

        assertContentEquals(floatArrayOf(0f, 0f, 16f, 0f, 16f, 12f, 0f, 12f), vertices)
    }

    @Test
    fun `frame seal keeps pair order and scopes equal packet identities by render step`() {
        val first = acceptedPair("first")
        val second = acceptedPair("second")
        val sameIdsOtherScope = acceptedPair("first", coverBounds = GPUPixelBounds(7, 8, 12, 13))
        val frameSeal = GPUCorePrimitivePathStencilNativeFrameRouteSeal(
            linkedMapOf(
                GPUCorePrimitivePathStencilNativeFrameRouteKey(
                    3,
                    first.producerPacketId,
                    first.coverPacketId,
                ) to first,
                GPUCorePrimitivePathStencilNativeFrameRouteKey(
                    3,
                    second.producerPacketId,
                    second.coverPacketId,
                ) to second,
                GPUCorePrimitivePathStencilNativeFrameRouteKey(
                    7,
                    sameIdsOtherScope.producerPacketId,
                    sameIdsOtherScope.coverPacketId,
                ) to sameIdsOtherScope,
            ),
        )

        val retained = assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Pairs>(
            frameSeal.retainedFor(
                sourceStepIndex = 3,
                packetIds = listOf(
                    first.producerPacketId,
                    first.coverPacketId,
                    second.producerPacketId,
                    second.coverPacketId,
                ),
            ),
        )
        assertEquals(listOf(first, second), retained.orderedPairs)
        assertSame(
            sameIdsOtherScope,
            assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Pairs>(
                frameSeal.retainedFor(
                    7,
                    listOf(sameIdsOtherScope.producerPacketId, sameIdsOtherScope.coverPacketId),
                ),
            ).orderedPairs.single(),
        )
        assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Missing>(
            frameSeal.retainedFor(
                3,
                listOf(first.coverPacketId, first.producerPacketId),
            ),
        )
        assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Missing>(
            frameSeal.retainedFor(3, listOf(first.producerPacketId)),
        )
        assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Empty>(
            frameSeal.retainedFor(11, emptyList()),
        )
        assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Missing>(
            frameSeal.retainedFor(11, listOf(first.producerPacketId, first.coverPacketId)),
        )
    }

    @Test
    fun `one packet identity cannot participate in two pairs inside one scope or arena`() {
        val first = acceptedPair("first")
        val overlapsFirstCover = acceptedPair(
            suffix = "overlap",
            producerPacketId = first.coverPacketId,
        )

        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitivePathStencilNativeRouteSeal.Pairs(listOf(first, overlapsFirstCover))
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitivePathStencilNativeFrameRouteSeal(
                linkedMapOf(
                    GPUCorePrimitivePathStencilNativeFrameRouteKey(
                        3,
                        first.producerPacketId,
                        first.coverPacketId,
                    ) to first,
                    GPUCorePrimitivePathStencilNativeFrameRouteKey(
                        3,
                        overlapsFirstCover.producerPacketId,
                        overlapsFirstCover.coverPacketId,
                    ) to overlapsFirstCover,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            packCorePrimitivePathStencilFrameGeometry(listOf(first, overlapsFirstCover))
        }

        GPUCorePrimitivePathStencilNativeFrameRouteSeal(
            linkedMapOf(
                GPUCorePrimitivePathStencilNativeFrameRouteKey(
                    3,
                    first.producerPacketId,
                    first.coverPacketId,
                ) to first,
                GPUCorePrimitivePathStencilNativeFrameRouteKey(
                    7,
                    first.producerPacketId,
                    first.coverPacketId,
                ) to first,
            ),
        )
    }

    @Test
    fun `unified frame seal rejects mismatched first packet keys and duplicate step entries`() {
        val first = directRoutes("first", commandId = 1)
        val second = directRoutes("second", commandId = 2)

        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitiveNativeScopeFrameRouteSeal(
                mapOf(
                    GPUCorePrimitiveNativeScopeFrameRouteKey(
                        3,
                        GPUDrawPacketID("packet.wrong"),
                    ) to first,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitiveNativeScopeFrameRouteSeal(
                linkedMapOf(
                    GPUCorePrimitiveNativeScopeFrameRouteKey(
                        3,
                        first.flattenedPacketIds.first(),
                    ) to first,
                    GPUCorePrimitiveNativeScopeFrameRouteKey(
                        3,
                        second.flattenedPacketIds.first(),
                    ) to second,
                ),
            )
        }
    }

    @Test
    fun `all ordered pairs share one arena with producer then cover slices`() {
        val first = acceptedPair("first")
        val secondSourceVertices = floatArrayOf(20f, 2f, 25f, 2f, 22f, 7f)
        val secondSourceIndices = intArrayOf(2, 0, 1)
        val second = acceptedPair(
            "second",
            producerVertices = secondSourceVertices,
            producerIndices = secondSourceIndices,
            coverBounds = GPUPixelBounds(20, 2, 25, 7),
            targetBounds = GPUPixelBounds(0, 0, 32, 16),
        )

        val arena = packCorePrimitivePathStencilFrameGeometry(listOf(first, second))
        secondSourceVertices.fill(99f)
        secondSourceIndices.fill(99)
        val copiedVertices = FloatArray(arena.vertexFloatCount)
        val copiedIndices = IntArray(arena.indexCount)
        arena.copyVerticesInto(copiedVertices)
        arena.copyIndicesInto(copiedIndices)

        assertEquals(4, arena.slices.size)
        assertEquals(listOf(0, 3, 9, 12), arena.slices.map { it.firstIndex })
        assertEquals(listOf(3, 6, 3, 6), arena.slices.map { it.indexCount })
        assertEquals(listOf(0, 3, 7, 10), arena.slices.map { it.baseVertex })
        assertEquals(listOf(3, 4, 3, 4), arena.slices.map { it.vertexCount })
        assertEquals(listOf(2, 3, 2, 3), arena.slices.map { it.maxLocalIndex })
        assertEquals(
            listOf(
                GPUCorePrimitivePathStencilArenaRole.Producer,
                GPUCorePrimitivePathStencilArenaRole.Cover,
                GPUCorePrimitivePathStencilArenaRole.Producer,
                GPUCorePrimitivePathStencilArenaRole.Cover,
            ),
            arena.slices.map { it.role },
        )
        assertEquals(
            listOf(
                first.producerPacketId,
                first.producerPacketId,
                second.producerPacketId,
                second.producerPacketId,
            ),
            arena.slices.map { it.pairKey.producerPacketId },
        )
        assertContentEquals(
            intArrayOf(
                0, 1, 2,
                0, 2, 1, 0, 3, 2,
                2, 0, 1,
                0, 2, 1, 0, 3, 2,
            ),
            copiedIndices,
        )
        assertEquals(7, arena.slices[2].baseVertex)
        assertEquals(20f, copiedVertices[arena.slices[2].baseVertex * 2])
        val sourceConstructors = GPUCorePrimitivePathStencilFrameGeometryArena::class.java
            .declaredConstructors.filterNot { constructor -> constructor.isSynthetic }
        assertTrue(sourceConstructors.isNotEmpty())
        assertTrue(
            sourceConstructors.all { constructor -> Modifier.isPrivate(constructor.modifiers) },
        )
        assertTrue(
            GPUCorePrimitivePathStencilFrameGeometryArena::class.java.methods.none { method ->
                method.name == "getVertices" || method.name == "getIndices"
            },
            "The arena must not expose its mutable primitive arrays",
        )
    }

    @Test
    fun `pair creation rejects corrupt local indices and invalid cover authorities`() {
        assertFailsWith<IllegalArgumentException> {
            acceptedPair("negative", producerIndices = intArrayOf(0, -1, 2))
        }
        assertFailsWith<IllegalArgumentException> {
            acceptedPair("outside", producerIndices = intArrayOf(0, 1, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            acceptedPair("empty-cover", coverBounds = GPUPixelBounds(2, 2, 2, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            acceptedPair(
                "outside-target",
                coverBounds = GPUPixelBounds(0, 0, 17, 8),
                targetBounds = GPUPixelBounds(0, 0, 16, 16),
            )
        }
    }

    private fun acceptedPair(
        suffix: String,
        producerPacketId: GPUDrawPacketID = GPUDrawPacketID("packet.path.$suffix.producer"),
        coverPacketId: GPUDrawPacketID = GPUDrawPacketID("packet.path.$suffix.cover"),
        producerVertices: FloatArray = floatArrayOf(1f, 1f, 5f, 1f, 3f, 6f),
        producerIndices: IntArray = intArrayOf(0, 1, 2),
        coverBounds: GPUPixelBounds = GPUPixelBounds(1, 1, 5, 6),
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 16, 16),
        inverseFill: Boolean = false,
    ) = GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(
        producerPacketId = producerPacketId,
        coverPacketId = coverPacketId,
        producerVertices = producerVertices,
        producerIndices = producerIndices,
        coverBounds = coverBounds,
        targetBounds = targetBounds,
        inverseFill = inverseFill,
    )

    private fun directRoutes(
        suffix: String,
        commandId: Int,
    ): GPUCorePrimitiveNativeScopeRouteSeal.Routes {
        val packetId = GPUDrawPacketID("packet.$suffix")
        val structuralKey = GPUCorePrimitiveRenderPipelineStructuralKey(
            shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry,
            topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
            blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp(GPUBlendMode.DST),
            clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        )
        val uniformSeal = GPUCorePrimitiveUniformSlabSeal(
            GPUUniformSlabPlan(
                planHash = "plan.$suffix",
                sourceLabel = "source.$suffix",
                deviceGeneration = 1,
                alignmentBytes = 4,
                totalBytes = 4,
                uploadBudgetBytes = 4,
                slots = listOf(
                    GPUUniformSlabSlot("slot.$suffix", "hash.$suffix", 4, 0, 4),
                ),
            ),
            listOf(commandId),
            byteArrayOf(0, 0, 0, 0),
        )
        return GPUCorePrimitiveNativeScopeRouteSeal.Routes(
            listOf(
                GPUCorePrimitiveNativeScopeRouteUnit.Direct(
                    commandId,
                    packetId,
                    GPUCorePrimitiveDirectNativeRoute.Accepted(
                        floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f),
                        intArrayOf(0, 1, 2),
                    ),
                    structuralKey,
                ),
            ),
            uniformSeal,
        )
    }
}
