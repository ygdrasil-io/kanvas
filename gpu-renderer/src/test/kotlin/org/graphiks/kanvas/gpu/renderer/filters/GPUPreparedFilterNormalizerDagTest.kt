package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GPUPreparedFilterNormalizerDagTest {

    private val normalizer = GPUPreparedFilterNormalizer()
    private val emptyBounds = emptyMap<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>()
    private val passthroughColor = GPUFilterColorPlan("sRGB", "sRGB", "passthrough")

    @Test
    fun `adjacent nodes in list but on independent branches are not merged`() {
        val graph = independentBranchGraph()
        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        assertEquals(3, result.graph.nodes.size)
        assertEquals(0, result.rewrites.size)
    }

    @Test
    fun `node with two consumers is not removed when only one path merges`() {
        val graph = fanOutGraph()
        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        assertTrue(result.graph.nodes.any { it.id.value == "n0" }, "shared node n0 must survive")
    }

    @Test
    fun `output not at end of topological order is preserved`() {
        val n0 = offsetNode("n0", 1f, 0f)
        val n1 = offsetNode("n1", 2f, 0f)
        val graph = graphWithOutput(listOf(n0, n1), GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        assertEquals(GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")), result.graph.output)
    }

    @Test
    fun `removed output is rewired to its exact source not the last unrelated node`() {
        val identity = identityNode("identity", GPUPreparedFilterInputRef.ImplicitSource)
        val unrelated = blurNode("unrelated", 3f, 3f)
        val graph = graphWithOutput(
            listOf(identity, unrelated),
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("identity")),
        )

        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)

        assertEquals(GPUPreparedFilterInputRef.ImplicitSource, result.graph.output)
        assertEquals(listOf("unrelated"), result.graph.nodes.map { it.id.value })
    }

    @Test
    fun `graph output producer is not folded into a dead consumer`() {
        val producer = offsetNode("producer", 1f, 0f)
        val deadConsumer = offsetNode(
            "dead",
            2f,
            0f,
            GPUPreparedFilterInputRef.Node(producer.id),
        )
        val graph = graphWithOutput(
            listOf(producer, deadConsumer),
            GPUPreparedFilterInputRef.Node(producer.id),
        )

        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)

        assertEquals(GPUPreparedFilterInputRef.Node(producer.id), result.graph.output)
        assertEquals(2, result.graph.nodes.size)
    }

    @Test
    fun `identity on ImplicitSource produces ImplicitSource not TransparentBlack`() {
        val graph = graphOf(identityNode("n0", GPUPreparedFilterInputRef.ImplicitSource))
        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        assertEquals(0, result.graph.nodes.size)
        assertEquals(GPUPreparedFilterInputRef.ImplicitSource, result.graph.output)
    }

    @Test
    fun `identity removal chains through to consumers`() {
        val n0 = identityNode("n0", GPUPreparedFilterInputRef.ImplicitSource)
        val n1 = offsetNode("n1", 5f, 0f, GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        val graph = graphWithOutput(listOf(n0, n1), GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1")))
        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        val remaining = result.graph.nodes.first()
        val input = remaining.inputs.first()
        assertEquals(GPUPreparedFilterInputRef.ImplicitSource, input)
    }

    @Test
    fun `identity removal with multiple consumers recables all`() {
        val n0 = identityNode("n0", GPUPreparedFilterInputRef.ImplicitSource)
        val n1 = blurNode("n1", 3f, 3f, GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        val n2 = blurNode("n2", 4f, 4f, GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        val graph = graphOf(n0, n1, n2)
        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        assertEquals(2, result.graph.nodes.size)
        result.graph.nodes.forEach { node ->
            assertEquals(GPUPreparedFilterInputRef.ImplicitSource, node.inputs.first())
        }
    }

    @Test
    fun `identity removal keeps merge parameter inputs synchronized`() {
        val identity = identityNode("identity", GPUPreparedFilterInputRef.ImplicitSource)
        val mergeInputs = listOf(
            GPUPreparedFilterInputRef.Node(identity.id),
            GPUPreparedFilterInputRef.TransparentBlack,
        )
        val merge = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("merge"),
            kind = GPUPreparedFilterKind.Merge,
            inputs = mergeInputs,
            parameters = MergeParams(mergeInputs),
            provenance = "test/merge",
        )
        val graph = graphWithOutput(
            listOf(identity, merge),
            GPUPreparedFilterInputRef.Node(merge.id),
        )

        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        val normalizedMerge = result.graph.nodes.single()
        val expectedInputs = listOf(
            GPUPreparedFilterInputRef.ImplicitSource,
            GPUPreparedFilterInputRef.TransparentBlack,
        )

        assertEquals(expectedInputs, normalizedMerge.inputs)
        assertEquals(expectedInputs, (normalizedMerge.parameters as MergeParams).inputs)
    }

    @Test
    fun `non-commutative matrix order produces different identity`() {
        val m1 = floatArrayOf(
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val m2 = floatArrayOf(
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val nA = colorNode("a", m1)
        val nB = colorNode("b", m2)
        val g1 = graphOf(nA, nB)
        val nC = colorNode("c", m2)
        val nD = colorNode("d", m1)
        val g2 = graphOf(nC, nD)
        assertTrue(g1.identity != g2.identity)
    }

    @Test
    fun `color matrices compose in execution order consumer times producer`() {
        val producerMatrix = identityMatrix().also { it[4] = 1f }
        val consumerMatrix = identityMatrix().also { it[0] = 2f }
        val producer = colorNode("producer", producerMatrix)
        val consumer = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("consumer"),
            GPUPreparedFilterKind.ColorFilter,
            listOf(GPUPreparedFilterInputRef.Node(producer.id)),
            ColorFilterParams(consumerMatrix),
            "test/consumer",
        )
        val graph = graphWithOutput(
            listOf(producer, consumer),
            GPUPreparedFilterInputRef.Node(consumer.id),
        )

        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)
        val matrix = (result.graph.nodes.single().parameters as ColorFilterParams).matrix

        assertEquals(2f, matrix[0])
        assertEquals(2f, matrix[4])
    }

    @Test
    fun `crops with different tile modes are not merged`() {
        val producer = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("producer"),
            GPUPreparedFilterKind.Crop,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            CropParams(0f, 0f, 20f, 20f, GPUTileMode.Clamp),
            "test/producer",
        )
        val consumer = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("consumer"),
            GPUPreparedFilterKind.Crop,
            listOf(GPUPreparedFilterInputRef.Node(producer.id)),
            CropParams(1f, 1f, 10f, 10f, GPUTileMode.Decal),
            "test/consumer",
        )
        val graph = graphWithOutput(
            listOf(producer, consumer),
            GPUPreparedFilterInputRef.Node(consumer.id),
        )

        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)

        assertEquals(2, result.graph.nodes.size)
        assertEquals(0, result.rewrites.size)
    }

    @Test
    fun `merged node id cannot collide with an existing node id`() {
        val producer = offsetNode("a", 1f, 0f)
        val consumer = offsetNode(
            "b",
            2f,
            0f,
            GPUPreparedFilterInputRef.Node(producer.id),
        )
        val preexistingCollision = blurNode("a_b_merged", 3f, 3f)
        val graph = graphWithOutput(
            listOf(producer, consumer, preexistingCollision),
            GPUPreparedFilterInputRef.Node(consumer.id),
        )

        val result = normalizer.normalize(graph, emptyBounds, passthroughColor)

        assertEquals(result.graph.nodes.size, result.graph.nodes.map { it.id }.distinct().size)
        assertEquals(
            GPUPreparedFilterKind.Offset,
            result.graph.nodes.single { GPUPreparedFilterInputRef.Node(it.id) == result.graph.output }.kind,
        )
    }

    @Test
    fun `color space barrier nodes prevent merging`() {
        val n0 = colorNode("n0", colorMatrix(0.21f))
        val n1 = colorNode("n1", colorMatrix(0.72f))
        val graph = graphOf(n0, n1)
        val barrierColor = GPUFilterColorPlan("displayP3", "sRGB", "convert")
        val result = normalizer.normalize(graph, emptyBounds, barrierColor)
        assertEquals(2, result.graph.nodes.size)
    }

    private fun independentBranchGraph(): GPUPreparedFilterGraph {
        val n0 = offsetNode("n0", 1f, 0f)
        val n1 = offsetNode("n1", 2f, 0f)
        val n2 = blurNode("n2", 3f, 3f,
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        return graphOf(n0, n1, n2)
    }

    private fun fanOutGraph(): GPUPreparedFilterGraph {
        val n0 = offsetNode("n0", 1f, 0f)
        val n1 = blurNode("n1", 3f, 3f,
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        val n2 = blurNode("n2", 4f, 4f,
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")))
        return graphOf(n0, n1, n2)
    }

    private fun graphOf(vararg nodes: GPUPreparedFilterNode): GPUPreparedFilterGraph {
        val list = nodes.toList()
        val output = GPUPreparedFilterInputRef.Node(list.last().id)
        val identity = GPUPreparedFilterGraph.computeIdentity(list, output)
        return GPUPreparedFilterGraph(list, output, identity)
    }

    private fun graphWithOutput(nodes: List<GPUPreparedFilterNode>, output: GPUPreparedFilterInputRef): GPUPreparedFilterGraph {
        val identity = GPUPreparedFilterGraph.computeIdentity(nodes, output)
        return GPUPreparedFilterGraph(nodes, output, identity)
    }

    private fun offsetNode(id: String, dx: Float, dy: Float, input: GPUPreparedFilterInputRef = GPUPreparedFilterInputRef.ImplicitSource) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.Offset, listOf(input), OffsetParams(dx, dy), "test/$id")

    private fun blurNode(id: String, sx: Float, sy: Float, input: GPUPreparedFilterInputRef = GPUPreparedFilterInputRef.ImplicitSource) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.Blur, listOf(input), BlurParams(sx, sy), "test/$id")

    private fun identityNode(id: String, input: GPUPreparedFilterInputRef) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.ColorFilter, listOf(input),
            ColorFilterParams(floatArrayOf(1f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1f,0f)), "test/$id")

    private fun colorNode(id: String, m: FloatArray) =
        GPUPreparedFilterNode(GPUPreparedFilterNodeId(id), GPUPreparedFilterKind.ColorFilter, listOf(GPUPreparedFilterInputRef.ImplicitSource),
            ColorFilterParams(m.copyOf()), "test/$id")

    private fun colorMatrix(v: Float) = floatArrayOf(v,0f,0f,0f,0f, 0f,v,0f,0f,0f, 0f,0f,v,0f,0f, 0f,0f,0f,1f,0f)

    private fun identityMatrix() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
