package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID

class GPUCorePrimitiveFrameGeometryArenaTest {
    @Test
    fun `frame seal isolates equal packet identities owned by different render scopes`() {
        val packetId = GPUDrawPacketID("packet.shared")
        val first = GPUCorePrimitiveDirectNativeRoute.Accepted(floatArrayOf(1f, 2f), intArrayOf(0))
        val second = GPUCorePrimitiveDirectNativeRoute.Accepted(floatArrayOf(8f, 9f), intArrayOf(0))
        val frameSeal = GPUCorePrimitiveDirectNativeFrameRouteSeal(
            mapOf(
                GPUCorePrimitiveDirectNativeFrameRouteKey(3, packetId) to first,
                GPUCorePrimitiveDirectNativeFrameRouteKey(7, packetId) to second,
            ),
        )

        val firstScope = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            frameSeal.retainedFor(3, listOf(packetId)),
        )
        val secondScope = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            frameSeal.retainedFor(7, listOf(packetId)),
        )

        assertSame(first, firstScope.routesByPacketId.getValue(packetId))
        assertSame(second, secondScope.routesByPacketId.getValue(packetId))
    }

    @Test
    fun `route seal snapshots geometry once and retains the same accepted route for its scope`() {
        val packetId = GPUDrawPacketID("packet.direct")
        val vertices = floatArrayOf(1f, 2f, 9f, 2f, 9f, 8f, 1f, 8f)
        val indices = intArrayOf(0, 2, 1, 0, 3, 2)
        val classifiedRoute = GPUCorePrimitiveDirectNativeRoute.Accepted(vertices, indices)
        val seal = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
                mapOf(packetId to classifiedRoute),
            ),
        )
        val sealedRoute = seal.routesByPacketId.getValue(packetId)

        vertices[0] = 99f
        indices[0] = 99

        val copiedVertices = FloatArray(sealedRoute.vertexCount * 2)
        val copiedIndices = IntArray(sealedRoute.indexCount)
        sealedRoute.copyVerticesInto(copiedVertices)
        sealedRoute.copyIndicesInto(copiedIndices)
        assertContentEquals(floatArrayOf(1f, 2f, 9f, 2f, 9f, 8f, 1f, 8f), copiedVertices)
        assertContentEquals(intArrayOf(0, 2, 1, 0, 3, 2), copiedIndices)
        assertEquals(4, sealedRoute.vertexCount)
        assertEquals(6, sealedRoute.indexCount)
        assertEquals(3, sealedRoute.maxLocalIndex)
        assertSame(classifiedRoute, sealedRoute)
        assertFalse(
            sealedRoute.javaClass.methods.any { method ->
                method.name == "getVertices" || method.name == "getIndices"
            },
            "Accepted must not expose boxed geometry lists",
        )
        val scoped = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            seal.retainedFor(listOf(packetId)),
        )
        assertSame(sealedRoute, scoped.routesByPacketId.getValue(packetId))
        assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Empty>(
            seal.retainedFor(listOf(GPUDrawPacketID("packet.other"))),
        )
        assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Missing>(
            seal.retainedFor(listOf(packetId, GPUDrawPacketID("packet.other"))),
        )
    }

    @Test
    fun `all frame draws share one vertex and one index slab with exact draw offsets`() {
        val rect = GPUCorePrimitiveDirectNativeRoute.Accepted(
            vertices = floatArrayOf(1f, 2f, 9f, 2f, 9f, 8f, 1f, 8f),
            indices = intArrayOf(0, 2, 1, 0, 3, 2),
        )
        val triangle = GPUCorePrimitiveDirectNativeRoute.Accepted(
            vertices = floatArrayOf(12f, 4f, 20f, 5f, 14f, 15f),
            indices = intArrayOf(2, 0, 1),
        )

        val arena = packCorePrimitiveFrameGeometry(listOf(rect, triangle))

        assertContentEquals(
            floatArrayOf(1f, 2f, 9f, 2f, 9f, 8f, 1f, 8f, 12f, 4f, 20f, 5f, 14f, 15f),
            arena.vertices,
        )
        assertContentEquals(intArrayOf(0, 2, 1, 0, 3, 2, 2, 0, 1), arena.indices)
        assertEquals(listOf(0, 6), arena.slices.map { it.firstIndex })
        assertEquals(listOf(6, 3), arena.slices.map { it.indexCount })
        assertEquals(listOf(0, 4), arena.slices.map { it.baseVertex })
        assertEquals(listOf(4, 3), arena.slices.map { it.vertexCount })
        assertEquals(listOf(3, 2), arena.slices.map { it.maxLocalIndex })
    }

    @Test
    fun `accepted route rejects negative and out of range local indices`() {
        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitiveDirectNativeRoute.Accepted(
                floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
                intArrayOf(0, -1, 2),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUCorePrimitiveDirectNativeRoute.Accepted(
                floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
                intArrayOf(0, 1, 3),
            )
        }
    }

    @Test
    fun `arena hot path allocates primitive slabs directly without boxed staging lists`() {
        val source = File(
            "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveNativeRoute.kt",
        ).readText()
        val hotPath = source.substringAfter("internal fun packCorePrimitiveFrameGeometry(")

        assertEquals(1, Regex("""\bFloatArray\(""").findAll(hotPath).count())
        assertEquals(1, Regex("""\bIntArray\(""").findAll(hotPath).count())
        assertFalse(hotPath.contains("mutableListOf"))
        assertFalse(hotPath.contains("toFloatArray"))
        assertFalse(hotPath.contains("toIntArray"))
    }
}
