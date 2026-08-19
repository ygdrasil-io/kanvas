package org.graphiks.kanvas.gpu.renderer.layers

import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterRefusalCodes
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNodeId
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterGraph
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterInputRef
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterRewriteProof
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNormalization
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedCoverageFormat
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedMaskFilterKind
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedMaskFilterLowering
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedMaskFilterPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GPUPreparedCompositeContractsTest {

    @Test
    fun `prepared refusal authorities contain no duplicate code`() {
        val all = GPUPreparedFilterRefusalCodes.ALL +
            GPUPreparedCompositeRefusalCodes.ALL
        assertEquals(all.size, all.toSet().size, "duplicate refusal codes found")
    }

    @Test
    fun `scope id is an inline value class`() {
        val id = GPUPreparedCompositeScopeId("scope_001")
        assertEquals("scope_001", id.value)
    }

    @Test
    fun `scope id rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedCompositeScopeId("")
        }
    }

    @Test
    fun `all four scope kinds are defined`() {
        val kinds = GPUPreparedCompositeScopeKind.entries
        assertEquals(4, kinds.size)
        assertTrue(kinds.contains(GPUPreparedCompositeScopeKind.Root))
        assertTrue(kinds.contains(GPUPreparedCompositeScopeKind.SaveLayer))
        assertTrue(kinds.contains(GPUPreparedCompositeScopeKind.PaintedPicture))
        assertTrue(kinds.contains(GPUPreparedCompositeScopeKind.FilterPictureSource))
    }

    @Test
    fun `scope has deterministic parent child hierarchy`() {
        val root = scopeNode("root", null, GPUPreparedCompositeScopeKind.Root)
        val child = scopeNode("child", root.id, GPUPreparedCompositeScopeKind.SaveLayer)
        assertNull(root.parentId)
        assertEquals(root.id, child.parentId)
    }

    @Test
    fun `scope entries preserve draw and child scope order`() {
        val entries = listOf(
            GPUPreparedCompositeEntry.Draw(0),
            GPUPreparedCompositeEntry.Scope(GPUPreparedCompositeScopeId("s1")),
            GPUPreparedCompositeEntry.Draw(1),
            GPUPreparedCompositeEntry.Scope(GPUPreparedCompositeScopeId("s2")),
        )
        assertEquals(4, entries.size)
        assertTrue(entries[0] is GPUPreparedCompositeEntry.Draw)
        assertTrue(entries[1] is GPUPreparedCompositeEntry.Scope)
    }

    @Test
    fun `scope and mask plan snapshot mutable lists`() {
        val entries = mutableListOf<GPUPreparedCompositeEntry>(
            GPUPreparedCompositeEntry.Draw(0),
        )
        val scope = GPUPreparedCompositeScope(
            id = GPUPreparedCompositeScopeId("scope"),
            parentId = null,
            saveOperationIndex = null,
            restoreOperationIndex = null,
            entries = entries,
            sourceKind = GPUPreparedCompositeScopeKind.Root,
            provenance = "test",
        )
        val table = mutableListOf(1, 2, 3)
        val mask = GPUPreparedMaskFilterPlan(
            kind = GPUPreparedMaskFilterKind.Table,
            coverageFormat = GPUPreparedCoverageFormat.A8,
            executionIdentity = "table",
            tableEntries = table,
        )

        entries += GPUPreparedCompositeEntry.Draw(1)
        table += 4

        assertEquals(1, scope.entries.size)
        assertEquals(listOf(1, 2, 3), mask.tableEntries)
    }

    @Test
    fun `composite plan carries capture identity and layer plans`() {
        val plan = GPUPreparedCompositePlan(
            captureIdentity = "cap_v1",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = emptyList(),
            normalizedFilters = emptyMap(),
            identity = "plan_v1",
        )
        assertEquals("cap_v1", plan.captureIdentity)
        assertTrue(plan.identity.isNotBlank())
    }

    @Test
    fun `composite lowering ready wraps accepted plan`() {
        val plan = minimalPlan()
        val result = GPUPreparedCompositeLowering.Ready(plan)
        assertEquals(plan.identity, (result as GPUPreparedCompositeLowering.Ready).plan.identity)
    }

    @Test
    fun `composite lowering refused carries code and facts`() {
        val result = GPUPreparedCompositeLowering.Refused(
            code = "unsupported.composite.layer.budget",
            operationIndex = 7,
            facts = mapOf("estimatedBytes" to "256000000", "limitBytes" to "128000000"),
        )
        assertEquals("unsupported.composite.layer.budget", result.code)
        assertEquals(7, result.operationIndex)
    }

    @Test
    fun `mask filter plan carries kind coverage format and table entries`() {
        val table = (0 until 256).toList()
        val plan = GPUPreparedMaskFilterPlan(
            kind = GPUPreparedMaskFilterKind.Table,
            coverageFormat = GPUPreparedCoverageFormat.A8,
            executionIdentity = "table_v1",
            tableEntries = table,
        )
        assertEquals(GPUPreparedMaskFilterKind.Table, plan.kind)
        assertEquals(GPUPreparedCoverageFormat.A8, plan.coverageFormat)
        assertEquals(256, plan.tableEntries.size)
        assertEquals(0, plan.tableEntries[0])
        assertEquals(255, plan.tableEntries[255])
    }

    @Test
    fun `image filter lowering encodes typed graph and refusal states`() {
        val graph = minimalGraph()
        val ready = GPUPreparedImageFilterLowering.Ready(graph)
        assertEquals(graph.identity, (ready as GPUPreparedImageFilterLowering.Ready).graph.identity)
        val refused = GPUPreparedImageFilterLowering.Refused(
            code = "unsupported.filter.parameter.non_finite",
            facts = mapOf("param" to "sigmaX"),
        )
        assertEquals("unsupported.filter.parameter.non_finite", refused.code)
    }

    @Test
    fun `mask filter lowering encodes three public kinds`() {
        val blurPlan = GPUPreparedMaskFilterPlan(
            GPUPreparedMaskFilterKind.Blur,
            GPUPreparedCoverageFormat.A8,
            "blur_exec",
        )
        val ready = GPUPreparedMaskFilterLowering.Ready(blurPlan)
        assertEquals(
            GPUPreparedMaskFilterKind.Blur,
            (ready as GPUPreparedMaskFilterLowering.Ready).plan.kind,
        )
        val refused = GPUPreparedMaskFilterLowering.Refused(
            code = "unsupported.mask-filter.table.size",
            facts = mapOf("size" to "512"),
        )
        assertEquals("unsupported.mask-filter.table.size", refused.code)
    }

    @Test
    fun `filter node id value class equals by content`() {
        val a = GPUPreparedFilterNodeId("a")
        val b = GPUPreparedFilterNodeId("a")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `scope id values never depend on object address`() {
        val id1 = GPUPreparedCompositeScopeId("s1")
        val id2 = GPUPreparedCompositeScopeId("s1")
        assertEquals(id1, id2)
    }

    @Test
    fun `refusal code sets from filter and composite have expected codes`() {
        val filterCodes = GPUPreparedFilterRefusalCodes.ALL
        assertTrue(filterCodes.contains(GPUPreparedFilterRefusalCodes.GRAPH_CYCLE))
        val compositeCodes = GPUPreparedCompositeRefusalCodes.ALL
        assertTrue(compositeCodes.contains(GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED))
    }

    @Test
    fun `filter normalization wraps graph and rewrites`() {
        val graph = minimalGraph()
        val rewrites = listOf<GPUPreparedFilterRewriteProof>()
        val normalization = GPUPreparedFilterNormalization(
            graph = graph,
            rewrites = rewrites,
            materializationNodeIds = emptySet(),
        )
        assertEquals(graph.identity, normalization.graph.identity)
        assertEquals(0, normalization.rewrites.size)
    }

    @Test
    fun `filter normalization marks materialization boundaries`() {
        val graph = minimalGraph()
        val nodeIds = setOf(GPUPreparedFilterNodeId("n1"))
        val normalization = GPUPreparedFilterNormalization(
            graph = graph,
            rewrites = emptyList(),
            materializationNodeIds = nodeIds,
        )
        assertEquals(1, normalization.materializationNodeIds.size)
        assertTrue(normalization.materializationNodeIds.contains(GPUPreparedFilterNodeId("n1")))
    }

    private fun scopeNode(
        id: String,
        parentId: GPUPreparedCompositeScopeId?,
        kind: GPUPreparedCompositeScopeKind,
    ): GPUPreparedCompositeScope {
        return GPUPreparedCompositeScope(
            id = GPUPreparedCompositeScopeId(id),
            parentId = parentId,
            saveOperationIndex = 0,
            restoreOperationIndex = null,
            entries = emptyList(),
            sourceKind = kind,
            provenance = "test/$id",
        )
    }

    private fun minimalPlan(): GPUPreparedCompositePlan {
        return GPUPreparedCompositePlan(
            captureIdentity = "cap_v1",
            rootScopeId = GPUPreparedCompositeScopeId("root"),
            layers = emptyList(),
            normalizedFilters = emptyMap(),
            identity = "plan_v1",
        )
    }

    private fun minimalGraph(): GPUPreparedFilterGraph {
        return GPUPreparedFilterGraph(
            nodes = emptyList(),
            output = GPUPreparedFilterInputRef.TransparentBlack,
        )
    }
}
