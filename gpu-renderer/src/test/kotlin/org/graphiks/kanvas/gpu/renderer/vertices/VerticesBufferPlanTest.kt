package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class VerticesBufferPlanTest {
    @Test
    fun indexedVerticesProduceDeterministicBufferUploadAndResourcePlansWithoutProductPromotion() {
        val routeDecision = GPUVerticesRouteDecisionPlanner().plan(
            verticesRouteRequest(
                commandId = "vertices:indexed",
                descriptor = verticesDescriptor(
                    vertexCount = 4,
                    indexCount = 6,
                    hasColors = true,
                ),
            ),
        )

        val plan = GPUVerticesBufferPlanPlanner().plan(
            verticesBufferRequest(
                routeDecision = routeDecision,
                sourceVertexContentHash = "sha256:vertex-payload-a",
                sourceIndexContentHash = "sha256:index-payload-a",
                sourceIndexType = "uint16",
                minIndex = 0,
                maxIndex = 3,
                deviceGeneration = 9,
                bufferGeneration = 5,
            ),
        )

        assertEquals("gpu-renderer.vertices.buffers", plan.evidenceRow)
        assertEquals("CPUPreparedGPU", plan.routeKind)
        assertEquals("TargetPrepared", plan.classification)
        assertFalse(plan.promoted)
        assertFalse(plan.productActivation)
        assertFalse(plan.materialized)
        assertEquals("accepted.vertices.buffer_plan", plan.diagnostics.single().code)
        assertFalse(plan.diagnostics.single().terminal)
        assertEquals(48L, plan.vertexBufferPlan.byteCount)
        assertEquals(12L, plan.indexBufferPlan?.byteCount)
        assertEquals(60L, plan.uploadPlan.totalBytes)
        assertEquals("upload-before-draw:vertices:indexed", plan.uploadPlan.beforeUseToken)
        assertEquals(listOf("copy_dst", "vertex"), plan.vertexBufferPlan.usageFlags)
        assertEquals(listOf("copy_dst", "index"), plan.indexBufferPlan?.usageFlags)
        assertEquals(
            listOf("localCoords=position", "primitiveBlend=SrcOver", "primitiveColor=true"),
            plan.materialKeyFacts,
        )
        assertEquals(
            listOf(
                "vertices:buffers row=gpu-renderer.vertices.buffers routeKind=CPUPreparedGPU classification=TargetPrepared promoted=false productActivation=false materialized=false command=vertices:indexed descriptor=${plan.descriptorHash} routeDecision=${plan.routeDecisionHash} artifact=${plan.artifactKey}",
                "vertices:vertex-buffer hash=${plan.vertexBufferHash} layout=${routeDecision.layoutHash} bytes=48 alignment=4 usage=copy_dst,vertex owner=GPURecorderScope generation=5 upload=upload-before-draw",
                "vertices:index-buffer hash=${plan.indexBufferHash} format=uint16 count=6 range=0..3 bytes=12 alignment=4 usage=copy_dst,index owner=GPURecorderScope generation=5 upload=upload-before-draw",
                "vertices:upload plan=${plan.uploadPlan.planHash} staging=GPURecorderScope ranges=vertex:0..47,index:48..59 bytes=60 dependency=upload-before-draw:vertices:indexed budget=vertices-buffer-default",
                "vertices:resource owner=GPURecorderScope deviceGeneration=9 bufferGeneration=5 invalidation=buffer-generation:5,device-generation:9 usage=copy_dst,index,vertex liveHandle=false materialKey=false",
                "vertices:key materialFacts=localCoords=position,primitiveBlend=SrcOver,primitiveColor=true resourceFactsExcluded=bufferBytes,bufferGenerations,resourceHandles,uploadOffsets,vertexIndexPayload",
                "vertices:diagnostic code=accepted.vertices.buffer_plan terminal=false",
                "vertices:nonclaim drawVerticesSupport=false adapterBacked=false vertexBufferUpload=false indexBufferUpload=false meshSupport=false batchingSupport=false productActivation=false cpuRenderedTextureFallback=false liveHandles=false",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun bufferResourceFactsStayOutOfMaterialKeys() {
        val routeDecision = GPUVerticesRouteDecisionPlanner().plan(
            verticesRouteRequest(
                descriptor = verticesDescriptor(vertexCount = 4, indexCount = 6),
            ),
        )
        val first = GPUVerticesBufferPlanPlanner().plan(
            verticesBufferRequest(
                routeDecision = routeDecision,
                sourceVertexContentHash = "sha256:vertex-payload-a",
                sourceIndexContentHash = "sha256:index-payload-a",
                bufferGeneration = 5,
            ),
        )
        val second = GPUVerticesBufferPlanPlanner().plan(
            verticesBufferRequest(
                routeDecision = routeDecision,
                sourceVertexContentHash = "sha256:vertex-payload-b",
                sourceIndexContentHash = "sha256:index-payload-b",
                bufferGeneration = 6,
            ),
        )

        assertNotEquals(first.artifactKey, second.artifactKey)
        assertNotEquals(first.vertexBufferHash, second.vertexBufferHash)
        assertEquals(first.materialKeyFacts, second.materialKeyFacts)
        assertFalse(first.dumpLines().single { it.startsWith("vertices:key") }.contains("vertex-payload-a"))
        assertFalse(second.dumpLines().single { it.startsWith("vertices:key") }.contains("vertex-payload-b"))
        assertFalse(second.dumpLines().single { it.startsWith("vertices:key") }.contains("generation=6"))
    }

    @Test
    fun invalidMissingStaleAndBudgetBufferFactsRefuseBeforeSubmission() {
        val accepted = GPUVerticesRouteDecisionPlanner().plan(
            verticesRouteRequest(
                descriptor = verticesDescriptor(vertexCount = 4, indexCount = 6),
            ),
        )
        val refusedRouteDecision = GPUVerticesRouteDecisionPlanner().plan(
            verticesRouteRequest(
                descriptor = verticesDescriptor(primitiveMode = GPUVertexMode.Unsupported("LineList")),
            ),
        )

        val cases = listOf(
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.route_decision_required",
                request = verticesBufferRequest(routeDecision = refusedRouteDecision),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.index_out_of_range",
                request = verticesBufferRequest(routeDecision = accepted, maxIndex = 4),
                expectedFacts = mapOf("maxIndex" to "4"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.upload_unavailable",
                request = verticesBufferRequest(routeDecision = accepted, uploadBeforeDraw = false),
                expectedFacts = mapOf("uploadBeforeDraw" to "false"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.buffer_budget_exceeded",
                request = verticesBufferRequest(routeDecision = accepted, maxVertexBufferBytes = 16),
                expectedFacts = mapOf("vertexBytes" to "32"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.buffer_budget_exceeded",
                request = verticesBufferRequest(routeDecision = accepted, maxIndexBufferBytes = 8),
                expectedFacts = mapOf("indexBytes" to "12"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.payload.upload_budget_exceeded",
                request = verticesBufferRequest(routeDecision = accepted, maxTotalUploadBytes = 16),
                expectedFacts = mapOf("totalUploadBytes" to "44"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.upload_unavailable",
                request = verticesBufferRequest(
                    routeDecision = accepted,
                    availableUsageFlags = setOf("copy_dst", "vertex"),
                ),
                expectedFacts = mapOf("availableUsageFlags" to "copy_dst,vertex"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.payload.resource_stale_generation",
                request = verticesBufferRequest(
                    routeDecision = accepted,
                    deviceGeneration = 9,
                    observedDeviceGeneration = 8,
                ),
                expectedFacts = mapOf("observedDeviceGeneration" to "8"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.payload.resource_stale_generation",
                request = verticesBufferRequest(
                    routeDecision = accepted,
                    bufferGeneration = 9,
                    observedBufferGeneration = 8,
                ),
                expectedFacts = mapOf("observedBufferGeneration" to "8"),
            ),
            BufferRefusalCase(
                expectedCode = "unsupported.vertices.resource_handle_leak",
                request = verticesBufferRequest(routeDecision = accepted, liveResourceHandleExposed = true),
                expectedFacts = mapOf("liveResourceHandleExposed" to "true"),
            ),
        )

        cases.forEach { case ->
            val plan = GPUVerticesBufferPlanPlanner().plan(case.request)

            assertEquals("RefuseDiagnostic", plan.routeKind)
            assertEquals(case.expectedCode, plan.diagnostics.single().code)
            case.expectedFacts.forEach { (key, value) ->
                assertEquals(value, plan.refusalFacts[key])
            }
            assertEquals(
                listOf(
                    "vertices:buffers.refused row=gpu-renderer.vertices.buffers routeKind=RefuseDiagnostic classification=TargetPrepared promoted=false productActivation=false materialized=false command=${case.request.routeDecision.commandId} descriptor=${plan.descriptorHash} routeDecision=${plan.routeDecisionHash} reason=${case.expectedCode}",
                    "vertices:refusal facts=${plan.refusalFacts.entries.sortedBy { entry -> entry.key }.joinToString(",") { entry -> "${entry.key}=${entry.value}" }}",
                    "vertices:nonclaim drawVerticesSupport=false adapterBacked=false vertexBufferUpload=false indexBufferUpload=false meshSupport=false batchingSupport=false productActivation=false cpuRenderedTextureFallback=false liveHandles=false",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class BufferRefusalCase(
    val expectedCode: String,
    val request: GPUVerticesBufferPlanRequest,
    val expectedFacts: Map<String, String> = emptyMap(),
)

private fun verticesBufferRequest(
    routeDecision: GPUVerticesRouteDecisionGatePlan = GPUVerticesRouteDecisionPlanner().plan(verticesRouteRequest()),
    sourceVertexContentHash: String = "sha256:vertex-payload",
    sourceIndexContentHash: String? = "sha256:index-payload",
    sourceIndexType: String = "uint16",
    minIndex: Int = 0,
    maxIndex: Int = 2,
    uploadBeforeDraw: Boolean = true,
    deviceGeneration: Long = 7,
    observedDeviceGeneration: Long = deviceGeneration,
    bufferGeneration: Long = 3,
    observedBufferGeneration: Long = bufferGeneration,
    maxVertexBufferBytes: Long = 1_048_576L,
    maxIndexBufferBytes: Long = 1_048_576L,
    maxTotalUploadBytes: Long = 2_097_152L,
    availableUsageFlags: Set<String> = setOf("copy_dst", "vertex", "index"),
    liveResourceHandleExposed: Boolean = false,
): GPUVerticesBufferPlanRequest =
    GPUVerticesBufferPlanRequest(
        routeDecision = routeDecision,
        sourceVertexContentHash = sourceVertexContentHash,
        sourceIndexContentHash = sourceIndexContentHash,
        sourceIndexType = sourceIndexType,
        minIndex = minIndex,
        maxIndex = maxIndex,
        uploadBeforeDraw = uploadBeforeDraw,
        deviceGeneration = deviceGeneration,
        observedDeviceGeneration = observedDeviceGeneration,
        bufferGeneration = bufferGeneration,
        observedBufferGeneration = observedBufferGeneration,
        maxVertexBufferBytes = maxVertexBufferBytes,
        maxIndexBufferBytes = maxIndexBufferBytes,
        maxTotalUploadBytes = maxTotalUploadBytes,
        availableUsageFlags = availableUsageFlags,
        liveResourceHandleExposed = liveResourceHandleExposed,
    )

private fun verticesRouteRequest(
    commandId: String = "vertices:triangles",
    descriptor: GPUVerticesDescriptor = verticesDescriptor(),
): GPUVerticesRouteDecisionRequest =
    GPUVerticesRouteDecisionRequest(
        commandId = commandId,
        descriptor = descriptor,
        materialKeyHash = "material:solid",
        targetFormatClass = "rgba8unorm",
        adapterEvidenceLabel = "wgpu-layout-evidence",
        wgslLayoutEvidenceLabel = "vertices-wgsl-layout-v1",
    )

private fun verticesDescriptor(
    primitiveMode: GPUVertexMode = GPUVertexMode.Triangles,
    vertexCount: Int = 3,
    indexCount: Int? = null,
    hasColors: Boolean = false,
    hasTexCoords: Boolean = false,
): GPUVerticesDescriptor =
    GPUVerticesDescriptor(
        primitiveMode = primitiveMode,
        vertexCount = vertexCount,
        indexCount = indexCount,
        hasColors = hasColors,
        hasTexCoords = hasTexCoords,
        boundsLabel = "local[0,0,16,16]",
        provenance = "unit-test",
        sourceKey = "sha256:vertices-source",
    )
