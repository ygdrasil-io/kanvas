package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VerticesBatchingPlanTest {
    @Test
    fun compatibleAdjacentVerticesProduceDeterministicBatchKeyAndSortDumpWithoutProductPromotion() {
        val first = verticesBatchInvocation(invocationId = "vertices:0", paintOrder = 0)
        val second = verticesBatchInvocation(invocationId = "vertices:1", paintOrder = 1)

        val plan = GPUVerticesBatchingPlanner().plan(
            GPUVerticesBatchingRequest(
                scopeId = "root-pass",
                invocations = listOf(first, second),
            ),
        )

        assertEquals("gpu-renderer.vertices-batching", plan.evidenceRow)
        assertEquals("GPUNative", plan.routeKind)
        assertEquals("ImplementationCandidate", plan.classification)
        assertFalse(plan.promoted)
        assertFalse(plan.productActivation)
        assertFalse(plan.materialized)
        assertEquals("accepted.vertices.batching_plan", plan.diagnostics.single().code)
        assertFalse(plan.diagnostics.single().terminal)
        assertEquals(1, plan.batches.size)
        assertEquals(listOf("vertices:0", "vertices:1"), plan.batches.single().invocationIds)
        assertEquals(emptyList(), plan.splitReasons)
        assertEquals(
            listOf(
                "vertices:batch row=gpu-renderer.vertices-batching routeKind=GPUNative classification=ImplementationCandidate promoted=false productActivation=false materialized=false scope=root-pass batches=1 splits=0",
                "vertices:batch-key hash=${plan.batches.single().batchKeyHash} invocations=vertices:0,vertices:1 axes=layer=root-layer,orderBand=opaque,sortWindow=root-window,pipeline=${first.pipelineKeyHash},material=${first.materialKeyHash},layout=${first.layoutHash},clip=scissor:main,destinationRead=none,barrier=0,uploadGeneration=3",
                "vertices:sort window=root-window compact=${plan.sortKeyHash} order=vertices:0@0,vertices:1@1 overlap=CompatibleOverlap insertion=original-order",
                "vertices:split none",
                "vertices:telemetry adjacentCandidates=1 acceptedAdjacent=1 splitCount=0 refused=false performanceReady=false",
                "vertices:diagnostic code=accepted.vertices.batching_plan terminal=false",
                "vertices:nonclaim batchingSupport=false drawVerticesSupport=false adapterBacked=false productActivation=false performanceReady=false crossLayerBatching=false destinationReadBatching=false cpuRenderedTextureFallback=false",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun adjacentVerticesSplitAtSortWindowBoundaryAndExposeWindowInDump() {
        val first = verticesBatchInvocation(invocationId = "vertices:0", paintOrder = 0)
        val second = verticesBatchInvocation(
            invocationId = "vertices:1",
            paintOrder = 1,
            sortWindowId = "overlay-window",
        )

        val plan = GPUVerticesBatchingPlanner().plan(
            GPUVerticesBatchingRequest(
                scopeId = "root-pass",
                invocations = listOf(first, second),
            ),
        )

        assertEquals(listOf("planner.stop.sort_window"), plan.splitReasons.map { split -> split.reasonCode })
        assertEquals(2, plan.batches.size)
        assertEquals(listOf(listOf("vertices:0"), listOf("vertices:1")), plan.batches.map { batch -> batch.invocationIds })
        assertEquals(
            listOf(
                "vertices:batch row=gpu-renderer.vertices-batching routeKind=GPUNative classification=ImplementationCandidate promoted=false productActivation=false materialized=false scope=root-pass batches=2 splits=1",
                "vertices:batch-key hash=${plan.batches[0].batchKeyHash} invocations=vertices:0 axes=layer=root-layer,orderBand=opaque,sortWindow=root-window,pipeline=${first.pipelineKeyHash},material=${first.materialKeyHash},layout=${first.layoutHash},clip=scissor:main,destinationRead=none,barrier=0,uploadGeneration=3",
                "vertices:batch-key hash=${plan.batches[1].batchKeyHash} invocations=vertices:1 axes=layer=root-layer,orderBand=opaque,sortWindow=overlay-window,pipeline=${second.pipelineKeyHash},material=${second.materialKeyHash},layout=${second.layoutHash},clip=scissor:main,destinationRead=none,barrier=0,uploadGeneration=3",
                "vertices:sort window=root-window|overlay-window compact=${plan.sortKeyHash} order=vertices:0@0,vertices:1@1 overlap=CompatibleOverlap insertion=original-order",
                "vertices:split reason=planner.stop.sort_window before=vertices:0 after=vertices:1",
                "vertices:telemetry adjacentCandidates=1 acceptedAdjacent=0 splitCount=1 refused=false performanceReady=false",
                "vertices:diagnostic code=accepted.vertices.batching_plan terminal=false",
                "vertices:nonclaim batchingSupport=false drawVerticesSupport=false adapterBacked=false productActivation=false performanceReady=false crossLayerBatching=false destinationReadBatching=false cpuRenderedTextureFallback=false",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun incompatibleVerticesSplitAtVisualResourceAndOrderingBoundaries() {
        val base = verticesBatchInvocation(invocationId = "vertices:0", paintOrder = 0)
        val topology = verticesBatchInvocation(
            invocationId = "vertices:1",
            paintOrder = 1,
            routeDecision = routeDecision(
                descriptor = verticesDescriptor(primitiveMode = GPUVertexMode.TriangleStrip),
            ),
        )
        val renderStep = verticesBatchInvocation(
            invocationId = "vertices:2",
            paintOrder = 2,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = "vertices.triangles.color.v1",
        )
        val pipeline = verticesBatchInvocation(
            invocationId = "vertices:3",
            paintOrder = 3,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = "sha256:pipeline-other",
        )
        val material = verticesBatchInvocation(
            invocationId = "vertices:4",
            paintOrder = 4,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = "material:gradient",
        )
        val blend = verticesBatchInvocation(
            invocationId = "vertices:5",
            paintOrder = 5,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = "primitive-blend",
        )
        val clip = verticesBatchInvocation(
            invocationId = "vertices:6",
            paintOrder = 6,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = blend.blendClass,
            clipKey = "clip:analytic",
        )
        val layer = verticesBatchInvocation(
            invocationId = "vertices:7",
            paintOrder = 7,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = blend.blendClass,
            clipKey = clip.clipKey,
            layerId = "layer:overlay",
        )
        val destinationRead = verticesBatchInvocation(
            invocationId = "vertices:8",
            paintOrder = 8,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = blend.blendClass,
            clipKey = clip.clipKey,
            layerId = layer.layerId,
            destinationReadClass = "TargetCopySnapshot",
        )
        val barrier = verticesBatchInvocation(
            invocationId = "vertices:9",
            paintOrder = 9,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = blend.blendClass,
            clipKey = clip.clipKey,
            layerId = layer.layerId,
            barrierGeneration = 1,
        )
        val uploadGeneration = verticesBatchInvocation(
            invocationId = "vertices:10",
            paintOrder = 10,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = blend.blendClass,
            clipKey = clip.clipKey,
            layerId = layer.layerId,
            barrierGeneration = barrier.barrierGeneration,
            uploadGeneration = 4,
        )
        val unknownOverlap = verticesBatchInvocation(
            invocationId = "vertices:11",
            paintOrder = 11,
            routeDecision = topology.routeDecision,
            bufferPlan = topology.bufferPlan,
            topology = "TriangleStrip",
            renderStepLabel = renderStep.renderStepLabel,
            pipelineKeyHash = pipeline.pipelineKeyHash,
            materialKeyHash = material.materialKeyHash,
            blendClass = blend.blendClass,
            clipKey = clip.clipKey,
            layerId = layer.layerId,
            barrierGeneration = barrier.barrierGeneration,
            uploadGeneration = uploadGeneration.uploadGeneration,
            overlapClass = "UnknownOverlap",
        )

        val plan = GPUVerticesBatchingPlanner().plan(
            GPUVerticesBatchingRequest(
                scopeId = "root-pass",
                invocations = listOf(
                    base,
                    topology,
                    renderStep,
                    pipeline,
                    material,
                    blend,
                    clip,
                    layer,
                    destinationRead,
                    barrier,
                    uploadGeneration,
                    unknownOverlap,
                ),
            ),
        )

        assertEquals(
            listOf(
                "planner.stop.topology",
                "planner.stop.render_step",
                "planner.stop.pipeline_key",
                "planner.stop.material_key",
                "planner.stop.blend_class",
                "planner.stop.clip_boundary",
                "planner.stop.layer_boundary",
                "planner.stop.destination_read",
                "planner.stop.barrier",
                "planner.stop.upload_generation",
                "planner.stop.incompatible_overlap",
            ),
            plan.splitReasons.map { split -> split.reasonCode },
        )
        assertEquals(12, plan.batches.size)
        assertEquals(
            listOf(
                "vertices:split reason=planner.stop.topology before=vertices:0 after=vertices:1",
                "vertices:split reason=planner.stop.render_step before=vertices:1 after=vertices:2",
                "vertices:split reason=planner.stop.pipeline_key before=vertices:2 after=vertices:3",
                "vertices:split reason=planner.stop.material_key before=vertices:3 after=vertices:4",
                "vertices:split reason=planner.stop.blend_class before=vertices:4 after=vertices:5",
                "vertices:split reason=planner.stop.clip_boundary before=vertices:5 after=vertices:6",
                "vertices:split reason=planner.stop.layer_boundary before=vertices:6 after=vertices:7",
                "vertices:split reason=planner.stop.destination_read before=vertices:7 after=vertices:8",
                "vertices:split reason=planner.stop.barrier before=vertices:8 after=vertices:9",
                "vertices:split reason=planner.stop.upload_generation before=vertices:9 after=vertices:10",
                "vertices:split reason=planner.stop.incompatible_overlap before=vertices:10 after=vertices:11",
            ),
            plan.dumpLines().filter { line -> line.startsWith("vertices:split reason=") },
        )
        assertFalse(plan.dumpLines().joinToString("\n").contains("performanceReady=true"))
    }

    @Test
    fun invalidBatchInputsRefuseWithStableDiagnostics() {
        val accepted = verticesBatchInvocation()
        val refusedRoute = verticesBatchInvocation(
            routeDecision = GPUVerticesRouteDecisionPlanner().plan(
                verticesRouteRequest(
                    descriptor = verticesDescriptor(primitiveMode = GPUVertexMode.Unsupported("LineList")),
                ),
            ),
        )
        val refusedBuffer = verticesBatchInvocation(
            bufferPlan = GPUVerticesBufferPlanPlanner().plan(
                verticesBufferRequest(
                    routeDecision = accepted.routeDecision,
                    uploadBeforeDraw = false,
                ),
            ),
        )
        val orderRegression = listOf(
            verticesBatchInvocation(invocationId = "vertices:later", paintOrder = 2),
            verticesBatchInvocation(invocationId = "vertices:earlier", paintOrder = 1),
        )

        val cases = listOf(
            BatchRefusalCase(
                expectedCode = "unsupported.vertices.batch_empty",
                request = GPUVerticesBatchingRequest(scopeId = "root-pass", invocations = emptyList()),
            ),
            BatchRefusalCase(
                expectedCode = "unsupported.vertices.batch_route_required",
                request = GPUVerticesBatchingRequest(scopeId = "root-pass", invocations = listOf(refusedRoute)),
                expectedFacts = mapOf("routeKind" to "RefuseDiagnostic"),
            ),
            BatchRefusalCase(
                expectedCode = "unsupported.vertices.batch_buffer_plan_required",
                request = GPUVerticesBatchingRequest(scopeId = "root-pass", invocations = listOf(refusedBuffer)),
                expectedFacts = mapOf("bufferRouteKind" to "RefuseDiagnostic"),
            ),
            BatchRefusalCase(
                expectedCode = "unsupported.vertices.batch_order_ambiguous",
                request = GPUVerticesBatchingRequest(scopeId = "root-pass", invocations = orderRegression),
                expectedFacts = mapOf("previousPaintOrder" to "2", "nextPaintOrder" to "1"),
            ),
        )

        cases.forEach { case ->
            val plan = GPUVerticesBatchingPlanner().plan(case.request)

            assertEquals("RefuseDiagnostic", plan.routeKind)
            assertEquals(case.expectedCode, plan.diagnostics.single().code)
            case.expectedFacts.forEach { (key, value) ->
                assertEquals(value, plan.refusalFacts[key])
            }
            assertEquals(
                listOf(
                    "vertices:batch.refused row=gpu-renderer.vertices-batching routeKind=RefuseDiagnostic classification=ImplementationCandidate promoted=false productActivation=false materialized=false scope=${case.request.scopeId} reason=${case.expectedCode}",
                    "vertices:refusal facts=${plan.refusalFacts.entries.sortedBy { entry -> entry.key }.joinToString(",") { entry -> "${entry.key}=${entry.value}" }}",
                    "vertices:nonclaim batchingSupport=false drawVerticesSupport=false adapterBacked=false productActivation=false performanceReady=false crossLayerBatching=false destinationReadBatching=false cpuRenderedTextureFallback=false",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class BatchRefusalCase(
    val expectedCode: String,
    val request: GPUVerticesBatchingRequest,
    val expectedFacts: Map<String, String> = emptyMap(),
)

private fun verticesBatchInvocation(
    invocationId: String = "vertices:0",
    paintOrder: Int = 0,
    routeDecision: GPUVerticesRouteDecisionGatePlan = GPUVerticesRouteDecisionPlanner().plan(verticesRouteRequest()),
    bufferPlan: GPUVerticesBufferPlanGatePlan = GPUVerticesBufferPlanPlanner().plan(
        verticesBufferRequest(routeDecision = routeDecision),
    ),
    materialKeyHash: String = routeDecision.materialKeyHash,
    pipelineKeyHash: String = routeDecision.pipelineKeyHash,
    layoutHash: String = routeDecision.layoutHash,
    renderStepLabel: String = routeDecision.renderStep.renderStepLabel,
    topology: String = routeDecision.descriptor.primitiveMode.sourceLabel,
    blendClass: String = "fixed",
    layerId: String = "root-layer",
    orderBand: String = "opaque",
    clipKey: String = "scissor:main",
    destinationReadClass: String = "none",
    barrierGeneration: Long = 0L,
    uploadGeneration: Long = bufferPlan.resourcePlan.bufferGeneration,
    overlapClass: String = "CompatibleOverlap",
    sortWindowId: String = "root-window",
): GPUVerticesBatchInvocation =
    GPUVerticesBatchInvocation(
        invocationId = invocationId,
        routeDecision = routeDecision,
        bufferPlan = bufferPlan,
        paintOrder = paintOrder,
        materialKeyHash = materialKeyHash,
        pipelineKeyHash = pipelineKeyHash,
        layoutHash = layoutHash,
        renderStepLabel = renderStepLabel,
        topology = topology,
        blendClass = blendClass,
        layerId = layerId,
        orderBand = orderBand,
        clipKey = clipKey,
        destinationReadClass = destinationReadClass,
        barrierGeneration = barrierGeneration,
        uploadGeneration = uploadGeneration,
        overlapClass = overlapClass,
        sortWindowId = sortWindowId,
    )

private fun routeDecision(
    descriptor: GPUVerticesDescriptor = verticesDescriptor(),
): GPUVerticesRouteDecisionGatePlan =
    GPUVerticesRouteDecisionPlanner().plan(verticesRouteRequest(descriptor = descriptor))

private fun verticesBufferRequest(
    routeDecision: GPUVerticesRouteDecisionGatePlan = GPUVerticesRouteDecisionPlanner().plan(verticesRouteRequest()),
    uploadBeforeDraw: Boolean = true,
): GPUVerticesBufferPlanRequest =
    GPUVerticesBufferPlanRequest(
        routeDecision = routeDecision,
        sourceVertexContentHash = "sha256:vertex-payload",
        sourceIndexContentHash = null,
        uploadBeforeDraw = uploadBeforeDraw,
        deviceGeneration = 7,
        bufferGeneration = 3,
    )

private fun verticesRouteRequest(
    descriptor: GPUVerticesDescriptor = verticesDescriptor(),
): GPUVerticesRouteDecisionRequest =
    GPUVerticesRouteDecisionRequest(
        commandId = "vertices:triangles",
        descriptor = descriptor,
        materialKeyHash = "material:solid",
        targetFormatClass = "rgba8unorm",
        adapterEvidenceLabel = "wgpu-layout-evidence",
        wgslLayoutEvidenceLabel = "vertices-wgsl-layout-v1",
    )

private fun verticesDescriptor(
    primitiveMode: GPUVertexMode = GPUVertexMode.Triangles,
): GPUVerticesDescriptor =
    GPUVerticesDescriptor(
        primitiveMode = primitiveMode,
        vertexCount = 3,
        indexCount = null,
        hasColors = false,
        hasTexCoords = false,
        boundsLabel = "local[0,0,16,16]",
        provenance = "unit-test",
        sourceKey = "sha256:vertices-source",
    )
