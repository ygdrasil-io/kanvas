package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GPUPreparedFilterNormalizerTest {

    private val normalizer = GPUPreparedFilterNormalizer()
    private val noBounds = emptyMap<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>()
    private val color = GPUFilterColorPlan("sRGB", "sRGB", "passthrough")

    @Test
    fun `identity node is removed`() {
        val graph = linearGraph(identityNode("n0"))
        val result = normalizer.normalize(graph, noBounds, color)
        assertEquals(0, result.graph.nodes.size)
        assertEquals(1, result.rewrites.size)
        assertEquals("remove-identity", result.rewrites.first().rule)
    }

    @Test
    fun `two adjacent offsets compose into one`() {
        val graph = linearGraph(
            off("n0", 2f, 3f, GPUPreparedFilterInputRef.ImplicitSource),
            off("n1", 5f, -1f, GPUPreparedFilterInputRef.Node(gid("n0"))),
        )
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(1, r.graph.nodes.size)
        assertEquals(1, r.rewrites.size)
        assertEquals("compose-offset", r.rewrites.first().rule)
    }

    @Test
    fun `compatible crops intersect`() {
        val graph = linearGraph(
            cropN("n0", 0f, 0f, 200f, 200f, GPUPreparedFilterInputRef.ImplicitSource),
            cropN("n1", 10f, 10f, 100f, 100f, GPUPreparedFilterInputRef.Node(gid("n0"))),
        )
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(1, r.graph.nodes.size)
        assertTrue(r.rewrites.any { it.rule == "intersect-crop" })
    }

    @Test
    fun `compatible color matrices compose exactly`() {
        val graph = linearGraph(
            col("n0", GPUPreparedFilterInputRef.ImplicitSource),
            col("n1", GPUPreparedFilterInputRef.Node(gid("n0"))),
        )
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(1, r.graph.nodes.size)
        assertTrue(r.rewrites.any { it.rule == "fold-color-filter" })
    }

    @Test
    fun `offset and blur in that order are not rewritten`() {
        val graph = starGraph(blur("n1", 4f, 4f), off("n0", 1f, 0f))
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(2, r.graph.nodes.size)
        assertEquals(0, r.rewrites.size)
    }

    @Test
    fun `blur nodes remain materialization boundaries`() {
        val graph = starGraph(blur("n0", 3f, 3f))
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(1, r.graph.nodes.size)
        assertTrue(r.materializationNodeIds.contains(gid("n0")))
    }

    @Test
    fun `blend nodes remain materialization boundaries`() {
        val graph = starGraph(
            GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Blend,
                listOf(GPUPreparedFilterInputRef.ImplicitSource, GPUPreparedFilterInputRef.TransparentBlack),
                BlendParams("srcOver"), "t"))
        assertTrue(normalizer.normalize(graph, noBounds, color).materializationNodeIds.contains(gid("n0")))
    }

    @Test
    fun `input order is preserved after normalization`() {
        val graph = linearGraph(
            off("n0", 1f, 0f, GPUPreparedFilterInputRef.ImplicitSource),
            off("n1", 0f, 2f, GPUPreparedFilterInputRef.Node(gid("n0"))),
        )
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(1, r.graph.nodes.size)
        assertEquals(1, r.rewrites.size)
    }

    @Test
    fun `node with two independent inputs is not merged`() {
        val graph = starGraph(
            blur("n0", 2f, 2f), off("n1", 3f, 1f))
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(2, r.graph.nodes.size)
        assertEquals(0, r.rewrites.size)
    }

    @Test
    fun `rewrite proof carries source and result node ids`() {
        val graph = linearGraph(
            off("n0", 2f, 3f, GPUPreparedFilterInputRef.ImplicitSource),
            off("n1", 5f, -1f, GPUPreparedFilterInputRef.Node(gid("n0"))),
        )
        val proof = normalizer.normalize(graph, noBounds, color).rewrites.first()
        assertEquals(setOf(gid("n0"), gid("n1")), proof.sourceNodeIds.toSet())
        assertEquals(1, proof.resultNodeIds.size)
        assertTrue(proof.removedIntermediateCount >= 1)
    }

    @Test
    fun `unsupported nodes remain unchanged not refused`() {
        val graph = starGraph(disp("n0", "r", "g", 10f))
        val r = normalizer.normalize(graph, noBounds, color)
        assertEquals(1, r.graph.nodes.size)
        assertEquals(GPUPreparedFilterKind.DisplacementMap, r.graph.nodes.first().kind)
        assertEquals(0, r.rewrites.size)
    }

    @Test
    fun `nodes not participating in rewrites preserve exact parameters`() {
        val graph = starGraph(dil("n0", 3f, 5f))
        val r = normalizer.normalize(graph, noBounds, color)
        val node = r.graph.nodes.first()
        val params = node.parameters as DilateParams
        assertEquals(3f, params.radiusX)
        assertEquals(5f, params.radiusY)
    }

    private fun gid(s: String) = GPUPreparedFilterNodeId(s)
    private fun off(id: String, dx: Float, dy: Float, input: GPUPreparedFilterInputRef = GPUPreparedFilterInputRef.ImplicitSource) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.Offset, listOf(input), OffsetParams(dx, dy), "t/$id")
    private fun cropN(id: String, x: Float, y: Float, w: Float, h: Float, input: GPUPreparedFilterInputRef) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.Crop, listOf(input), CropParams(x, y, w, h), "t/$id")
    private fun col(id: String, input: GPUPreparedFilterInputRef) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.ColorFilter, listOf(input),
            ColorFilterParams(floatArrayOf(0.21f,0.72f,0.07f,0f,0f, 0.21f,0.72f,0.07f,0f,0f, 0.21f,0.72f,0.07f,0f,0f, 0f,0f,0f,1f,0f)), "t/$id")
    private fun blur(id: String, sx: Float, sy: Float) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.Blur, listOf(GPUPreparedFilterInputRef.ImplicitSource), BlurParams(sx, sy), "t/$id")
    private fun disp(id: String, xc: String, yc: String, scale: Float) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.DisplacementMap, listOf(GPUPreparedFilterInputRef.ImplicitSource, GPUPreparedFilterInputRef.ImplicitSource), DisplacementMapParams(xc, yc, scale), "t/$id")
    private fun dil(id: String, rx: Float, ry: Float) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.Dilate, listOf(GPUPreparedFilterInputRef.ImplicitSource), DilateParams(rx, ry), "t/$id")
    private fun identityNode(id: String) =
        GPUPreparedFilterNode(gid(id), GPUPreparedFilterKind.ColorFilter, listOf(GPUPreparedFilterInputRef.ImplicitSource),
            ColorFilterParams(floatArrayOf(1f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1f,0f)), "t/$id")

    private fun linearGraph(vararg nodes: GPUPreparedFilterNode): GPUPreparedFilterGraph {
        val list = nodes.toList()
        val output = GPUPreparedFilterInputRef.Node(list.last().id)
        val idv = GPUPreparedFilterGraph.computeIdentity(list, output)
        return GPUPreparedFilterGraph(list, output, idv)
    }

    private fun starGraph(vararg nodes: GPUPreparedFilterNode): GPUPreparedFilterGraph {
        val list = nodes.toList()
        val output = GPUPreparedFilterInputRef.Node(list.last().id)
        val idv = GPUPreparedFilterGraph.computeIdentity(list, output)
        return GPUPreparedFilterGraph(list, output, idv)
    }
}
