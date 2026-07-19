package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeStyle
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

class GPUCorePrimitiveCoverageMaskPreparedRouteTest {
    @Test
    fun `snapshot and seal accept one full-target ordered color-only mask with two core consumers`() {
        val request = validRequest()

        val candidate = assertIs<GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Accepted>(
            snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(request),
        ).candidate
        val route = assertIs<GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted>(
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(candidate, request),
        )

        assertEquals("mask.main", route.contentKey)
        assertEquals(GPUPixelBounds(0, 0, 64, 48), route.bounds)
        assertEquals(request.plan.canonicalIdentity(), route.planCanonicalIdentity)
        assertEquals(listOf(0, 1), route.producers.map { it.sourceOrder })
        assertEquals(
            listOf(GPUClipMaskCombine.Intersect, GPUClipMaskCombine.Difference),
            route.producers.map { it.combine },
        )
        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectDifference,
            route.producers[1].structuralKey.coverageMaskStructuralProgramOrNull(),
        )
        assertEquals(listOf(10, 11), route.consumers.map { it.commandId })
        assertIs<GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.Rect>(
            route.consumers.first().geometry,
        )
        assertEquals(request.attachment, route.attachment)
        assertEquals(GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest,
            route.consumers.first().structuralKey.coverageMaskStructuralProgramOrNull())
        assertNotEquals(route.producers.first().structuralKey, route.consumers.first().structuralKey)
    }

    @Test
    fun `all-zero rrect producers canonicalize to rect programs for both combines`() {
        listOf(
            GPUClipMaskCombine.Intersect to
                GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectIntersect,
            GPUClipMaskCombine.Difference to
                GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectDifference,
        ).forEach { (combine, expectedProgram) ->
            val request = validRequest(plan = validPlan(producers = listOf(producer(
                sourceOrder = 0,
                geometry = GPUClipExecutionGeometry.RRect(
                    GPUBounds(0f, 0f, 64f, 48f),
                    List(8) { 0f },
                ),
                combine = combine,
            ))))

            val key = acceptedCandidate(request).producers.single().structuralKey

            assertEquals(
                corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                    combine,
                ),
                key,
            )
            assertEquals(expectedProgram, key.coverageMaskStructuralProgramOrNull())
        }
    }

    @Test
    fun `rrect producer epsilon boundary matches the native shader`() {
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.rrect-sub-epsilon-radii",
            validRequest(plan = validPlan(producers = listOf(producer(
                sourceOrder = 0,
                geometry = GPUClipExecutionGeometry.RRect(
                    GPUBounds(0f, 0f, 64f, 48f),
                    List(8) { 0.00001f },
                ),
            )))),
        )

        val accepted = acceptedCandidate(validRequest(plan = validPlan(producers = listOf(producer(
            sourceOrder = 0,
            geometry = GPUClipExecutionGeometry.RRect(
                GPUBounds(0f, 0f, 64f, 48f),
                List(8) { 0.0001f },
            ),
        )))))

        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectIntersect,
            accepted.producers.single().structuralKey.coverageMaskStructuralProgramOrNull(),
        )
    }

    @Test
    fun `snapshot requires shading packets with direct non analytic core geometry`() {
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-role",
            validRequest(consumers = listOf(
                consumer(10, 1, packetRole = GPUDrawPacketRole.StencilConsumer),
                consumer(11, 2),
            )),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-geometry",
            validRequest(consumers = listOf(
                consumer(10, 1, geometry = GPUCorePrimitiveGeometry.RRect(
                    0f, 0f, 32f, 24f, List(8) { 4f },
                )),
                consumer(11, 2),
            )),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-geometry",
            validRequest(consumers = listOf(
                consumer(10, 1, geometry = directPath(inverseFill = true)),
                consumer(11, 2),
            )),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-geometry",
            validRequest(consumers = listOf(
                consumer(10, 1, geometry = directPath(strokeStyle = GPUCorePrimitiveStrokeStyle(
                    width = 1f,
                    cap = "butt",
                    join = "miter",
                    miterLimit = 4f,
                    dashIntervals = emptyList(),
                    dashPhase = 0f,
                    loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1,
                ))),
                consumer(11, 2),
            )),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-geometry",
            validRequest(consumers = listOf(
                consumer(10, 1, geometry = directPath(
                    GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                )),
                consumer(11, 2),
            )),
        )
    }

    @Test
    fun `producer geometry values and consumer invert stay outside structural keys`() {
        val firstRequest = validRequest()
        val secondRequest = validRequest(
            plan = validPlan(
                producers = listOf(
                    producer(0, GPUClipExecutionGeometry.Rect(GPUBounds(2f, 3f, 52f, 44f))),
                    producer(
                        1,
                        GPUClipExecutionGeometry.RRect(
                            GPUBounds(8f, 7f, 60f, 46f),
                            listOf(6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f),
                        ),
                        GPUClipMaskCombine.Difference,
                    ),
                ),
                invert = true,
            ),
        )

        val first = acceptedCandidate(firstRequest)
        val second = acceptedCandidate(secondRequest)

        assertEquals(first.producers.map { it.structuralKey }, second.producers.map { it.structuralKey })
        assertEquals(first.consumers.map { it.structuralKey }, second.consumers.map { it.structuralKey })
        assertNotEquals(first.planCanonicalIdentity, second.planCanonicalIdentity)
    }

    @Test
    fun `snapshot returns stable refusals for unsupported producer and attachment facts`() {
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.producer-path",
            validRequest(
                plan = validPlan(producers = listOf(
                    producer(
                        0,
                        GPUClipExecutionGeometry.Path(
                            vertices = listOf(0f, 0f, 32f, 0f, 32f, 32f),
                            contourStarts = listOf(0),
                            fillRule = GPUClipFillRule.Winding,
                            inverseFill = false,
                        ),
                    ),
                )),
            ),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.depth-stencil",
            validRequest(plan = validPlan(depthStencilRequired = true)),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.msaa",
            validRequest(
                plan = validPlan(sampleCount = 4),
                attachment = attachment(sampleCount = 4),
            ),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.anti-alias",
            validRequest(plan = validPlan(producers = listOf(producer(0, antiAlias = true)))),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.rrect-radii",
            validRequest(plan = validPlan(producers = listOf(producer(
                0,
                GPUClipExecutionGeometry.RRect(
                    GPUBounds(0f, 0f, 100f, 40f),
                    listOf(80f, 20f, 20f, 20f, 20f, 20f, 20f, 20f),
                ),
            )))),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.rrect-mixed-zero-radii",
            validRequest(plan = validPlan(producers = listOf(producer(
                0,
                GPUClipExecutionGeometry.RRect(
                    GPUBounds(0f, 0f, 100f, 40f),
                    listOf(0f, 0f, 20f, 20f, 20f, 20f, 20f, 20f),
                ),
            )))),
        )
        assertRefused(
            "invalid.prepared-core-primitive.coverage-mask.full-target",
            validRequest(plan = validPlan(bounds = GPUPixelBounds(1, 0, 64, 48))),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.target-format",
            validRequest(attachment = attachment(
                format = GPUCorePrimitiveCoverageMaskAttachmentFormat.Bgra8Unorm,
            )),
        )
    }

    @Test
    fun `snapshot refuses wrong consumer order and ordering token`() {
        assertRefused(
            "invalid.prepared-core-primitive.coverage-mask.ordering",
            validRequest(consumers = listOf(consumer(10, 2), consumer(11, 1))),
        )
        assertRefused(
            "invalid.prepared-core-primitive.coverage-mask.ordering-authority",
            validRequest(consumers = listOf(
                consumer(10, 1),
                consumer(11, 2, orderingToken = GPUClipOrderingToken("order.substituted")),
            )),
        )
    }

    @Test
    fun `snapshot refuses fewer than two core consumers and unsupported coverage`() {
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-count",
            validRequest(consumers = listOf(consumer(10, 1))),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-coverage",
            validRequest(consumers = listOf(
                consumer(10, 1, coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA),
                consumer(11, 2),
            )),
        )
    }

    @Test
    fun `snapshot refuses destination read and non canonical consumer blend`() {
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.destination-read",
            validRequest(consumers = listOf(
                consumer(10, 1, blendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
                    mode = GPUBlendMode.MULTIPLY,
                    formulaId = "multiply",
                    sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
                )),
                consumer(11, 2),
            )),
        )
        assertRefused(
            "unsupported.prepared-core-primitive.coverage-mask.blend",
            validRequest(consumers = listOf(
                consumer(10, 1, blendPlan = fixedBlend(GPUBlendMode.SRC, "src")),
                consumer(11, 2),
            )),
        )
    }

    @Test
    fun `seal refuses substituted snapshots and stale attachment generations`() {
        val request = validRequest()
        val candidate = acceptedCandidate(request)

        val substituted = request.copy(
            consumers = request.consumers.mapIndexed { index, consumer ->
                if (index == 0) consumer.copy(semanticCanonicalIdentity = "semantic.substituted") else consumer
            },
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.substituted",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(candidate, substituted),
        )

        val substitutedTopology = GPUCorePrimitiveCoverageMaskPreparedCandidate(
            contentKey = candidate.contentKey,
            planCanonicalIdentity = candidate.planCanonicalIdentity,
            bounds = candidate.bounds,
            orderingToken = candidate.orderingToken,
            producers = candidate.producers,
            consumers = candidate.consumers.mapIndexed { index, consumer ->
                if (index == 0) consumer.copy(
                    structuralKey = consumer.structuralKey.copy(
                        role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading,
                        topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticRRect,
                        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
                    ),
                ) else consumer
            },
            attachment = candidate.attachment,
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.substituted",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(substitutedTopology, request),
        )

        val stale = request.copy(
            attachment = request.attachment.copy(resourceGeneration =
                request.attachment.resourceGeneration + 1L),
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.stale-authority",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(candidate, stale),
        )

        val staleDevice = request.copy(
            attachment = request.attachment.copy(deviceGeneration = GPUDeviceGenerationID(8)),
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.stale-authority",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(candidate, staleDevice),
        )

        assertRouteRefused(
            "unsupported.prepared-core-primitive.coverage-mask.target-format",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(attachment = request.attachment.copy(
                    format = GPUCorePrimitiveCoverageMaskAttachmentFormat.Bgra8Unorm,
                )),
            ),
        )
        assertRouteRefused(
            "unsupported.prepared-core-primitive.coverage-mask.msaa",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(
                    plan = validPlan(sampleCount = 4),
                    attachment = request.attachment.copy(sampleCount = 4),
                ),
            ),
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.substituted",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(consumers = request.consumers.mapIndexed { index, consumer ->
                    if (index == 0) consumer.copy(geometry = directPath()) else consumer
                }),
            ),
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.ordering",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(consumers = request.consumers.reversed()),
            ),
        )
        assertRouteRefused(
            "invalid.prepared-core-primitive.coverage-mask.ordering-authority",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(consumers = request.consumers.mapIndexed { index, consumer ->
                    if (index == 0) consumer.copy(
                        orderingToken = GPUClipOrderingToken("order.substituted"),
                    ) else consumer
                }),
            ),
        )
        assertRouteRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-coverage",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(consumers = request.consumers.mapIndexed { index, consumer ->
                    if (index == 0) consumer.copy(
                        coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
                    ) else consumer
                }),
            ),
        )
        assertRouteRefused(
            "unsupported.prepared-core-primitive.coverage-mask.blend",
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(
                candidate,
                request.copy(consumers = request.consumers.mapIndexed { index, consumer ->
                    if (index == 0) consumer.copy(
                        blendPlan = fixedBlend(GPUBlendMode.SRC, "src"),
                    ) else consumer
                }),
            ),
        )
    }

    @Test
    fun `request and candidate snapshots cannot be rewritten through input aliases`() {
        val consumers = mutableListOf(consumer(10, 1), consumer(11, 2))
        val radii = MutableList(8) { 5f }
        val plan = validPlan(producers = listOf(
            producer(0),
            producer(
                1,
                GPUClipExecutionGeometry.RRect(GPUBounds(4f, 4f, 60f, 44f), radii),
                GPUClipMaskCombine.Difference,
            ),
        ))
        val request = validRequest(plan = plan, consumers = consumers)
        val candidate = acceptedCandidate(request)

        consumers.clear()
        radii.fill(99f)

        assertEquals(2, request.consumers.size)
        assertEquals(2, candidate.consumers.size)
        assertEquals(
            List(8) { 5f },
            (candidate.producers[1].geometry as GPUClipExecutionGeometry.RRect).radii,
        )
        assertIs<GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted>(
            sealGPUCorePrimitiveCoverageMaskPreparedRoute(candidate, request),
        )
    }

    private fun validRequest(
        plan: GPUClipExecutionPlan.CoverageMask = validPlan(),
        consumers: List<GPUCorePrimitiveCoverageMaskConsumerInput> = listOf(
            consumer(10, 1),
            consumer(11, 2),
        ),
        attachment: GPUCorePrimitiveCoverageMaskAttachmentAuthority = attachment(),
    ) = GPUCorePrimitiveCoverageMaskPreparedRouteRequest(plan, consumers, attachment)

    private fun validPlan(
        bounds: GPUPixelBounds = GPUPixelBounds(0, 0, 64, 48),
        sampleCount: Int = 1,
        depthStencilRequired: Boolean = false,
        producers: List<GPUClipMaskProducerPlan> = listOf(
            producer(0),
            producer(
                1,
                GPUClipExecutionGeometry.RRect(
                    GPUBounds(4f, 4f, 60f, 44f),
                    List(8) { 5f },
                ),
                GPUClipMaskCombine.Difference,
            ),
        ),
        invert: Boolean = false,
    ) = GPUClipExecutionPlan.CoverageMask(
        contentKey = "mask.main",
        bounds = bounds,
        sampleCount = sampleCount,
        depthStencilRequired = depthStencilRequired,
        orderingToken = GPUClipOrderingToken("order.main"),
        producers = producers,
        consumer = GPUClipMaskConsumerPlan(invert = invert),
    )

    private fun producer(
        sourceOrder: Int,
        geometry: GPUClipExecutionGeometry = GPUClipExecutionGeometry.Rect(
            GPUBounds(0f, 0f, 64f, 48f),
        ),
        combine: GPUClipMaskCombine = GPUClipMaskCombine.Intersect,
        antiAlias: Boolean = false,
    ) = GPUClipMaskProducerPlan(sourceOrder, geometry, combine, antiAlias)

    private fun consumer(
        commandId: Int,
        sourceOrder: Int,
        orderingToken: GPUClipOrderingToken = GPUClipOrderingToken("order.main"),
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
        blendPlan: GPUBlendPlan = fixedBlend(GPUBlendMode.SRC_OVER, "src-over"),
        packetRole: GPUDrawPacketRole = GPUDrawPacketRole.Shading,
        geometry: GPUCorePrimitiveGeometry = GPUCorePrimitiveGeometry.Rect(0f, 0f, 32f, 24f),
    ) = GPUCorePrimitiveCoverageMaskConsumerInput(
        packetId = GPUDrawPacketID("packet.$commandId"),
        commandId = commandId,
        sourceOrder = sourceOrder,
        semanticCanonicalIdentity = "semantic.$commandId",
        coverageMode = coverageMode,
        blendPlan = blendPlan,
        orderingToken = orderingToken,
        packetRole = packetRole,
        geometry = geometry,
    )

    private fun directPath(
        mode: GPUCorePrimitiveGeometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        inverseFill: Boolean = false,
        strokeStyle: GPUCorePrimitiveStrokeStyle? = null,
    ) = GPUCorePrimitiveGeometry.TriangulatedPath(
        vertices = listOf(0f, 0f, 32f, 0f, 32f, 24f),
        indices = listOf(0, 1, 2),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 3,
        coverBounds = GPUPixelBounds(0, 0, 32, 24),
        geometryMode = mode,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = inverseFill,
        strokeStyle = strokeStyle,
    )

    private fun attachment(
        format: GPUCorePrimitiveCoverageMaskAttachmentFormat =
            GPUCorePrimitiveCoverageMaskAttachmentFormat.Rgba8Unorm,
        sampleCount: Int = 1,
    ) = GPUCorePrimitiveCoverageMaskAttachmentAuthority(
        logicalReference = "coverage-mask.main",
        width = 64,
        height = 48,
        format = format,
        sampleCount = sampleCount,
        deviceGeneration = GPUDeviceGenerationID(7),
        resourceGeneration = 9,
    )

    private fun fixedBlend(mode: GPUBlendMode, stateId: String) = GPUBlendPlan.FixedFunctionBlend(
        mode = mode,
        state = GPUFixedFunctionBlendState(
            stateId = stateId,
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun acceptedCandidate(request: GPUCorePrimitiveCoverageMaskPreparedRouteRequest) =
        assertIs<GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Accepted>(
            snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(request),
        ).candidate

    private fun assertRefused(
        code: String,
        request: GPUCorePrimitiveCoverageMaskPreparedRouteRequest,
    ) = assertEquals(
        code,
        assertIs<GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Refused>(
            snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(request),
        ).code,
    )

    private fun assertRouteRefused(
        code: String,
        route: GPUCorePrimitiveCoverageMaskPreparedRoute,
    ) = assertEquals(code, assertIs<GPUCorePrimitiveCoverageMaskPreparedRoute.Refused>(route).code)
}
