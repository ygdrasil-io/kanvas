package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

class StencilCoverMaterializationPreimageTest {
    @Test
    fun `stencil cover gate derives pass materialization preimage`() {
        val gate = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilPreimageShape,
            path = stencilPreimagePath,
            evidence = completeStencilPreimageEvidence,
        )

        val preimage = gate.toStencilCoverPassMaterializationPreimage()

        assertEquals(true, preimage.accepted)
        assertFalse(preimage.nonClaims.adapterBacked)
        assertFalse(preimage.nonClaims.liveHandles)
        assertFalse(preimage.nonClaims.productRoute)
        assertEquals(listOf(GPUMaterializedResourceRole.StencilAttachment), preimage.resources.map { it.role })
        assertEquals(
            listOf(
                "resource-preimage:accepted plan=stencil-cover:path-triangle-v1 source=gpu-renderer.path.stencil-cover resources=pass-resource:stencil-cover:triangle:v1 bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:resource label=pass-resource:stencil-cover:triangle:v1 role=stencil-attachment generation=0 lifetime=pass-local descriptor=depth-stencil:Depth24PlusStencil8:adapter-test-device usage=render_attachment,stencil_attachment facts=clip=clip:device-rect:local[0,0,16,16];readback=readback:stencil-cover:triangle:v1;sampleCount=4;state=write-increment-cover-equal;target=target:offscreen-rgba8unorm-depth24plusstencil8",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }

    @Test
    fun `refused stencil cover gate derives refused materialization preimage`() {
        val gate = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilPreimageShape,
            path = stencilPreimagePath,
            evidence = completeStencilPreimageEvidence.copy(readbackEvidenceLabel = null),
        )

        val preimage = gate.toStencilCoverPassMaterializationPreimage()

        assertFalse(preimage.accepted)
        assertEquals("unsupported.execution.readback_unavailable", preimage.refusalCode)
        assertEquals(
            listOf(
                "resource-preimage:refused plan=stencil-cover:path-triangle-v1 source=gpu-renderer.path.stencil-cover reason=unsupported.execution.readback_unavailable resources=none bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }
}

private val stencilPreimageShape = GPUShapeDescriptor(
    shapeKind = "path-fill",
    boundsLabel = "local[0,0,16,16]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)

private val stencilPreimagePath = GPUPathDescriptor(
    pathKey = "path:triangle:v1",
    verbCount = 4,
    pointCount = 3,
    fillRule = "NonZero",
    inverseFill = false,
    finiteProof = "finite",
    volatility = "immutable",
    transformClass = "identity",
    edgeCount = 3,
)

private val completeStencilPreimageEvidence = GPUStencilCoverEvidence(
    adapterEvidenceLabel = "adapter:wgpu4k:test-device",
    depthStencilCapability = true,
    depthStencilEvidenceLabel = "depth-stencil:Depth24PlusStencil8:adapter-test-device",
    sampleCount = 4,
    sampleCountEvidenceLabel = "sample-count:4x:adapter-test-device",
    stencilStateLabel = "write-increment-cover-equal",
    producerBeforeCoverOrdering = true,
    passResourceEvidenceLabel = "pass-resource:stencil-cover:triangle:v1",
    readbackEvidenceLabel = "readback:stencil-cover:triangle:v1",
    targetStateLabel = "offscreen-rgba8unorm-depth24plusstencil8",
    targetEvidenceLabel = "target:offscreen-rgba8unorm-depth24plusstencil8",
    targetSupportsStencilCover = true,
    clipStateLabel = "clip:device-rect:local[0,0,16,16]",
    clipSupportsStencilCover = true,
)
