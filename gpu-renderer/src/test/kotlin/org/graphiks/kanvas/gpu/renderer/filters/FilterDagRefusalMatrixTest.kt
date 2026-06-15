package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterDagRefusalMatrixTest {
    @Test
    fun `unsupported dag variants map to stable diagnostics`() {
        val report = GPUFilterDagRefusalMatrix.evaluate(
            GPUFilterDagRefusalInput(
                graph = filterGraph(
                    node("n1", "UnknownPlatformFilter"),
                    node("n2", "Picture"),
                    node("n3", "RuntimeShader"),
                ),
                registeredRuntimeEffectIds = emptySet(),
            ),
        )

        assertEquals(
            listOf(
                "unsupported.filter.node_unimplemented",
                "unsupported.filter.picture_unbounded",
                "unsupported.filter.runtime_effect_unregistered",
            ),
            report.diagnostics.map { it.code },
        )
        assertTrue(report.rows.all { it.status == GPUFilterDagMatrixStatus.Refused })
        assertFalse(report.promotable)
    }

    @Test
    fun `pm matrix separates supportable bounded rows from refused variants`() {
        val report = GPUFilterDagRefusalMatrix.evaluate(
            GPUFilterDagRefusalInput(
                graph = filterGraph(
                    node("n1", "ColorFilter"),
                    node("n2", "Picture"),
                ),
                supportableBoundedNodeKinds = setOf("ColorFilter"),
            ),
        )

        assertEquals(GPUFilterDagMatrixStatus.SupportableBounded, report.rows[0].status)
        assertEquals(null, report.rows[0].diagnosticCode)
        assertEquals(GPUFilterDagMatrixStatus.Refused, report.rows[1].status)
        assertEquals("unsupported.filter.picture_unbounded", report.rows[1].diagnosticCode)
        assertFalse(report.promotable)
    }

    @Test
    fun `missing bounds or intermediate ownership blocks promotion`() {
        val unbounded = GPUFilterDagRefusalMatrix.evaluate(
            GPUFilterDagRefusalInput(
                graph = filterGraph(node("n1", "ColorFilter")),
                finiteBounds = false,
                supportableBoundedNodeKinds = setOf("ColorFilter"),
            ),
        )
        val missingIntermediate = GPUFilterDagRefusalMatrix.evaluate(
            GPUFilterDagRefusalInput(
                graph = filterGraph(node("n1", "ColorFilter")),
                intermediateOwnershipValidated = false,
                supportableBoundedNodeKinds = setOf("ColorFilter"),
            ),
        )

        assertEquals(listOf("unsupported.filter.bounds_unbounded"), unbounded.diagnostics.map { it.code })
        assertFalse(unbounded.promotable)
        assertEquals(
            listOf("unsupported.filter.intermediate_unvalidated"),
            missingIntermediate.diagnostics.map { it.code },
        )
        assertFalse(missingIntermediate.promotable)
    }

    @Test
    fun `supportable bounded rows remain non promotable in refusal matrix`() {
        val report = GPUFilterDagRefusalMatrix.evaluate(
            GPUFilterDagRefusalInput(
                graph = filterGraph(node("n1", "ColorFilter")),
                supportableBoundedNodeKinds = setOf("ColorFilter"),
            ),
        )

        assertEquals(GPUFilterDagMatrixStatus.SupportableBounded, report.rows.single().status)
        assertTrue(report.diagnostics.isEmpty())
        assertFalse(report.promotable)
    }

    private fun filterGraph(vararg nodes: GPUFilterNodeDescriptor): GPUFilterGraphDescriptor =
        GPUFilterGraphDescriptor(
            graphId = "filter-dag",
            version = 1,
            sourceRole = "layer-source",
            nodes = nodes.toList(),
            edges = nodes
                .toList()
                .windowed(size = 2)
                .map { pair -> "${pair[0].nodeId.value}->${pair[1].nodeId.value}" },
            coordinateSpaces = listOf("layer", "target"),
            provenance = "test-fixture",
        )

    private fun node(id: String, kind: String): GPUFilterNodeDescriptor =
        GPUFilterNodeDescriptor(
            nodeId = GPUFilterNodeID(id),
            nodeKind = kind,
            inputLabels = emptyList(),
            parameterHash = "$kind:params",
        )
}
