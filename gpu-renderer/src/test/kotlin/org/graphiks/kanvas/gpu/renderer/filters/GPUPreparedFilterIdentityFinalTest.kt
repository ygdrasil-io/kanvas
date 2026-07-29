package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedFilterIdentityFinalTest {

    @Test
    fun `DAG constructor rejects identity parameter silently ignored`() {
        val n = GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1f, 0f), "a")
        assertFailsWith<IllegalStateException> {
            GPUPreparedFilterGraph(listOf(n), GPUPreparedFilterInputRef.Node(gid("n0")), "wrong_identity")
        }
    }

    @Test
    fun `two neighbor floats produce distinct identities`() {
        val a = GPUPreparedFilterNode(gid("a"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1.000001f, 0f), "t")
        val b = GPUPreparedFilterNode(gid("b"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1.000002f, 0f), "t")
        assertNotEquals(a.canonicalIdentity(), b.canonicalIdentity())
    }

    @Test
    fun `0dot0 and minus0dot0 produce distinct identities`() {
        val a = GPUPreparedFilterNode(gid("a"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(0f, 0f), "t")
        val b = GPUPreparedFilterNode(gid("b"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(-0f, 0f), "t")
        assertNotEquals(a.canonicalIdentity(), b.canonicalIdentity())
    }

    @Test
    fun `FloatArray is not exposed through ColorFilterParams getter`() {
        val m = floatArrayOf(0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0.21f, 0.72f, 0.07f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)
        val p = ColorFilterParams(m)
        val exposed = p.matrix
        exposed[0] = 99f
        assertNotEquals(99f, p.matrix[0])
    }

    @Test
    fun `Convolution kernel is not exposed through getter`() {
        val k = floatArrayOf(0f, 1f, 0f, 1f, -4f, 1f, 0f, 1f, 0f)
        val p = MatrixConvolutionParams(k, 3, 3, 1f, 0f, 0, 0, false, "clamp")
        val exposed = p.kernel
        exposed[4] = 99f
        assertEquals(-4f, p.kernel[4])
    }

    @Test
    fun `reverse map in RuntimeEffectParams uniforms is not mutable`() {
        val p = RuntimeEffectParams("fx", 1, mapOf("u" to floatArrayOf(1f)), emptyMap())
        val m = p.uniforms
        val e = try {
            (m as java.util.Map<*, *>).clear()
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(e, "uniforms map must throw on mutation")
    }

    private fun gid(s: String) = GPUPreparedFilterNodeId(s)
}
