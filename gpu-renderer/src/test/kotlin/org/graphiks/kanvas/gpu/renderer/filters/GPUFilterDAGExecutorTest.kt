package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUFilterDAGExecutorTest {

    @Test
    fun `executes single node graph`() {
        val dag = GPUFilterDAG(
            graphId = "single-node",
            nodes = listOf(
                GPUFilterDAGNode(GPUFilterNodeID("n1"), "Blur", emptyList()),
            ),
            edges = emptyList(),
        )
        val result = GPUFilterDAGExecutor().execute(dag)
        assertTrue(result.accepted)
        assertEquals(1, result.passCount)
        assertEquals(0, result.intermediateCount)
    }

    @Test
    fun `executes two node chain`() {
        val dag = GPUFilterDAG(
            graphId = "two-node-chain",
            nodes = listOf(
                GPUFilterDAGNode(GPUFilterNodeID("n1"), "Blur", emptyList()),
                GPUFilterDAGNode(GPUFilterNodeID("n2"), "ColorMatrix", listOf("n1")),
            ),
            edges = listOf(
                GPUFilterDAGEdge(GPUFilterNodeID("n1"), GPUFilterNodeID("n2")),
            ),
        )
        val result = GPUFilterDAGExecutor().execute(dag)
        assertTrue(result.accepted)
        assertEquals(2, result.passCount)
        assertEquals(1, result.intermediateCount)
    }

    @Test
    fun `rejects empty graph`() {
        val dag = GPUFilterDAG(
            graphId = "empty",
            nodes = emptyList(),
            edges = emptyList(),
        )
        val result = GPUFilterDAGExecutor().execute(dag)
        assertFalse(result.accepted)
        assertEquals(0, result.passCount)
        assertTrue(result.diagnostics.contains("unsupported.filter.dag_empty"))
    }

    @Test
    fun `rejects graph with more than two nodes`() {
        val dag = GPUFilterDAG(
            graphId = "three-node",
            nodes = listOf(
                GPUFilterDAGNode(GPUFilterNodeID("n1"), "Blur", emptyList()),
                GPUFilterDAGNode(GPUFilterNodeID("n2"), "ColorMatrix", listOf("n1")),
                GPUFilterDAGNode(GPUFilterNodeID("n3"), "Blur", listOf("n2")),
            ),
            edges = listOf(
                GPUFilterDAGEdge(GPUFilterNodeID("n1"), GPUFilterNodeID("n2")),
                GPUFilterDAGEdge(GPUFilterNodeID("n2"), GPUFilterNodeID("n3")),
            ),
        )
        val result = GPUFilterDAGExecutor().execute(dag)
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.contains("unsupported.filter.dag_too_many_nodes"))
    }

    @Test
    fun `node count is reported correctly`() {
        val dag = GPUFilterDAG(
            graphId = "test",
            nodes = listOf(
                GPUFilterDAGNode(GPUFilterNodeID("n1"), "Blur", emptyList()),
                GPUFilterDAGNode(GPUFilterNodeID("n2"), "ColorMatrix", listOf("n1")),
            ),
            edges = listOf(
                GPUFilterDAGEdge(GPUFilterNodeID("n1"), GPUFilterNodeID("n2")),
            ),
        )
        assertEquals(2, dag.nodeCount)
        assertEquals(1, dag.edgeCount)
    }
}
