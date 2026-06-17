package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class StencilCoverGatePlannerTest {
    @Test
    fun `bounded path emits dumpable native stencil cover candidate when adapter evidence is linked`() {
        val plan = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilShape,
            path = stencilPath,
            evidence = completeStencilCoverEvidence,
        )

        val route = assertIs<GPUGeometryRoute.StencilCover>(plan.route)
        val stencilPlan = route.stencilPlan

        assertEquals("path-fill.stencil-producer", stencilPlan.stencilStepLabel)
        assertEquals("path-fill.cover-consumer", stencilPlan.coverStepLabel)
        assertEquals("Depth24PlusStencil8", stencilPlan.depthStencilFormat)
        assertEquals("depth-stencil:Depth24PlusStencil8:adapter-test-device", stencilPlan.depthStencilEvidenceLabel)
        assertEquals(4, stencilPlan.sampleCount)
        assertEquals("sample-count:4x:adapter-test-device", stencilPlan.sampleCountEvidenceLabel)
        assertEquals("atomic-group:path-stencil-cover:path_triangle_v1", stencilPlan.atomicGroupLabel)
        assertEquals("producer-before-cover", stencilPlan.orderingToken)
        assertEquals("target:offscreen-rgba8unorm-depth24plusstencil8", stencilPlan.targetEvidenceLabel)
        assertEquals("clip:device-rect:local[0,0,16,16]", stencilPlan.clipStateLabel)
        assertEquals("readback:stencil-cover:triangle:v1", stencilPlan.readbackEvidenceLabel)
        assertEquals(
            listOf(
                "geometry:stencil-cover.candidate row=gpu-renderer.path.stencil-cover routeKind=GPUNative classification=TargetNative promoted=false",
                "path:descriptor key=path:triangle:v1 verbs=4 points=3 fillRule=NonZero inverse=false transform=identity edges=3 finite=finite volatility=immutable",
                "stencil-cover:steps producer=path-fill.stencil-producer cover=path-fill.cover-consumer fillRule=NonZero msaa=true sampleCount=4 depthStencil=Depth24PlusStencil8 state=write-increment-cover-equal",
                "stencil-cover:ordering atomicGroup=atomic-group:path-stencil-cover:path_triangle_v1 token=producer-before-cover sortWindow=atomic-no-interleave",
                "stencil-cover:bounds producer=local[0,0,16,16] cover=local[0,0,16,16] clearLoadStore=clear-stencil-store-color-discard-stencil",
                "stencil-cover:clip state=clip:device-rect:local[0,0,16,16] supported=true",
                "stencil-cover:evidence adapter=adapter:wgpu4k:test-device depthStencil=depth-stencil:Depth24PlusStencil8:adapter-test-device samples=sample-count:4x:adapter-test-device target=target:offscreen-rgba8unorm-depth24plusstencil8 targetSupported=true passResources=pass-resource:stencil-cover:triangle:v1 readback=readback:stencil-cover:triangle:v1",
                "nonclaim:no-product-activation no-release-blocking-gate no-broad-path-aa no-graphite-port no-cpu-prepared-continuation-as-support",
            ),
            plan.dumpLines(),
        )
        assertFalse(plan.dumpLines().joinToString("\n").contains("productRouteActivated=true"))
    }

    @Test
    fun `missing native stencil cover evidence refuses with stable skipped diagnostics`() {
        val cases = listOf(
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_unavailable",
                evidence = completeStencilCoverEvidence.copy(depthStencilCapability = false),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_unavailable",
                evidence = completeStencilCoverEvidence.copy(depthStencilEvidenceLabel = null),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_unavailable",
                evidence = completeStencilCoverEvidence.copy(sampleCount = 0),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_unavailable",
                evidence = completeStencilCoverEvidence.copy(sampleCountEvidenceLabel = null),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_unavailable",
                evidence = completeStencilCoverEvidence.copy(stencilStateLabel = ""),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_target",
                evidence = completeStencilCoverEvidence.copy(targetSupportsStencilCover = false),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_target",
                evidence = completeStencilCoverEvidence.copy(targetEvidenceLabel = null),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.clip.stencil_cover",
                evidence = completeStencilCoverEvidence.copy(clipSupportsStencilCover = false),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.clip.stencil_cover",
                evidence = completeStencilCoverEvidence.copy(clipStateLabel = ""),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_ordering_illegal",
                evidence = completeStencilCoverEvidence.copy(producerBeforeCoverOrdering = false),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.execution.readback_unavailable",
                evidence = completeStencilCoverEvidence.copy(readbackEvidenceLabel = null),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_pass_resources_missing",
                evidence = completeStencilCoverEvidence.copy(passResourceEvidenceLabel = null),
            ),
            StencilCoverRefusalCase(
                expectedCode = "unsupported.geometry.path_fill_rule",
                path = stencilPath.copy(fillRule = "InverseWindingMaybe"),
            ),
        )

        for (case in cases) {
            val plan = GPUStencilCoverGatePlanner().plan(
                descriptor = stencilShape,
                path = case.path,
                evidence = case.evidence,
            )
            val route = assertIs<GPUGeometryRoute.Refused>(plan.route)

            assertEquals(case.expectedCode, route.diagnostic.code)
            assertContains(plan.diagnostics.map { it.code }, case.expectedCode)
            assertEquals(
                listOf(
                    "geometry:stencil-cover.refused row=gpu-renderer.path.stencil-cover classification=TargetNative routeKind=GPUNative reason=${case.expectedCode}",
                    "stencil-cover:skipped adapter=${case.evidence.adapterEvidenceLabel ?: "missing"} depthStencil=${case.evidence.depthStencilEvidenceLabel ?: "missing"} samples=${case.evidence.sampleCountEvidenceLabel ?: "missing"} target=${case.evidence.targetEvidenceLabel ?: "missing"} targetSupported=${case.evidence.targetSupportsStencilCover} stencilState=${case.evidence.stencilStateLabel.ifBlank { "missing" }} clip=${case.evidence.clipStateLabel.ifBlank { "missing" }} clipSupported=${case.evidence.clipSupportsStencilCover} passResources=${case.evidence.passResourceEvidenceLabel ?: "missing"} readback=${case.evidence.readbackEvidenceLabel ?: "missing"} ordering=${case.evidence.producerBeforeCoverOrdering}",
                    "nonclaim:no-native-stencil-cover-support no-product-activation no-release-blocking-gate no-cpu-prepared-continuation-as-support no-refusal-only-selector-as-support",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class StencilCoverRefusalCase(
    val expectedCode: String,
    val path: GPUPathDescriptor = stencilPath,
    val evidence: GPUStencilCoverEvidence = completeStencilCoverEvidence,
)

private val stencilShape = GPUShapeDescriptor(
    shapeKind = "path-fill",
    boundsLabel = "local[0,0,16,16]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)

private val stencilPath = GPUPathDescriptor(
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

private val completeStencilCoverEvidence = GPUStencilCoverEvidence(
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
