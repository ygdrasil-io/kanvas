package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUPreparedFilterNormalizerTest {

    private val normalizer = GPUPreparedFilterNormalizer()

    @Test
    fun `identity node is removed`() {
        val graph = graphOf(
            identityNode("n0"),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(0, result.graph.nodes.size)
        assertEquals(1, result.rewrites.size)
        assertEquals("remove-identity", result.rewrites.first().rule)
        assertEquals(1, result.rewrites.first().removedIntermediateCount)
    }

    @Test
    fun `two adjacent offsets compose into one`() {
        val graph = graphOf(
            offsetNode("n0", 2f, 3f),
            offsetNode("n1", 5f, -1f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(1, result.graph.nodes.size)
        assertEquals(1, result.rewrites.size)
        assertEquals("compose-offset", result.rewrites.first().rule)
        assertEquals(1, result.rewrites.first().removedIntermediateCount)
    }

    @Test
    fun `compatible crops intersect`() {
        val graph = graphOf(
            cropNode("n0", 0f, 0f, 200f, 200f),
            cropNode("n1", 10f, 10f, 100f, 100f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(1, result.graph.nodes.size)
        assertTrue(result.rewrites.any { it.rule == "intersect-crop" })
    }

    @Test
    fun `compatible color matrices compose exactly`() {
        val graph = graphOf(
            colorMatrixNode("n0"),
            colorMatrixNode("n1"),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(1, result.graph.nodes.size)
        assertTrue(result.rewrites.any { it.rule == "fold-color-filter" })
    }

    @Test
    fun `input order is preserved after normalization`() {
        val graph = graphOf(
            offsetNode("n0", 1f, 0f),
            offsetNode("n1", 0f, 2f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(1, result.graph.nodes.size)
        assertEquals(1, result.rewrites.size)
        assertEquals("compose-offset", result.rewrites.first().rule)
    }

    @Test
    fun `blur nodes remain materialization boundaries`() {
        val graph = graphOf(
            blurNode("n0", 3f, 3f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(1, result.graph.nodes.size)
        assertTrue(
            result.materializationNodeIds.contains(GPUPreparedFilterNodeId("n0")),
            "blur node must be marked as materialization boundary",
        )
    }

    @Test
    fun `blend nodes remain materialization boundaries`() {
        val graph = graphOf(blendNode("n0", "srcOver"))
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertTrue(result.materializationNodeIds.contains(GPUPreparedFilterNodeId("n0")))
    }

    @Test
    fun `incompatible adjacent nodes are not rewritten`() {
        val graph = graphOf(
            blurNode("n0", 2f, 2f),
            offsetNode("n1", 3f, 1f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(2, result.graph.nodes.size)
        assertEquals(0, result.rewrites.size)
    }

    @Test
    fun `offset and blur in that order are not rewritten`() {
        val graph = graphOf(
            offsetNode("n0", 1f, 0f),
            blurNode("n1", 4f, 4f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(2, result.graph.nodes.size)
        assertEquals(0, result.rewrites.size)
    }

    @Test
    fun `rewrite proof carries source and result node ids`() {
        val graph = graphOf(
            offsetNode("n0", 2f, 3f),
            offsetNode("n1", 5f, -1f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        val proof = result.rewrites.first()
        assertEquals(
            setOf(GPUPreparedFilterNodeId("n0"), GPUPreparedFilterNodeId("n1")),
            proof.sourceNodeIds.toSet(),
        )
        assertEquals(1, proof.resultNodeIds.size)
        assertTrue(proof.removedIntermediateCount >= 1)
        assertTrue(proof.inputBoundsIdentity.isNotBlank())
        assertTrue(proof.outputBoundsIdentity.isNotBlank())
    }

    @Test
    fun `unsupported nodes remain unchanged not refused`() {
        val graph = graphOf(
            displacementNode("n0", "r", "g", 10f),
        )
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        assertEquals(1, result.graph.nodes.size)
        assertEquals(GPUPreparedFilterKind.DisplacementMap, result.graph.nodes.first().kind)
        assertEquals(0, result.rewrites.size)
    }

    @Test
    fun `nodes not participating in rewrites preserve exact parameters`() {
        val graph = graphOf(dilateNode("n0", 3f, 5f))
        val result = normalizer.normalize(graph, emptyBounds(), emptyColorFacts())
        val node = result.graph.nodes.first()
        val params = node.parameters as DilateParams
        assertEquals(3f, params.radiusX)
        assertEquals(5f, params.radiusY)
    }

    private fun identityNode(id: String) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.ColorFilter,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
        parameters = ColorFilterParams(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )),
        provenance = "test/$id",
    )

    private fun offsetNode(id: String, dx: Float, dy: Float) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.Offset,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
        parameters = OffsetParams(dx, dy),
        provenance = "test/$id",
    )

    private fun cropNode(id: String, x: Float, y: Float, w: Float, h: Float) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.Crop,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
        parameters = CropParams(x, y, w, h),
        provenance = "test/$id",
    )

    private fun colorMatrixNode(id: String) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.ColorFilter,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
        parameters = ColorFilterParams(floatArrayOf(
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )),
        provenance = "test/$id",
    )

    private fun blurNode(id: String, sx: Float, sy: Float) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.Blur,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
        parameters = BlurParams(sx, sy),
        provenance = "test/$id",
    )

    private fun blendNode(id: String, mode: String) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.Blend,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource, GPUPreparedFilterInputRef.TransparentBlack),
        parameters = BlendParams(mode),
        provenance = "test/$id",
    )

    private fun displacementNode(id: String, xc: String, yc: String, scale: Float) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.DisplacementMap,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource, GPUPreparedFilterInputRef.ImplicitSource),
        parameters = DisplacementMapParams(xc, yc, scale),
        provenance = "test/$id",
    )

    private fun dilateNode(id: String, rx: Float, ry: Float) = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(id),
        kind = GPUPreparedFilterKind.Dilate,
        inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
        parameters = DilateParams(rx, ry),
        provenance = "test/$id",
    )

    private fun graphOf(vararg nodes: GPUPreparedFilterNode): GPUPreparedFilterGraph {
        val list = nodes.toList()
        val output = if (list.isEmpty()) GPUPreparedFilterInputRef.TransparentBlack
        else GPUPreparedFilterInputRef.Node(list.last().id)
        return GPUPreparedFilterGraph(
            nodes = list,
            output = output,
            identity = "test",
        )
    }

    private fun emptyBounds(): Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan> = emptyMap()

    private fun emptyColorFacts(): GPUFilterColorPlan =
        GPUFilterColorPlan("sRGB", "sRGB", "passthrough")
}
