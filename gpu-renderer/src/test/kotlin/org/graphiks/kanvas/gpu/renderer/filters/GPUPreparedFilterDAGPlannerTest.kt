package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GPUPreparedFilterDAGPlannerTest {

    private val planner = GPUPreparedFilterDAGPlanner

    @Test
    fun `blur node as materialization boundary gets NativeRender route`() {
        val blur = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("blur1"), GPUPreparedFilterKind.Blur,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            BlurParams(2f, 3f), "test",
        )
        val graph = graphOf(blur)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), setOf(blur.id),
        )

        val plan = planner.plan(normalization)

        val route = plan.nodeRoutes[blur.id]
        assertTrue(route is GPUFilterNodeRoute.NativeRender,
            "blur materialization node should be NativeRender, got $route")
        assertEquals(1, plan.intermediateTextures.size)
        assertEquals(blur.id, plan.intermediateTextures[0].nodeId)
    }

    @Test
    fun `offset node gets FoldedMaterial route`() {
        val offset = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("offset1"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            OffsetParams(10f, 20f), "test",
        )
        val graph = graphOf(offset)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), emptySet(),
        )

        val plan = planner.plan(normalization)

        val route = plan.nodeRoutes[offset.id]
        assertTrue(route is GPUFilterNodeRoute.FoldedMaterial,
            "offset node should be FoldedMaterial, got $route")
    }

    @Test
    fun `crop node gets FoldedMaterial route`() {
        val crop = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("crop1"), GPUPreparedFilterKind.Crop,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            CropParams(0f, 0f, 100f, 100f), "test",
        )
        val graph = graphOf(crop)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), emptySet(),
        )

        val plan = planner.plan(normalization)

        val route = plan.nodeRoutes[crop.id]
        assertTrue(route is GPUFilterNodeRoute.FoldedMaterial,
            "crop node should be FoldedMaterial, got $route")
    }

    @Test
    fun `identity color filter gets FoldedMaterial route`() {
        val identityMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val colorFilter = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("cf1"), GPUPreparedFilterKind.ColorFilter,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            ColorFilterParams(identityMatrix), "test",
        )
        val graph = graphOf(colorFilter)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), emptySet(),
        )

        val plan = planner.plan(normalization)

        val route = plan.nodeRoutes[colorFilter.id]
        assertTrue(route is GPUFilterNodeRoute.FoldedMaterial,
            "identity color filter should be FoldedMaterial, got $route")
    }

    @Test
    fun `execution order matches graph node order`() {
        val n0 = offsetNode("n0")
        val n1 = blurNode("n1", 2f, 2f)
        val n2 = cropNode("n2")
        val graph = graphOf(n0, n1, n2)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), setOf(n1.id),
        )

        val plan = planner.plan(normalization)

        assertEquals(
            listOf(n0.id, n1.id, n2.id),
            plan.executionOrder,
        )
    }

    @Test
    fun `intermediate texture count matches materialization boundary count`() {
        val n0 = offsetNode("n0")
        val n1 = blurNode("n1", 2f, 2f)
        val n2 = blurNode("n2", 3f, 3f)
        val graph = graphOf(n0, n1, n2)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), setOf(n1.id, n2.id),
        )

        val plan = planner.plan(normalization)

        assertEquals(2, plan.intermediateTextures.size)
        assertEquals(n1.id, plan.intermediateTextures[0].nodeId)
        assertEquals(n2.id, plan.intermediateTextures[1].nodeId)
    }

    @Test
    fun `unsupported filter kind gets Refused route`() {
        val dropShadow = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("ds1"), GPUPreparedFilterKind.DropShadow,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            DropShadowParams(5f, 5f, 2f, 2f, floatArrayOf(0f, 0f, 0f, 0.5f)), "test",
        )
        val graph = graphOf(dropShadow)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), emptySet(),
        )

        val plan = planner.plan(normalization)

        val route = plan.nodeRoutes[dropShadow.id]
        assertTrue(route is GPUFilterNodeRoute.Refused,
            "unsupported filter kind should be Refused, got $route")
        val refused = route as GPUFilterNodeRoute.Refused
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `non-identity color filter gets Refused route`() {
        val tintMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0.1f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val colorFilter = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("tint"), GPUPreparedFilterKind.ColorFilter,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            ColorFilterParams(tintMatrix), "test",
        )
        val graph = graphOf(colorFilter)
        val normalization = GPUPreparedFilterNormalization(
            graph, emptyList(), emptySet(),
        )

        val plan = planner.plan(normalization)

        val route = plan.nodeRoutes[colorFilter.id]
        assertTrue(route is GPUFilterNodeRoute.Refused,
            "non-identity color filter should be Refused, got $route")
    }

    private fun graphOf(vararg nodes: GPUPreparedFilterNode): GPUPreparedFilterGraph {
        val list = nodes.toList()
        val output = if (list.isNotEmpty()) {
            GPUPreparedFilterInputRef.Node(list.last().id)
        } else {
            GPUPreparedFilterInputRef.ImplicitSource
        }
        val identity = GPUPreparedFilterGraph.computeIdentity(list, output)
        return GPUPreparedFilterGraph(list, output, identity)
    }

    private fun offsetNode(id: String, dx: Float = 1f, dy: Float = 0f) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(dx, dy), "test/$id")

    private fun blurNode(id: String, sx: Float, sy: Float) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.Blur,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), BlurParams(sx, sy), "test/$id")

    private fun cropNode(id: String) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.Crop,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            CropParams(0f, 0f, 100f, 100f), "test/$id")
}
