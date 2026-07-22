package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VerticesRouteDecisionTest {
    @Test
    fun vertexModeLabelsAreClosedForAcceptedAndUnsupportedInputs() {
        assertEquals("Triangles", GPUVertexMode.Triangles.sourceLabel)
        assertEquals("TriangleStrip", GPUVertexMode.TriangleStrip.sourceLabel)
        assertEquals("TriangleFan", GPUVertexMode.TriangleFan.sourceLabel)
        assertEquals("LineList", GPUVertexMode.Unsupported("LineList").sourceLabel)
    }

    @Test
    fun trianglesDescriptorProducesDeterministicRouteDecisionWithoutProductPromotion() {
        val plan = GPUVerticesRouteDecisionPlanner().plan(verticesRequest())

        assertEquals("gpu-renderer.vertices.descriptor", plan.evidenceRow)
        assertEquals("GPUNative", plan.routeKind)
        assertEquals("TargetNative", plan.classification)
        assertFalse(plan.promoted)
        assertTrue(plan.productActivation)
        assertFalse(plan.materialized)
        assertEquals("accepted.vertices.route_decision", plan.diagnostics.single().code)
        assertFalse(plan.diagnostics.single().terminal)
        assertEquals("position-only", plan.variant)
        assertEquals(listOf("position"), plan.layout.attributes)
        assertEquals(
            listOf(
                "vertices:descriptor row=gpu-renderer.vertices.descriptor routeKind=GPUNative classification=TargetNative promoted=false productActivation=true materialized=false command=vertices:triangles descriptor=${plan.descriptorHash} mode=Triangles vertexCount=3 indexCount=none variant=position-only",
                "vertices:layout hash=${plan.layoutHash} attributes=position:f32x2@0:0 stride=8 locations=position:0",
                "vertices:route decision=NativeDescriptor renderStep=vertices.triangles.position-only.v1 pipeline=${plan.pipelineKeyHash} material=material:solid target=rgba8unorm adapter=wgpu-layout-evidence wgsl=vertices-wgsl-layout-v1",
                "vertices:key materialFacts=localCoords=position,primitiveBlend=none,primitiveColor=false pipelineFacts=layout=${plan.layoutHash},mode=Triangles,primitiveColor=false,target=rgba8unorm,texcoord=false",
                "vertices:diagnostic code=accepted.vertices.route_decision terminal=false",
                "vertices:nonclaim drawVerticesSupport=false adapterBacked=false vertexBufferUpload=false indexBufferUpload=false primitiveBlenderSupport=false texcoordMaterialSupport=false meshSupport=false productActivation=true cpuRenderedTextureFallback=false",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun unsupportedVerticesInputsRefuseWithStableDiagnostics() {
        val cases = listOf(
            RefusalCase(
                expectedCode = "unsupported.vertices.topology",
                descriptor = verticesDescriptor(primitiveMode = GPUVertexMode.Unsupported("LineList")),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.triangle_fan_unprepared",
                descriptor = verticesDescriptor(primitiveMode = GPUVertexMode.TriangleFan),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.key_nondeterministic",
                descriptor = verticesDescriptor(sourceMutable = true),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.positions_nonfinite",
                descriptor = verticesDescriptor(finitePositions = false),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.vertex_count_budget",
                descriptor = verticesDescriptor(vertexCount = 65_536),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.index_count_budget",
                descriptor = verticesDescriptor(indexCount = 70_000),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.attribute_format",
                descriptor = verticesDescriptor(positionFormat = "f16x2"),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.color_format",
                descriptor = verticesDescriptor(hasColors = true, colorFormat = "rgba32float"),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.local_coords_unproven",
                descriptor = verticesDescriptor(hasTexCoords = true, materialLocalCoordinatePolicy = "perspective-texcoord"),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.primitive_blender_unregistered",
                descriptor = verticesDescriptor(hasColors = true, primitiveBlendMode = "custom-skblender"),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.primitive_blend_destination_read",
                descriptor = verticesDescriptor(hasColors = true, primitiveBlendMode = "Multiply"),
                expectedFacts = mapOf("primitiveBlendDestinationRead" to "true"),
            ),
            RefusalCase(
                expectedCode = "unsupported.vertices.wgsl_abi_unvalidated",
                request = verticesRequest(wgslLayoutEvidenceLabel = null),
            ),
        )

        cases.forEach { case ->
            val plan = GPUVerticesRouteDecisionPlanner().plan(
                case.request ?: verticesRequest(descriptor = case.descriptor),
            )

            assertEquals("RefuseDiagnostic", plan.routeKind)
            assertEquals(case.expectedCode, plan.diagnostics.single().code)
            case.expectedFacts.forEach { (key, value) ->
                assertEquals(value, plan.refusalFacts[key])
            }
            assertEquals(
                listOf(
                    "vertices:descriptor.refused row=gpu-renderer.vertices.descriptor routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=true materialized=false command=vertices:triangles descriptor=${plan.descriptorHash} reason=${case.expectedCode} mode=${plan.descriptor.primitiveMode.sourceLabel} vertexCount=${plan.descriptor.vertexCount} indexCount=${plan.descriptor.indexCount ?: "none"}",
                    "vertices:refusal facts=${plan.refusalFacts.entries.sortedBy { entry -> entry.key }.joinToString(",") { entry -> "${entry.key}=${entry.value}" }}",
                    "vertices:nonclaim drawVerticesSupport=false adapterBacked=false vertexBufferUpload=false indexBufferUpload=false primitiveBlenderSupport=false texcoordMaterialSupport=false meshSupport=false productActivation=true cpuRenderedTextureFallback=false",
                ),
                plan.dumpLines(),
            )
        }
    }

    @Test
    fun materialKeyFactsExcludeConcreteVerticesSourceIdentity() {
        val first = GPUVerticesRouteDecisionPlanner().plan(
            verticesRequest(
                descriptor = verticesDescriptor(
                    sourceKey = "sha256:vertices-source-a",
                    provenance = "gm:vertices-a",
                ),
            ),
        )
        val second = GPUVerticesRouteDecisionPlanner().plan(
            verticesRequest(
                descriptor = verticesDescriptor(
                    sourceKey = "sha256:vertices-source-b",
                    provenance = "gm:vertices-b",
                ),
            ),
        )

        assertNotEquals(first.descriptorHash, second.descriptorHash)
        assertEquals(first.materialKeyFacts, second.materialKeyFacts)
        assertFalse(first.materialKeyFacts.joinToString(",").contains("vertices-source-a"))
        assertFalse(second.materialKeyFacts.joinToString(",").contains("vertices-source-b"))
        assertFalse(first.dumpLines().single { it.startsWith("vertices:key") }.contains("gm:vertices-a"))
    }
}

private data class RefusalCase(
    val expectedCode: String,
    val descriptor: GPUVerticesDescriptor = verticesDescriptor(),
    val request: GPUVerticesRouteDecisionRequest? = null,
    val expectedFacts: Map<String, String> = emptyMap(),
)

private fun verticesRequest(
    descriptor: GPUVerticesDescriptor = verticesDescriptor(),
    wgslLayoutEvidenceLabel: String? = "vertices-wgsl-layout-v1",
): GPUVerticesRouteDecisionRequest =
    GPUVerticesRouteDecisionRequest(
        commandId = "vertices:triangles",
        descriptor = descriptor,
        materialKeyHash = "material:solid",
        targetFormatClass = "rgba8unorm",
        adapterEvidenceLabel = "wgpu-layout-evidence",
        wgslLayoutEvidenceLabel = wgslLayoutEvidenceLabel,
    )

private fun verticesDescriptor(
    primitiveMode: GPUVertexMode = GPUVertexMode.Triangles,
    vertexCount: Int = 3,
    indexCount: Int? = null,
    hasColors: Boolean = false,
    hasTexCoords: Boolean = false,
    boundsLabel: String = "local[0,0,16,16]",
    provenance: String = "unit-test",
    sourceKey: String = "sha256:vertices-source",
    positionFormat: String = "f32x2",
    colorFormat: String? = if (hasColors) "rgba8unorm-premul" else null,
    texCoordFormat: String? = if (hasTexCoords) "f32x2" else null,
    primitiveBlendMode: String = if (hasColors) "SrcOver" else "none",
    materialLocalCoordinatePolicy: String = if (hasTexCoords) "texcoord" else "position",
    finitePositions: Boolean = true,
    sourceMutable: Boolean = false,
): GPUVerticesDescriptor =
    GPUVerticesDescriptor(
        primitiveMode = primitiveMode,
        vertexCount = vertexCount,
        indexCount = indexCount,
        hasColors = hasColors,
        hasTexCoords = hasTexCoords,
        boundsLabel = boundsLabel,
        provenance = provenance,
        sourceKey = sourceKey,
        positionFormat = positionFormat,
        colorFormat = colorFormat,
        texCoordFormat = texCoordFormat,
        primitiveBlendMode = primitiveBlendMode,
        materialLocalCoordinatePolicy = materialLocalCoordinatePolicy,
        finitePositions = finitePositions,
        sourceMutable = sourceMutable,
    )
