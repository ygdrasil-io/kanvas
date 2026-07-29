package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedFilterDescriptorsTest {

    @Test
    fun `graph snapshots mutable kernel input and identity changes by exact bits`() {
        val kernel = floatArrayOf(1f, 2f, 3f, 4f)
        val first = matrixConvolutionNode("n0", 2, 2, kernel)
        kernel[0] = 99f
        val second = matrixConvolutionNode("n0", 2, 2, floatArrayOf(1f, 2f, 3f, 4f))
        assertEquals(first.parameters, second.parameters)
        assertEquals(first.canonicalIdentity(), second.canonicalIdentity())
    }

    @Test
    fun `float arrays are copied on construction preserving exact bits`() {
        assertFailsWith<IllegalArgumentException> {
            MatrixConvolutionParams(
                kernel = floatArrayOf(0.5f, -1.0f, Float.NaN), kernelSizeX = 1, kernelSizeY = 3,
                gain = 2f, bias = 0.5f,
                kernelOffsetX = 0, kernelOffsetY = 0,
                convolveAlpha = true, tileMode = "clamp",
            )
        }
    }

    @Test
    fun `all 22 public image filter kinds are defined`() {
        val all = GPUPreparedFilterKind.entries
        assertEquals(22, all.size)
        val expected = setOf(
            GPUPreparedFilterKind.Crop,
            GPUPreparedFilterKind.Blur,
            GPUPreparedFilterKind.DropShadow,
            GPUPreparedFilterKind.ColorFilter,
            GPUPreparedFilterKind.Compose,
            GPUPreparedFilterKind.Blend,
            GPUPreparedFilterKind.Dilate,
            GPUPreparedFilterKind.Erode,
            GPUPreparedFilterKind.DistantLitDiffuse,
            GPUPreparedFilterKind.PointLitDiffuse,
            GPUPreparedFilterKind.SpotLitDiffuse,
            GPUPreparedFilterKind.DistantLitSpecular,
            GPUPreparedFilterKind.PointLitSpecular,
            GPUPreparedFilterKind.SpotLitSpecular,
            GPUPreparedFilterKind.Offset,
            GPUPreparedFilterKind.Tile,
            GPUPreparedFilterKind.Merge,
            GPUPreparedFilterKind.DisplacementMap,
            GPUPreparedFilterKind.Picture,
            GPUPreparedFilterKind.Magnifier,
            GPUPreparedFilterKind.MatrixConvolution,
            GPUPreparedFilterKind.RuntimeEffect,
        )
        assertEquals(expected, all.toSet())
    }

    @Test
    fun `input refs have stable equals based on content not identity`() {
        val a = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1"))
        val b = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `parameter classes with same content are equal`() {
        val a = OffsetParams(3f, -2.5f)
        val b = OffsetParams(3f, -2.5f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `parameter classes with different content are not equal`() {
        val a = OffsetParams(3f, -2.5f)
        val b = OffsetParams(3f, 0f)
        assertNotEquals(a, b)
    }

    @Test
    fun `graph identity is deterministic and order independent for map content`() {
        val n1 = blurNode("n1", 2f, 2f)
        val a = graphOf("n1" to n1)
        val b = graphOf("n1" to blurNode("n1", 2f, 2f))
        assertEquals(a.identity, b.identity)
    }

    @Test
    fun `graph identity differs when node parameters differ`() {
        val a = graphOf("n1" to blurNode("n1", 2f, 2f))
        val b = graphOf("n1" to blurNode("n1", 3f, 2f))
        assertNotEquals(a.identity, b.identity)
    }

    @Test
    fun `node id is an inline value class`() {
        val id = GPUPreparedFilterNodeId("test")
        assertEquals("test", id.value)
    }

    @Test
    fun `node id rejects blank value`() {
        try {
            GPUPreparedFilterNodeId("")
            assertTrue(false, "expected exception")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `Float NaN parameters are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BlurParams(Float.NaN, 1f)
        }
    }

    @Test
    fun `node input refs distinguish transparent black from implicit source`() {
        assertNotEquals(
            GPUPreparedFilterInputRef.TransparentBlack.identityFragment(),
            GPUPreparedFilterInputRef.ImplicitSource.identityFragment(),
        )
    }

    @Test
    fun `backdrop input ref carries destination plan identity`() {
        val ref = GPUPreparedFilterInputRef.Backdrop("plan_789")
        assertEquals("plan_789", ref.destinationPlanIdentity)
    }

    @Test
    fun `picture input ref carries picture identity`() {
        val ref = GPUPreparedFilterInputRef.Picture("pic_001")
        assertEquals("pic_001", ref.pictureIdentity)
    }

    @Test
    fun `filter node parameters include kind provenance and canonical identity`() {
        val node = cropNode("c1")
        assertEquals(GPUPreparedFilterKind.Crop, node.kind)
        assertEquals("test/c1", node.provenance)
        assertTrue(node.canonicalIdentity().isNotBlank())
    }

    @Test
    fun `DropShadow params snapshot color array`() {
        val color = floatArrayOf(0f, 0f, 0f, 0.5f)
        val params = DropShadowParams(4f, -2f, 3f, 5f, color)
        color[3] = 0.9f
        assertEquals(0.5f.toRawBits(), params.color[3].toRawBits())
    }

    @Test
    fun `Blend params preserve closed mode`() {
        val a = BlendParams("srcOver")
        val b = BlendParams("srcIn")
        assertEquals(org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.SRC_OVER, a.mode)
        assertNotEquals(a, b)
    }

    @Test
    fun `Merge params deterministically encode input order`() {
        val a = MergeParams(listOf(
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("a")),
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("b")),
        ))
        val b = MergeParams(listOf(
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("b")),
            GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("a")),
        ))
        assertNotEquals(a, b)
        assertNotEquals(a.canonicalIdentity(), b.canonicalIdentity())
    }

    @Test
    fun `DisplacementMap params snapshot channel selectors`() {
        val params = DisplacementMapParams(
            xChannel = "r", yChannel = "a", scale = 12f,
        )
        assertEquals(GPUColorChannel.R, params.xChannel)
        assertEquals(GPUColorChannel.A, params.yChannel)
    }

    @Test
    fun `Lighting params encode distant direction as unit vector facts`() {
        val params = DistantLitDiffuseParams(
            dx = 1f, dy = 0f, dz = 0f,
            surfaceScale = 1f, kd = 0.8f, color = floatArrayOf(1f, 1f, 1f),
        )
        assertEquals(0.8f, params.kd)
        assertEquals(1f.toRawBits(), params.color[0].toRawBits())
    }

    @Test
    fun `Magnifier params encode lens geometry`() {
        val a = MagnifierParams(0f, 0f, 100f, 100f, 0f, 0f, 100f, 100f, 2f, 0f)
        val b = MagnifierParams(0f, 0f, 100f, 100f, 0f, 0f, 100f, 100f, 2f, 0f)
        assertEquals(a, b)
        assertEquals(a.canonicalIdentity(), b.canonicalIdentity())
    }

    @Test
    fun `MatrixConvolution params validate kernel dimensions`() {
        val kernel = floatArrayOf(0f, 1f, 0f, 1f, -4f, 1f, 0f, 1f, 0f)
        val params = MatrixConvolutionParams(
            kernel = kernel, kernelSizeX = 3, kernelSizeY = 3,
            gain = 1f, bias = 0f,
            kernelOffsetX = 0, kernelOffsetY = 0,
            convolveAlpha = false, tileMode = "clamp",
        )
        assertTrue(params.kernelHash.isNotBlank())
    }

    @Test
    fun `RuntimeEffect params encode stable effect id and uniforms`() {
        val params = RuntimeEffectParams(
            effectId = "simple_rt", effectVersion = 1,
            uniforms = mapOf("gColor" to floatArrayOf(1f, 0f, 0f, 0.5f)),
            children = mapOf("source" to GPUPreparedFilterInputRef.ImplicitSource),
        )
        assertEquals("simple_rt", params.effectId)
        assertEquals(1, params.children.size)
    }

    @Test
    fun `ColorFilter params encode color matrix row-major facts`() {
        val matrix = floatArrayOf(
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val params = ColorFilterParams(matrix)
        matrix[0] = 99f
        assertEquals(0.21f.toRawBits(), params.matrix[0].toRawBits())
    }

    @Test
    fun `Tile params encode source and destination rects`() {
        val params = TileParams(10f, 20f, 60f, 40f, 5f, 15f, 55f, 35f)
        assertEquals(10f, params.srcLeft)
        assertEquals(60f, params.srcRight)
        assertEquals(5f, params.dstX)
        assertEquals(35f, params.dstBottom)
    }

    @Test
    fun `Picture params encode source picture identity`() {
        val params = PictureParams("picture_abc123")
        assertEquals("picture_abc123", params.pictureIdentity)
    }

    @Test
    fun `Compose params preserve inner and outer input order`() {
        val params = ComposeParams(
            inner = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("i")),
            outer = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("o")),
        )
        assertEquals("i", (params.inner as GPUPreparedFilterInputRef.Node).id.value)
        assertEquals("o", (params.outer as GPUPreparedFilterInputRef.Node).id.value)
    }

    @Test
    fun `filter graph requires acyclic validation marker`() {
        val n1 = offsetNode("n1", 1f, 0f)
        val n2 = blurNode("n2", 2f, 2f, inputRef = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1")))
        val graph = graphOf("n1" to n1, "n2" to n2, output = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n2")))
        assertEquals("n2", (graph.output as GPUPreparedFilterInputRef.Node).id.value)
        assertTrue(graph.identity.isNotBlank())
    }

    @Test
    fun `graph nodes list is an immutable copy`() {
        val graph = graphOf(
            "n1" to blurNode("n1", 2f, 2f),
            "n2" to offsetNode("n2", 5f, 0f),
        )
        val nodes = graph.nodes
        assertFailsWith<UnsupportedOperationException> { (nodes as? MutableList<*>)?.clear() }
        assertEquals(2, graph.nodes.size)
    }

    private fun matrixConvolutionNode(id: String, kx: Int, ky: Int, kernel: FloatArray): GPUPreparedFilterNode {
        return GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId(id),
            kind = GPUPreparedFilterKind.MatrixConvolution,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = MatrixConvolutionParams(
                kernel = kernel, kernelSizeX = kx, kernelSizeY = ky,
                gain = 1f, bias = 0f,
                kernelOffsetX = 0, kernelOffsetY = 0,
                convolveAlpha = true, tileMode = "clamp",
            ),
            provenance = "test/$id",
        )
    }

    private fun blurNode(
        id: String, sigmaX: Float, sigmaY: Float,
        inputRef: GPUPreparedFilterInputRef = GPUPreparedFilterInputRef.ImplicitSource,
    ): GPUPreparedFilterNode {
        return GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId(id),
            kind = GPUPreparedFilterKind.Blur,
            inputs = listOf(inputRef),
            parameters = BlurParams(sigmaX, sigmaY),
            provenance = "test/$id",
        )
    }

    private fun offsetNode(id: String, dx: Float, dy: Float): GPUPreparedFilterNode {
        return GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId(id),
            kind = GPUPreparedFilterKind.Offset,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = OffsetParams(dx, dy),
            provenance = "test/$id",
        )
    }

    private fun cropNode(id: String): GPUPreparedFilterNode {
        return GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId(id),
            kind = GPUPreparedFilterKind.Crop,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = CropParams(0f, 0f, 100f, 100f),
            provenance = "test/$id",
        )
    }

    private fun graphOf(
        vararg entries: Pair<String, GPUPreparedFilterNode>,
        output: GPUPreparedFilterInputRef? = null,
    ): GPUPreparedFilterGraph {
        val list = entries.map { it.second }
        val out = output ?: GPUPreparedFilterInputRef.Node(list.last().id)
        val identity = GPUPreparedFilterGraph.computeIdentity(list, out)
        return GPUPreparedFilterGraph(list, out, identity)
    }
}
