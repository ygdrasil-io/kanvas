package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedFilterDagValidationTest {

    @Test
    fun `self-cycle via node referencing itself is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterGraph(
                listOf(GPUPreparedFilterNode(
                    GPUPreparedFilterNodeId("n0"), GPUPreparedFilterKind.Offset,
                    listOf(GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0"))),
                    OffsetParams(1f, 0f), "test")),
                GPUPreparedFilterInputRef.Node(GPUPreparedFilterNodeId("n0")),
                "bad",
            )
        }
    }

    @Test
    fun `two-node cycle is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterGraph(
                listOf(
                    GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Offset,
                        listOf(GPUPreparedFilterInputRef.Node(gid("n1"))), OffsetParams(1f, 0f), "a"),
                    GPUPreparedFilterNode(gid("n1"), GPUPreparedFilterKind.Offset,
                        listOf(GPUPreparedFilterInputRef.Node(gid("n0"))), OffsetParams(2f, 0f), "b"),
                ),
                GPUPreparedFilterInputRef.Node(gid("n0")), "bad",
            )
        }
    }

    @Test
    fun `wrong kind-params association is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Offset,
                listOf(GPUPreparedFilterInputRef.ImplicitSource),
                BlurParams(2f, 2f), "test")
        }
    }

    @Test
    fun `too many inputs for single-input node is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Blur,
                listOf(GPUPreparedFilterInputRef.ImplicitSource, GPUPreparedFilterInputRef.ImplicitSource),
                BlurParams(2f, 2f), "test")
        }
    }

    @Test
    fun `too few inputs for blend node is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Blend,
                listOf(GPUPreparedFilterInputRef.ImplicitSource),
                BlendParams("srcOver"), "test")
        }
    }

    @Test
    fun `graph with injected bad identity uses computed identity`() {
        val n = GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1f, 0f), "a")
        val computed = GPUPreparedFilterGraph.computeIdentity(listOf(n), GPUPreparedFilterInputRef.Node(gid("n0")))
        assertFailsWith<IllegalStateException> {
            GPUPreparedFilterGraph(listOf(n), GPUPreparedFilterInputRef.Node(gid("n0")), "wrong_identity")
        }
    }

    @Test
    fun `same DAG from different collection instances produces same identity`() {
        val n1 = GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1f, 0f), "a")
        val g1 = GPUPreparedFilterGraph(listOf(n1), GPUPreparedFilterInputRef.Node(gid("n0")), "")
        val n2 = GPUPreparedFilterNode(gid("n0"), GPUPreparedFilterKind.Offset,
            listOf(GPUPreparedFilterInputRef.ImplicitSource), OffsetParams(1f, 0f), "a")
        val g2 = GPUPreparedFilterGraph(mutableListOf(n2), GPUPreparedFilterInputRef.Node(gid("n0")), "")
        assertEquals(g1.identity, g2.identity)
    }

    private fun gid(s: String) = GPUPreparedFilterNodeId(s)
}
