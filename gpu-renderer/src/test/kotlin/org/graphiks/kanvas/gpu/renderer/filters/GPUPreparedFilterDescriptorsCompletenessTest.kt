package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedFilterDescriptorsCompletenessTest {

    @Test
    fun `BlurParams preserves tileMode`() {
        val a = BlurParams(3f, 5f, tileMode = "clamp")
        val b = BlurParams(3f, 5f, tileMode = "repeat")
        assertEquals(GPUTileMode.Clamp, a.tileMode)
        assertNotEquals(a, b)
        assertNotEquals(a.canonicalIdentity(), b.canonicalIdentity())
    }

    @Test
    fun `CropParams preserves tileMode`() {
        val a = CropParams(0f, 0f, 100f, 100f, tileMode = "decal")
        val b = CropParams(0f, 0f, 100f, 100f, tileMode = "clamp")
        assertEquals(GPUTileMode.Decal, a.tileMode)
        assertNotEquals(a, b)
    }

    @Test
    fun `TileParams has exact src and dst rects`() {
        val p = TileParams(10f, 20f, 60f, 40f, 5f, 15f, 55f, 35f)
        assertEquals(10f, p.srcLeft)
        assertEquals(20f, p.srcTop)
        assertEquals(5f, p.dstX)
        assertEquals(15f, p.dstY)
    }

    @Test
    fun `PictureParams has picture identity and optional src rect`() {
        val withSrc = PictureParams("pic1", srcX = 0f, srcY = 0f, srcW = 100f, srcH = 100f)
        assertEquals("pic1", withSrc.pictureIdentity)
        assertEquals(0f, withSrc.srcX)
        val withoutSrc = PictureParams("pic2")
        assertEquals("pic2", withoutSrc.pictureIdentity)
        assertEquals(null, withoutSrc.sourceRect)
    }

    @Test
    fun `MatrixConvolutionParams preserves kernel size`() {
        val k = floatArrayOf(0f, 1f, 0f, 1f, -4f, 1f, 0f, 1f, 0f)
        val p = MatrixConvolutionParams(
            kernel = k, kernelSizeX = 3, kernelSizeY = 3,
            gain = 1f, bias = 0f, kernelOffsetX = 0, kernelOffsetY = 0,
            convolveAlpha = false, tileMode = "clamp",
        )
        assertEquals(3, p.kernelSizeX)
        assertEquals(3, p.kernelSizeY)
        assertTrue(p.kernelHash.isNotBlank())
    }

    @Test
    fun `MatrixConvolutionParams rejects mismatched kernel dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            MatrixConvolutionParams(
                kernel = floatArrayOf(0f, 1f, 0f, 1f),
                kernelSizeX = 3, kernelSizeY = 3,
                gain = 1f, bias = 0f, kernelOffsetX = 0, kernelOffsetY = 0,
                convolveAlpha = true, tileMode = "clamp",
            )
        }
    }

    @Test
    fun `two kernels with same content but different dimensions produce different identity`() {
        val a = MatrixConvolutionParams(
            kernel = floatArrayOf(1f, 2f, 3f, 4f),
            kernelSizeX = 2, kernelSizeY = 2,
            gain = 1f, bias = 0f, kernelOffsetX = 0, kernelOffsetY = 0,
            convolveAlpha = false, tileMode = "clamp",
        )
        val b = MatrixConvolutionParams(
            kernel = floatArrayOf(1f, 2f, 3f, 4f, 0f, 0f, 0f, 0f, 0f),
            kernelSizeX = 3, kernelSizeY = 3,
            gain = 1f, bias = 0f, kernelOffsetX = 0, kernelOffsetY = 0,
            convolveAlpha = false, tileMode = "clamp",
        )
        assertNotEquals(a.canonicalIdentity(), b.canonicalIdentity())
        assertNotEquals(a, b)
    }

    @Test
    fun `RuntimeEffectParams preserves childShaderName`() {
        val p = RuntimeEffectParams(
            effectId = "simple_rt", effectVersion = 1,
            uniforms = emptyMap(), children = emptyMap(),
            childShaderName = "src",
        )
        assertEquals("src", p.childShaderName)
    }

    @Test
    fun `RuntimeEffectParams child order is deterministic in identity`() {
        val a = RuntimeEffectParams(
            effectId = "effect", effectVersion = 1,
            uniforms = mapOf("u" to floatArrayOf(1f)),
            children = mapOf("a" to GPUPreparedFilterInputRef.ImplicitSource,
                             "b" to GPUPreparedFilterInputRef.TransparentBlack),
        )
        val b = RuntimeEffectParams(
            effectId = "effect", effectVersion = 1,
            uniforms = mapOf("u" to floatArrayOf(1f)),
            children = mapOf("b" to GPUPreparedFilterInputRef.TransparentBlack,
                             "a" to GPUPreparedFilterInputRef.ImplicitSource),
        )
        assertEquals(a.canonicalIdentity(), b.canonicalIdentity())
    }

    @Test
    fun `Non-finite float parameters are refused`() {
        assertFailsWith<IllegalArgumentException> {
            BlurParams(Float.NaN, 2f)
        }
        assertFailsWith<IllegalArgumentException> {
            BlurParams(2f, Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `Negative sigma in BlurParams is refused`() {
        assertFailsWith<IllegalArgumentException> {
            BlurParams(-1f, 2f)
        }
    }

    @Test
    fun `MutableUniforms cannot mutate RuntimeEffectParams after construction`() {
        val uniforms = mutableMapOf("u" to floatArrayOf(1f))
        val p = RuntimeEffectParams("effect", 1, uniforms, emptyMap())
        uniforms["u"]!![0] = 99f
        assertEquals(1f, p.uniforms["u"]!![0])
        uniforms["new"] = floatArrayOf(2f)
        assertEquals(1, p.uniforms.size)
    }

    @Test
    fun `Graph identity must be computed from nodes not freely assigned`() {
        val n1 = GPUPreparedFilterNode(
            GPUPreparedFilterNodeId("n1"), GPUPreparedFilterKind.Blur,
            listOf(GPUPreparedFilterInputRef.ImplicitSource),
            BlurParams(2f, 2f), "test",
        )
        val expected = GPUPreparedFilterGraph.computeIdentity(
            listOf(n1), GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1")))
        val graph = GPUPreparedFilterGraph(
            nodes = listOf(n1),
            output = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1")),
        )
        assertEquals(expected, graph.identity)
    }

    @Test
    fun `Graph with duplicate node IDs is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterGraph(
                nodes = listOf(
                    GPUPreparedFilterNode(GPUPreparedFilterNodeId("n1"), GPUPreparedFilterKind.Offset,
                        listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1f, 0f), "a"),
                    GPUPreparedFilterNode(GPUPreparedFilterNodeId("n1"), GPUPreparedFilterKind.Offset,
                        listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(2f, 0f), "b"),
                ),
                output = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n1")),
            )
        }
    }

    @Test
    fun `Graph with missing output reference is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterGraph(
                nodes = listOf(
                    GPUPreparedFilterNode(GPUPreparedFilterNodeId("n1"), GPUPreparedFilterKind.Offset,
                        listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1f, 0f), "a"),
                ),
                output = GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n_missing")),
            )
        }
    }

    @Test
    fun `Reverse map access returns immutable collections`() {
        val p = RuntimeEffectParams("fx", 1,
            mapOf("u" to floatArrayOf(1f)),
            mapOf("c" to GPUPreparedFilterInputRef.ImplicitSource))
        val m = p.uniforms
        assertFailsWith<UnsupportedOperationException> { (m as? MutableMap<*, *>)?.clear() }
    }
}
