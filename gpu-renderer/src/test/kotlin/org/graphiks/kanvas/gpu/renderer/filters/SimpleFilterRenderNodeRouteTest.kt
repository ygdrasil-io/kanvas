package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SimpleFilterRenderNodeRouteTest {
    @Test
    fun boundedColorFilterNodeProducesSimpleRenderRouteEvidenceDump() {
        val result = GPUSimpleFilterRenderNodePlanner().plan(simpleFilterRequest())

        assertEquals("gpu-renderer.filter.simple-node", result.evidenceRow)
        assertEquals("GPUNative", result.routeKind)
        assertEquals("TargetNative", result.classification)
        assertFalse(result.promoted)
        assertFalse(result.productActivation)
        assertFalse(result.materialized)
        assertAcceptedDiagnostic(result)

        val nodePlan = assertIs<GPUFilterNodePlan.Accepted>(result.filterPlan.nodePlans.single())
        val route = assertIs<GPUFilterNodeRoute.NativeRender>(nodePlan.route)
        assertEquals("filter-render:colorfilter", route.renderNode.renderStepLabel)
        assertEquals("render_attachment,texture_binding", result.intermediate.usageLabel)

        assertEquals(
            listOf(
                "filter:simple-node row=gpu-renderer.filter.simple-node routeKind=GPUNative classification=TargetNative promoted=false productActivation=false materialized=false graph=filter-card node=cf-1 kind=ColorFilter route=GPUNativeRender",
                "filter:graph id=filter-card version=1 source=layer-source nodes=cf-1 edges=none coordinates=layer,target provenance=test-fixture descriptor=${result.graphDescriptorHash}",
                "filter:bounds node=cf-1 input=0,0,64,48 output=0,0,64,48 finite=true conservative=true crop=0,0,64,48 tile=decal/decal sampling=nearest/none coord=layer",
                "filter:intermediate label=filter-intermediate:cf-1 descriptor=${result.intermediateDescriptorHash} owner=GPURecorderScope generation=17 bounds=0,0,64,48 format=rgba8unorm usage=render_attachment,texture_binding lifetime=layer-local bytes=12288",
                "filter:render-node step=filter-render:colorfilter pipeline=${result.renderPipelineKeyHash} payload=${result.payloadPlanHash} binding=${result.bindingPlanHash}",
                "filter:resource source=layer-source generation=17 readWriteAliasing=false activeAttachmentSampled=false budget=filter-small intermediateBytes=12288",
                "filter:diagnostic code=accepted.filter.simple_node terminal=false",
                "filter:nonclaim nativeFilter=false adapterBacked=false arbitraryFilterDag=false runtimeEffectFilter=false cpuRenderedFilterTextureFallback=false productActivation=false",
            ),
            result.dumpLines(),
        )
    }

    @Test
    fun unsupportedSimpleFilterVariantsRefuseWithStableDiagnostics() {
        val cases = listOf(
            refusalCase(
                "unbounded",
                bounds = bounds(finite = false),
                reason = "unsupported.filter.bounds_unbounded",
            ),
            refusalCase(
                "invalid-bounds",
                bounds = bounds(width = 0),
                reason = "unsupported.filter.bounds_invalid",
            ),
            refusalCase(
                "multi-node",
                graph = filterGraph(
                    node("cf-1", "ColorFilter"),
                    node("blur-1", "Blur"),
                ),
                reason = "unsupported.filter.graph_node_limit",
            ),
            refusalCase(
                "unsupported-node",
                graph = filterGraph(node("blur-1", "Blur")),
                reason = "unsupported.filter.node_unimplemented",
            ),
            refusalCase(
                "missing-usage",
                intermediateUsageLabels = setOf("texture_binding"),
                reason = "unsupported.filter.intermediate_unvalidated",
            ),
            refusalCase(
                "intermediate-unvalidated",
                intermediateOwnershipValidated = false,
                reason = "unsupported.filter.intermediate_unvalidated",
            ),
            refusalCase(
                "active-attachment",
                activeAttachmentSampled = true,
                reason = "unsupported.filter.read_write_aliasing",
            ),
            refusalCase(
                "read-write-alias",
                readWriteAliasing = true,
                reason = "unsupported.filter.read_write_aliasing",
            ),
            refusalCase(
                "binding-unvalidated",
                renderNodeBindingValidated = false,
                reason = "unsupported.filter.node_descriptor_invalid",
            ),
            refusalCase(
                "cpu-fallback",
                cpuRenderedTextureFallbackRequested = true,
                reason = "unsupported.filter.cpu_rendered_texture_forbidden",
            ),
            refusalCase(
                "budget",
                maxIntermediateBytes = 1024,
                reason = "unsupported.filter.intermediate_budget_exceeded",
            ),
        )

        cases.forEach { case ->
            val result = GPUSimpleFilterRenderNodePlanner().plan(case.request)

            assertIs<GPUFilterNodePlan.Refused>(result.filterPlan.nodePlans.single())
            assertEquals(case.reason, result.diagnostics.single().code)
            assertEquals(
                listOf(
                    "filter:simple-node.refused row=gpu-renderer.filter.simple-node routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=false materialized=false graph=filter-card node=${case.nodeId} reason=${case.reason} label=${case.label}",
                    "filter:nonclaim nativeFilter=false adapterBacked=false arbitraryFilterDag=false runtimeEffectFilter=false cpuRenderedFilterTextureFallback=false productActivation=false",
                ),
                result.dumpLines(),
            )
        }
    }
}

private fun assertAcceptedDiagnostic(result: GPUSimpleFilterRenderNodeGatePlan) {
    val diagnostic = result.diagnostics.single()

    assertEquals("accepted.filter.simple_node", diagnostic.code)
    assertFalse(diagnostic.terminal)
}

private data class RefusalCase(
    val label: String,
    val request: GPUSimpleFilterRenderNodeRequest,
    val reason: String,
) {
    val nodeId: String = request.graph.nodes.firstOrNull()?.nodeId?.value ?: "none"
}

private fun refusalCase(
    label: String,
    reason: String,
    graph: GPUFilterGraphDescriptor = filterGraph(node("cf-1", "ColorFilter")),
    bounds: GPUSimpleFilterBounds = bounds(),
    intermediateUsageLabels: Set<String> = setOf("render_attachment", "texture_binding"),
    intermediateOwnershipValidated: Boolean = true,
    activeAttachmentSampled: Boolean = false,
    readWriteAliasing: Boolean = false,
    renderNodeBindingValidated: Boolean = true,
    cpuRenderedTextureFallbackRequested: Boolean = false,
    maxIntermediateBytes: Long = 16 * 1024 * 1024,
): RefusalCase = RefusalCase(
    label = label,
    reason = reason,
    request = simpleFilterRequest(
        label = label,
        graph = graph,
        bounds = bounds,
        intermediateUsageLabels = intermediateUsageLabels,
        intermediateOwnershipValidated = intermediateOwnershipValidated,
        activeAttachmentSampled = activeAttachmentSampled,
        readWriteAliasing = readWriteAliasing,
        renderNodeBindingValidated = renderNodeBindingValidated,
        cpuRenderedTextureFallbackRequested = cpuRenderedTextureFallbackRequested,
        maxIntermediateBytes = maxIntermediateBytes,
    ),
)

private fun simpleFilterRequest(
    label: String = "accepted",
    graph: GPUFilterGraphDescriptor = filterGraph(node("cf-1", "ColorFilter")),
    bounds: GPUSimpleFilterBounds = bounds(),
    intermediateUsageLabels: Set<String> = setOf("render_attachment", "texture_binding"),
    intermediateOwnershipValidated: Boolean = true,
    activeAttachmentSampled: Boolean = false,
    readWriteAliasing: Boolean = false,
    renderNodeBindingValidated: Boolean = true,
    cpuRenderedTextureFallbackRequested: Boolean = false,
    targetGeneration: Long = 17,
    maxIntermediateBytes: Long = 16 * 1024 * 1024,
): GPUSimpleFilterRenderNodeRequest = GPUSimpleFilterRenderNodeRequest(
    label = label,
    graph = graph,
    source = GPUFilterSourcePlan(
        sourceLabel = "layer-source",
        boundsLabel = "0,0,64,48",
        colorTreatment = "premul-srgb",
    ),
    bounds = bounds,
    crop = GPUFilterCropPlan(
        cropLabel = "0,0,64,48",
        tilePolicy = GPUFilterTilePlan(
            tileModeX = "decal",
            tileModeY = "decal",
            decalOutsideCrop = true,
        ),
    ),
    sampling = GPUFilterSamplingPlan(
        filterMode = "nearest",
        mipmapMode = "none",
        coordinateSpaceLabel = "layer",
    ),
    targetFormatClass = "rgba8unorm",
    targetGeneration = targetGeneration,
    intermediateUsageLabels = intermediateUsageLabels,
    intermediateOwnershipValidated = intermediateOwnershipValidated,
    activeAttachmentSampled = activeAttachmentSampled,
    readWriteAliasing = readWriteAliasing,
    renderNodeBindingValidated = renderNodeBindingValidated,
    cpuRenderedTextureFallbackRequested = cpuRenderedTextureFallbackRequested,
    maxIntermediateBytes = maxIntermediateBytes,
)

private fun filterGraph(vararg nodes: GPUFilterNodeDescriptor): GPUFilterGraphDescriptor =
    GPUFilterGraphDescriptor(
        graphId = "filter-card",
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
        inputLabels = listOf("source"),
        parameterHash = "$kind:params",
    )

private fun bounds(
    finite: Boolean = true,
    width: Int = 64,
    height: Int = 48,
): GPUSimpleFilterBounds = GPUSimpleFilterBounds(
    inputBoundsLabel = "0,0,64,48",
    outputBoundsLabel = "0,0,64,48",
    conservative = true,
    finite = finite,
    width = width,
    height = height,
)
