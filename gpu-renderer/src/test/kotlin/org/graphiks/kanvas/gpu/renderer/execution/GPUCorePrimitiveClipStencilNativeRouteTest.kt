package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilConsumerInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilNativeRouteRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilProducerGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.sealGPUCorePrimitiveClipStencilNativeRoute
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

class GPUCorePrimitiveClipStencilNativeRouteTest {
    @Test
    fun `pure seal converts asymmetric device coordinates to NDC and retains contours and hole`() {
        val vertices = mutableListOf(
            0f, 0f, 200f, 0f, 200f, 100f, 0f, 100f,
            50f, 25f, 50f, 75f, 150f, 75f, 150f, 25f,
        )
        val consumers = mutableListOf(
            consumer(commandId = 7, sourceOrder = 2, last = false),
            consumer(commandId = 9, sourceOrder = 5, last = true),
        )
        val request = request(vertices = vertices, contourStarts = listOf(0, 4), consumers = consumers)

        val accepted = assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(request),
        )
        assertEquals(
            listOf(
                -1f, 1f, 1f, 1f, 1f, -1f, -1f, -1f,
                -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            ),
            accepted.producer.ndcVertices,
        )
        assertEquals(listOf(0, 4), accepted.producer.contourStarts)
        assertEquals("clip-0", accepted.producer.contentKey)
        assertEquals(request.clipArtifacts.single().canonicalIdentity(), accepted.producer.planCanonicalIdentity)
        assertEquals(listOf(7, 9), accepted.consumers.map { it.commandId })
        assertEquals(9, accepted.lastConsumerCommandId)
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
            mapped(accepted.producer.structuralKey).identity.program,
        )
        assertTrue(accepted.consumers.all {
            mapped(it.structuralKey).identity.program ==
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular
        })

        vertices.fill(999f)
        consumers.clear()
        assertEquals(-1f, accepted.producer.ndcVertices.first())
        assertEquals(listOf(0, 4), accepted.producer.contourStarts)
        assertEquals(listOf(7, 9), accepted.consumers.map { it.commandId })
    }

    @Test
    fun `seal retains exact attachment atomic ordering reference and structural keys`() {
        val accepted = assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(request(inverse = true, reference = 37u)),
        )

        assertEquals("clip-depth-0", accepted.attachment.logicalReference)
        assertEquals(200, accepted.attachment.width)
        assertEquals(100, accepted.attachment.height)
        assertEquals(37u, accepted.stencilReference)
        assertEquals(GPUClipAtomicGroupID("atomic-0"), accepted.atomicGroup)
        assertEquals(GPUClipOrderingToken("order-0"), accepted.orderingToken)
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse,
            mapped(accepted.consumers.single().structuralKey).identity.program,
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveBindingPolicy.NoBindings,
            mapped(accepted.producer.structuralKey).componentIdentity.bindingPolicy,
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveBindingPolicy.DynamicUniformRequired,
            mapped(accepted.consumers.single().structuralKey).componentIdentity.bindingPolicy,
        )
    }

    @Test
    fun `attachment authority accepts contained partial clip bounds and refuses out of target bounds`() {
        val partialVertices = mutableListOf(20f, 10f, 120f, 10f, 70f, 60f)
        assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(
                request(
                    vertices = partialVertices,
                    stencilBounds = GPUPixelBounds(20, 10, 100, 50),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            GPUPixelBounds(-1, 0, 100, 50)
        }
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.attachment-authority",
            request(stencilBounds = GPUPixelBounds(150, 60, 210, 110)),
        )
    }

    @Test
    fun `seal accepts even odd producer and direct triangles inverse consumer`() {
        val accepted = assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(
                request(
                    inverse = true,
                    fillRule = GPUClipFillRule.EvenOdd,
                    consumers = mutableListOf(
                        consumer(inverse = true, geometry = directTrianglesConsumer()),
                    ),
                ),
            ),
        )

        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd,
            mapped(accepted.producer.structuralKey).identity.program,
        )
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse,
            mapped(accepted.consumers.single().structuralKey).identity.program,
        )
    }

    @Test
    fun `seal refuses unsupported bounded policy cases with stable codes`() {
        assertRefused("unsupported.native-core-primitive.clip-stencil.anti-alias", request(producerAntiAlias = true))
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.consumer-coverage",
            request(consumers = mutableListOf(consumer(coverageMode = GPUCorePrimitiveCoverageMode.StencilAA))),
        )
        assertRefused("unsupported.native-core-primitive.clip-stencil.msaa", request(sampleCount = 4))
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.consumer-geometry",
            request(consumers = mutableListOf(consumer(geometry = stencilPathConsumer()))),
        )
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.mask",
            request(artifacts = mutableListOf(maskPlan())),
        )
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.destination-read",
            request(consumers = mutableListOf(consumer(blendPlan = destinationReadBlend()))),
        )
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.blend",
            request(consumers = mutableListOf(consumer(blendPlan = srcBlendPlan()))),
        )
        val plan = stencilPlan()
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.multiple-artifacts",
            request(artifacts = mutableListOf(plan, plan.copy(contentKey = "clip-1"))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.producer-geometry",
            request(artifacts = mutableListOf(stencilPlan(producerGeometry = GPUClipExecutionGeometry.Rect(
                GPUBounds(0f, 0f, 10f, 10f),
            ), producerOperation = GPUClipStencilOperation.Replace))),
        )
    }

    @Test
    fun `seal refuses broken atomic ordering last consumer attachment and producer state authority`() {
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.ordering",
            request(consumers = mutableListOf(consumer(1, 2), consumer(2, 1, last = true))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.last-consumer",
            request(consumers = mutableListOf(consumer(last = false))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.atomic-authority",
            request(consumers = mutableListOf(consumer(atomicGroup = GPUClipAtomicGroupID("other")))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.attachment-authority",
            request(consumers = mutableListOf(consumer(attachment = attachment().copy(resourceGeneration = 8L)))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.producer-state",
            request(artifacts = mutableListOf(stencilPlan(producerOperation = GPUClipStencilOperation.Replace))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.ordering",
            request(consumers = mutableListOf(consumer(commandId = 1, sourceOrder = -1))),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.ordering",
            request(consumers = mutableListOf(
                consumer(commandId = 1, sourceOrder = 0, last = false),
                consumer(commandId = 1, sourceOrder = 1),
            )),
        )
    }

    @Test
    fun `seal refuses malformed raw producer vertices and contours`() {
        val invalidVertices = listOf(
            emptyList(),
            listOf(0f, 0f, 1f, 0f, 0f),
            listOf(0f, 0f, Float.NaN, 0f, 0f, 1f),
            listOf(0f, 0f, Float.POSITIVE_INFINITY, 0f, 0f, 1f),
        )
        invalidVertices.forEach { vertices ->
            assertRefused(
                "invalid.native-core-primitive.clip-stencil.producer-geometry",
                request(producerGeometry = producerGeometry(vertices, listOf(0))),
            )
        }

        val sixVertices = listOf(0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f, 1f, 1f, 1f, 0f)
        listOf(
            emptyList(),
            listOf(1),
            listOf(0, 3, 3),
            listOf(0, 7),
            listOf(0, 2),
        ).forEach { contourStarts ->
            assertRefused(
                "invalid.native-core-primitive.clip-stencil.producer-geometry",
                request(producerGeometry = producerGeometry(sixVertices, contourStarts)),
            )
        }
    }

    private fun assertRefused(code: String, request: GPUCorePrimitiveClipStencilNativeRouteRequest) {
        assertEquals(
            code,
            assertIs<GPUCorePrimitiveClipStencilNativeRoute.Refused>(
                sealGPUCorePrimitiveClipStencilNativeRoute(request),
            ).code,
        )
    }

    private fun mapped(
        structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    ) = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
        mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey),
    )

    private fun request(
        vertices: MutableList<Float> = mutableListOf(0f, 0f, 200f, 0f, 100f, 100f),
        contourStarts: List<Int> = listOf(0),
        inverse: Boolean = false,
        fillRule: GPUClipFillRule = GPUClipFillRule.Winding,
        reference: UInt = 0u,
        producerAntiAlias: Boolean = false,
        sampleCount: Int = 1,
        stencilBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 200, 100),
        producerGeometry: GPUCorePrimitiveClipStencilProducerGeometryAuthority =
            producerGeometry(vertices, contourStarts),
        consumers: MutableList<GPUCorePrimitiveClipStencilConsumerInput> = mutableListOf(
            consumer(reference = reference, inverse = inverse),
        ),
        artifacts: MutableList<GPUClipExecutionPlan> = mutableListOf(
            stencilPlan(vertices, contourStarts, inverse, reference, fillRule, sampleCount, stencilBounds),
        ),
    ) = GPUCorePrimitiveClipStencilNativeRouteRequest(
        clipArtifacts = artifacts,
        consumers = consumers,
        producerGeometry = producerGeometry,
        producerAttachment = attachment(),
        producerAntiAlias = producerAntiAlias,
        expectedLastConsumerCommandId = consumers.lastOrNull()?.commandId ?: -1,
    )

    private fun producerGeometry(vertices: List<Float>, contourStarts: List<Int>) =
        GPUCorePrimitiveClipStencilProducerGeometryAuthority(vertices, contourStarts)

    private fun stencilPlan(
        vertices: List<Float> = listOf(0f, 0f, 200f, 0f, 100f, 100f),
        contourStarts: List<Int> = listOf(0),
        inverse: Boolean = false,
        reference: UInt = 0u,
        fillRule: GPUClipFillRule = GPUClipFillRule.Winding,
        sampleCount: Int = 1,
        bounds: GPUPixelBounds = GPUPixelBounds(0, 0, 200, 100),
        producerGeometry: GPUClipExecutionGeometry = GPUClipExecutionGeometry.Path(
            vertices,
            contourStarts,
            fillRule,
            inverse,
        ),
        producerOperation: GPUClipStencilOperation = if (fillRule == GPUClipFillRule.Winding) {
            GPUClipStencilOperation.IncrementWrap
        } else {
            GPUClipStencilOperation.Invert
        },
    ) = GPUClipExecutionPlan.StencilCoverage(
        contentKey = "clip-0",
        bounds = bounds,
        sampleCount = sampleCount,
        atomicGroup = GPUClipAtomicGroupID("atomic-0"),
        orderingToken = GPUClipOrderingToken("order-0"),
        producer = GPUClipStencilProducerPlan(
            geometry = producerGeometry,
            scissor = null,
            fillRule = fillRule,
            reference = reference,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = producerOperation,
            backPassOperation = if (fillRule == GPUClipFillRule.Winding &&
                producerOperation == GPUClipStencilOperation.IncrementWrap
            ) GPUClipStencilOperation.DecrementWrap else producerOperation,
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        ),
        consumer = GPUClipStencilConsumerPlan(
            scissor = null,
            reference = reference,
            compare = if (inverse) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual,
        ),
    )

    private fun consumer(
        commandId: Int = 3,
        sourceOrder: Int = 0,
        last: Boolean = true,
        reference: UInt = 0u,
        inverse: Boolean = false,
        geometry: GPUCorePrimitiveGeometry = GPUCorePrimitiveGeometry.Rect(0f, 0f, 40f, 20f),
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
        blendPlan: GPUBlendPlan = srcOverBlendPlan(),
        atomicGroup: GPUClipAtomicGroupID = GPUClipAtomicGroupID("atomic-0"),
        attachment: GPUCorePrimitiveClipStencilAttachmentAuthority = attachment(),
    ) = GPUCorePrimitiveClipStencilConsumerInput(
        commandId = commandId,
        sourceOrder = sourceOrder,
        geometry = geometry,
        coverageMode = coverageMode,
        blendPlan = blendPlan,
        inverseFill = inverse,
        stencilReference = reference,
        atomicGroup = atomicGroup,
        orderingToken = GPUClipOrderingToken("order-0"),
        scissor = null,
        attachment = attachment,
        isLastConsumer = last,
    )

    private fun attachment() = GPUCorePrimitiveClipStencilAttachmentAuthority(
        logicalReference = "clip-depth-0",
        width = 200,
        height = 100,
        format = GPUCorePrimitiveClipStencilAttachmentFormat.Depth24PlusStencil8,
        sampleCount = 1,
        deviceGeneration = GPUDeviceGenerationID(4),
        resourceGeneration = 7L,
    )

    private fun maskPlan() = GPUClipExecutionPlan.CoverageMask(
        contentKey = "mask",
        bounds = GPUPixelBounds(0, 0, 200, 100),
        sampleCount = 1,
        depthStencilRequired = true,
        orderingToken = GPUClipOrderingToken("order-0"),
        producers = listOf(
            GPUClipMaskProducerPlan(
                sourceOrder = 0,
                geometry = GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 10f, 10f)),
                combine = GPUClipMaskCombine.Intersect,
                antiAlias = false,
            ),
        ),
        consumer = GPUClipMaskConsumerPlan(),
    )

    private fun stencilPathConsumer() = GPUCorePrimitiveGeometry.TriangulatedPath(
        vertices = listOf(0f, 0f, 10f, 0f, 0f, 10f),
        indices = listOf(0, 1, 2),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 3,
        coverBounds = GPUPixelBounds(0, 0, 10, 10),
        geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
        strokeStyle = null,
    )

    private fun directTrianglesConsumer() = GPUCorePrimitiveGeometry.TriangulatedPath(
        vertices = listOf(0f, 0f, 10f, 0f, 0f, 10f),
        indices = listOf(0, 1, 2),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 3,
        coverBounds = GPUPixelBounds(0, 0, 10, 10),
        geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
        strokeStyle = null,
    )

    private fun srcOverBlendPlan() = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = fixedState("src-over", "one", "one-minus-src-alpha"),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun srcBlendPlan() = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC,
        state = fixedState("src", "one", "zero"),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun destinationReadBlend() = GPUBlendPlan.ShaderBlendWithDstRead(
        mode = GPUBlendMode.MULTIPLY,
        formulaId = "multiply",
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun fixedState(id: String, source: String, destination: String) = GPUFixedFunctionBlendState(
        stateId = id,
        color = GPUFixedFunctionBlendComponent(source, destination, "add"),
        alpha = GPUFixedFunctionBlendComponent(source, destination, "add"),
        writeMask = "rgba",
    )
}
