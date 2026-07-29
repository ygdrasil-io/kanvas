package org.graphiks.kanvas.gpu.renderer.layers

import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterRefusalCodes
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNodeId
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterGraph
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterInputRef
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterRewriteProof
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNormalization
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
    fun `capture contains root and child scopes`() {
        val root = scopeNode("root", null, GPUPreparedCompositeScopeKind.Root)
        val child = scopeNode("child", root.id, GPUPreparedCompositeScopeKind.SaveLayer)
        val capture = GPUPreparedCompositeCapture(
            rootScopeId = root.id,
            scopes = mapOf(root.id to root, child.id to child),
            expandedOperations = emptyList(),
            identity = "capture_v1",
        )
        assertEquals(2, capture.scopes.size)
        assertEquals(root.id, capture.rootScopeId)
        assertNotNull(capture.scopes[child.id])
    }

    @Test
    fun `capture ready result wraps immutable capture`() {
        val capture = minimalCapture()
        val result = GPUPreparedCompositeCaptureResult.Ready(capture)
        assertEquals(capture.identity, (result as GPUPreparedCompositeCaptureResult.Ready).capture.identity)
    }

    @Test
    fun `capture refused result carries code and facts`() {
        val result = GPUPreparedCompositeCaptureResult.Refused(
            code = "unsupported.composite.layer.unbalanced",
            operationIndex = 3,
            facts = mapOf("reason" to "unmatched EndLayer"),
        )
        assertEquals("unsupported.composite.layer.unbalanced", result.code)
        assertEquals(3, result.operationIndex)
        assertEquals("unmatched EndLayer", result.facts["reason"])
    }

    @Test
    fun `captured operation carries source index and identity`() {
        val op = GPUPreparedCapturedOperation(
            sourceOperationIndex = 5,
            snapshot = DummyDisplayOp("drawRect_0"),
            identity = "op_5_hash",
        )
        assertEquals(5, op.sourceOperationIndex)
        assertEquals("op_5_hash", op.identity)
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
            kind = "Table",
            coverageFormat = "A8",
            executionIdentity = "table_v1",
            tableEntries = table,
        )
        assertEquals("Table", plan.kind)
        assertEquals("A8", plan.coverageFormat)
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
        val blurPlan = GPUPreparedMaskFilterPlan("Blur", "A8", "blur_exec")
        val ready = GPUPreparedMaskFilterLowering.Ready(blurPlan)
        assertEquals("Blur", (ready as GPUPreparedMaskFilterLowering.Ready).plan.kind)
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
    fun `filter rewrite proof carries rule and node ids`() {
        val proof = GPUPreparedFilterRewriteProof(
            rule = "compose-offset",
            sourceNodeIds = listOf(GPUPreparedFilterNodeId("n1"), GPUPreparedFilterNodeId("n2")),
            resultNodeIds = listOf(GPUPreparedFilterNodeId("n12")),
            removedIntermediateCount = 1,
            inputBoundsIdentity = "bounds_in",
            outputBoundsIdentity = "bounds_out",
        )
        assertEquals("compose-offset", proof.rule)
        assertEquals(2, proof.sourceNodeIds.size)
        assertEquals(1, proof.removedIntermediateCount)
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

    @Test
    fun `refusal code sets from filter and composite have expected codes`() {
        val filterCodes = GPUPreparedFilterRefusalCodes.ALL
        assertTrue(filterCodes.contains(GPUPreparedFilterRefusalCodes.GRAPH_CYCLE))
        assertTrue(filterCodes.contains(GPUPreparedFilterRefusalCodes.GRAPH_BUDGET))
        assertTrue(filterCodes.contains(GPUPreparedFilterRefusalCodes.PARAMETER_NON_FINITE))
        assertTrue(filterCodes.contains(GPUPreparedFilterRefusalCodes.BOUNDS_OVERFLOW))
        assertTrue(filterCodes.contains(GPUPreparedFilterRefusalCodes.INTERMEDIATE_BUDGET))
        val compositeCodes = GPUPreparedCompositeRefusalCodes.ALL
        assertTrue(compositeCodes.contains(GPUPreparedCompositeRefusalCodes.LAYER_UNBALANCED))
        assertTrue(compositeCodes.contains(GPUPreparedCompositeRefusalCodes.PICTURE_CYCLE))
        assertTrue(compositeCodes.contains(GPUPreparedCompositeRefusalCodes.PICTURE_BUDGET))
    }

    @Test
    fun `id values never depend on object address`() {
        val id1 = GPUPreparedFilterNodeId("n1")
        val id2 = GPUPreparedFilterNodeId("n1")
        assertEquals(id1, id2)
    }

    @Test
    fun `scope id values never depend on object address`() {
        val id1 = GPUPreparedCompositeScopeId("s1")
        val id2 = GPUPreparedCompositeScopeId("s1")
        assertEquals(id1, id2)
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

    private fun minimalCapture(): GPUPreparedCompositeCapture {
        val root = scopeNode("root", null, GPUPreparedCompositeScopeKind.Root)
        return GPUPreparedCompositeCapture(
            rootScopeId = root.id,
            scopes = mapOf(root.id to root),
            expandedOperations = emptyList(),
            identity = "capture_v1",
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
            identity = "graph_empty",
        )
    }

    private data class DummyDisplayOp(val label: String)
}
