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
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilProducerGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilNativePathOrNull
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.sealGPUCorePrimitiveClipStencilNativeRoute
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot

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
        assertEquals(null, accepted.producer.scissor)
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
        val producerScissor = GPUPixelBounds(20, 10, 180, 90)
        val accepted = assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(
                request(inverse = true, producerScissor = producerScissor),
            ),
        )

        assertEquals(producerScissor, accepted.producer.scissor)
        assertEquals("clip-depth-0", accepted.attachment.logicalReference)
        assertEquals(200, accepted.attachment.width)
        assertEquals(100, accepted.attachment.height)
        assertEquals(0u, accepted.stencilReference)
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
    fun `pure seal and prepared candidate retain exact four sample clip stencil authority`() {
        val accepted = assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(
                request(sampleCount = 4, producerAntiAlias = true),
            ),
        )

        assertEquals(4, accepted.attachment.sampleCount)
        assertEquals(4, accepted.producer.structuralKey.sampleCount)
        assertTrue(accepted.consumers.all { it.structuralKey.sampleCount == 4 })
        assertEquals(4, mapped(accepted.producer.structuralKey).identity.sampleCount)

        val candidate = preparedCandidate(accepted, sampleCount = 4)
        assertEquals(4, candidate.attachmentSampleCount)
        listOf(0, 2, 8).forEach { unsupported ->
            assertFailsWith<IllegalArgumentException> {
                preparedCandidate(accepted, sampleCount = unsupported)
            }
        }
    }

    @Test
    fun `seal refuses unsupported bounded policy cases with stable codes`() {
        assertRefused("unsupported.native-core-primitive.clip-stencil.anti-alias", request(producerAntiAlias = true))
        assertRefused(
            "unsupported.native-core-primitive.clip-stencil.consumer-coverage",
            request(consumers = mutableListOf(consumer(coverageMode = GPUCorePrimitiveCoverageMode.StencilAA))),
        )
        assertRefused("unsupported.native-core-primitive.clip-stencil.msaa", request(sampleCount = 2))
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
            "invalid.native-core-primitive.clip-stencil.reference-authority",
            request(reference = 37u),
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
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.producer-scissor",
            request(producerScissor = GPUPixelBounds(0, 0, 201, 100)),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.producer-scissor",
            request(producerScissor = GPUPixelBounds(10, 10, 10, 20)),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.consumer-scissor",
            request(consumerScissor = GPUPixelBounds(0, 0, 200, 101)),
        )
        assertRefused(
            "invalid.native-core-primitive.clip-stencil.consumer-scissor",
            request(consumerScissor = GPUPixelBounds(10, 10, 20, 10)),
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

    @Test
    fun `non AA edge fan accepts more than 256 vertices and native classifier rejects nonzero reference`() {
        val vertices = (0..256).flatMap { index ->
            val angle = index.toDouble() * Math.PI * 2.0 / 257.0
            listOf(
                (100.0 + kotlin.math.cos(angle) * 80.0).toFloat(),
                (50.0 + kotlin.math.sin(angle) * 40.0).toFloat(),
            )
        }.toMutableList()
        assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(
                request(vertices = vertices),
            ),
        )
        assertEquals(null, stencilPlan(reference = 1u).corePrimitiveClipStencilNativePathOrNull())
    }

    @Test
    fun `prepared frame seal refuses consumers whose scope indices reverse frame order`() {
        val accepted = assertIs<GPUCorePrimitiveClipStencilNativeRoute.Accepted>(
            sealGPUCorePrimitiveClipStencilNativeRoute(
                request(
                    consumers = mutableListOf(
                        consumer(commandId = 7, sourceOrder = 2, last = false),
                        consumer(commandId = 9, sourceOrder = 5, last = true),
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            sealGPUCorePrimitiveClipStencilPreparedFrameRoute(
                route = accepted,
                producerFanVertices = accepted.producer.ndcVertices.take(6),
                producerFanIndices = listOf(0, 1, 2),
                slabAuthority = preparedSlabAuthority(),
                attachmentAuthority = GPUCorePrimitiveClipStencilPreparedAttachmentAuthority(
                    GPUFrameTextureRef("clip-depth-0"),
                    7L,
                ),
                producerSourceStepIndex = 1,
                producerPacketId = GPUDrawPacketID("packet.producer"),
                producerCommandId = 3,
                consumers = listOf(
                    GPUCorePrimitiveClipStencilPreparedConsumerLocation(
                        5,
                        GPUDrawPacketID("packet.consumer.7"),
                        7,
                        2,
                        null,
                    ),
                    GPUCorePrimitiveClipStencilPreparedConsumerLocation(
                        3,
                        GPUDrawPacketID("packet.consumer.9"),
                        9,
                        5,
                        "prepared-core-primitive.clip-stencil.consumer.9",
                    ),
                ),
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
        producerScissor: GPUPixelBounds? = null,
        consumerScissor: GPUPixelBounds? = null,
        producerGeometry: GPUCorePrimitiveClipStencilProducerGeometryAuthority =
            producerGeometry(vertices, contourStarts),
        consumers: MutableList<GPUCorePrimitiveClipStencilConsumerInput> = mutableListOf(
            consumer(
                reference = reference,
                inverse = inverse,
                scissor = consumerScissor,
                attachment = attachment(sampleCount),
            ),
        ),
        artifacts: MutableList<GPUClipExecutionPlan> = mutableListOf(
            stencilPlan(
                vertices,
                contourStarts,
                inverse,
                reference,
                fillRule,
                sampleCount,
                stencilBounds,
                producerScissor = producerScissor,
                consumerScissor = consumerScissor,
            ),
        ),
    ) = GPUCorePrimitiveClipStencilNativeRouteRequest(
        clipArtifacts = artifacts,
        consumers = consumers,
        producerGeometry = producerGeometry,
        producerAttachment = attachment(sampleCount),
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
        producerScissor: GPUPixelBounds? = null,
        consumerScissor: GPUPixelBounds? = null,
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
            scissor = producerScissor,
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
            scissor = consumerScissor,
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
        scissor: GPUPixelBounds? = null,
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
        scissor = scissor,
        attachment = attachment,
        isLastConsumer = last,
    )

    private fun attachment(sampleCount: Int = 1) = GPUCorePrimitiveClipStencilAttachmentAuthority(
        logicalReference = "clip-depth-0",
        width = 200,
        height = 100,
        format = GPUCorePrimitiveClipStencilAttachmentFormat.Depth24PlusStencil8,
        sampleCount = sampleCount,
        deviceGeneration = GPUDeviceGenerationID(4),
        resourceGeneration = 7L,
    )

    private fun preparedCandidate(
        route: GPUCorePrimitiveClipStencilNativeRoute.Accepted,
        sampleCount: Int,
    ) = GPUCorePrimitiveClipStencilPreparedCandidate(
        contentKey = route.producer.contentKey,
        planCanonicalIdentity = route.producer.planCanonicalIdentity,
        producerPacketId = GPUDrawPacketID("packet.producer"),
        producerCommandId = 1,
        producerNdcVertices = route.producer.ndcVertices,
        producerContourStarts = route.producer.contourStarts,
        producerFanVertices = route.producer.ndcVertices.take(6),
        producerFanIndices = listOf(0, 1, 2),
        producerStructuralKey = route.producer.structuralKey,
        consumers = route.consumers.mapIndexed { index, consumer ->
            GPUCorePrimitiveClipStencilPreparedCandidate.Consumer(
                packetId = GPUDrawPacketID("packet.consumer.$index"),
                commandId = consumer.commandId,
                sourceOrder = consumer.sourceOrder,
                structuralKey = consumer.structuralKey,
                dependencyFromPreviousConsumerToken = null,
            )
        },
        attachmentLogicalReference = route.attachment.logicalReference,
        attachmentWidth = route.attachment.width,
        attachmentHeight = route.attachment.height,
        attachmentSampleCount = sampleCount,
    )

    private fun preparedSlabAuthority(): GPUCorePrimitiveClipStencilPreparedSlabAuthority {
        val plan = GPUUniformSlabPlan(
            planHash = "test-plan",
            sourceLabel = "core-primitive-uniform-pass",
            deviceGeneration = 4L,
            alignmentBytes = 256L,
            totalBytes = 512L,
            uploadBudgetBytes = 512L,
            slots = listOf(
                GPUUniformSlabSlot("draw-7", "hash-7", 32L, 0L, 256L),
                GPUUniformSlabSlot("draw-9", "hash-9", 32L, 256L, 256L),
            ),
        )
        val seal = GPUCorePrimitiveUniformSlabSeal(
            plan,
            listOf(7, 9),
            ByteArray(512),
        )
        return GPUCorePrimitiveClipStencilPreparedSlabAuthority(
            GPUFrameBufferRef("vertices"),
            1L,
            88L,
            GPUFrameBufferRef("indices"),
            1L,
            60L,
            GPUFrameBufferRef("uniforms"),
            1L,
            512L,
            256L,
            seal,
        )
    }

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
