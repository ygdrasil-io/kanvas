package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.filters.MAX_MASK_BLUR_TAPS
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurRequest
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurLocalGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE_DST
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_MASK
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_STYLE
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.topLevelMaskBlurCompositeClipRefusal
import org.graphiks.kanvas.gpu.renderer.recording.topLevelMaskBlurScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/**
 * Headless materializer coverage for the prepared top-level mask blur lane (Task 11).
 *
 * The fixture mirrors [GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest]'s conventions
 * (NativeProxy fake device/queue, [GPUWgpu4kPreparedSceneTarget] via the setup
 * transaction, real [GPUWgpu4kMaskBlurSessionCache] + [GPUWgpu4kCorePrimitiveSessionCache]
 * over the fake device, hand-built frame plans). The refusal paths run before any device
 * use, so the refusal matrix asserts zero native events and zero uniform uploads.
 *
 * Reachability notes: `step-identity` is unreachable because the stage of every mask
 * blur packet is DERIVED from its render step id (`maskBlurStageFromRenderStepId`), so a
 * substituted step id either fails the stage lookup (`stage`) or breaks the closed chain
 * shape (`chain-shape`) first. `scene-bounds` is likewise unreachable because the scene
 * preparation descriptor feeds both `resolveSceneFormat` and `sceneTargetBounds`, and a
 * non-texture descriptor trips `scene-format` first.
 */
class GPUWgpu4kMaskBlurFramePayloadMaterializerTest {

    private companion object {
        const val TARGET_WIDTH = 32
        const val TARGET_HEIGHT = 32
        val TARGET_BOUNDS = GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT)
        val SCENE_TARGET = GPUFrameTargetRef("frame.scene")
        val CLEAR_LOAD_STORE = GPULoadStorePlan("clear", GPUStorePlan.Store)
        val LOAD_LOAD_STORE = GPULoadStorePlan("load", GPUStorePlan.Store)
        val GENERATION = GPUDeviceGenerationID(23L)
        val FRAME_ID = GPUFrameID(231L)
    }

    @Test
    fun `valid chain materializes five ordered render operands with exact local authorities`() {
        val fixture = fixture()
        val materialized = fixture.materialize()

        val payload = materialized.draft.payload
        val renders = payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertEquals(5, renders.size)
        assertEquals((1..5).toList(), renders.map { it.sourceStepIndex })
        assertEquals(
            listOf(
                GPUPreparedNativeLoadOperation.Clear,
                GPUPreparedNativeLoadOperation.Clear,
                GPUPreparedNativeLoadOperation.Clear,
                GPUPreparedNativeLoadOperation.Clear,
                GPUPreparedNativeLoadOperation.Load,
            ),
            renders.map { it.pass.loadOperation },
        )
        // Every local stage draws the fullscreen triangle inside the local scissor;
        // the composite scissor is the semantic scissor authority.
        renders.dropLast(1).forEach { render ->
            val scissor = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>().single()
            assertEquals(
                GPUPixelBounds(0, 0, 9, 9),
                GPUPixelBounds(scissor.x, scissor.y, scissor.x + scissor.width, scissor.y + scissor.height),
            )
            assertEquals(1, render.commands.count { it is GPUPreparedNativeRenderCommand.Draw })
            assertTrue(render.pass.depthStencilTarget == null)
        }
        val composite = renders.last()
        assertEquals(
            GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
            composite.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>()
                .single().let { GPUPixelBounds(it.x, it.y, it.x + it.width, it.y + it.height) },
        )
        // Local stages are clear passes onto the pooled intermediates; the composite
        // rides the borrowed scene target.
        val localViews = renders.dropLast(1).map { it.pass.colorTarget.view }
        val compositeView = composite.pass.colorTarget.view
        assertNotSame(compositeView, localViews.first())
        // One uniform per stage: mask 592B, blur 144B, style 16B, composite 32B.
        assertEquals(4, fixture.native.writeBufferCalls.size)
        assertEquals(
            listOf(592uL, 144uL, 16uL, 32uL),
            fixture.native.writeBufferCalls.map { it.dataBytes },
        )
        // Cache counts: one invariant set, one intermediate set, one composite set
        // (4 invariant pipelines + 4 composite pipelines).
        val counters = fixture.maskBlurCache.counters()
        assertEquals(1L, counters.invariantCreations)
        assertEquals(1L, counters.intermediateCreations)
        assertEquals(8, fixture.native.renderPipelineDescriptors.size)
        assertEquals(1, fixture.native.renderPipelineDescriptors.count {
            requireNotNull(it.label).contains("maskBlur.composite-src-over")
        })
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `destination-read chain materializes the target-sized snapshot copy before the composite`() {
        val fixture = fixture(dstRead = true)
        val materialized = fixture.materialize()

        val payload = materialized.draft.payload
        val scopes = payload.scopeOperands
        val copy = scopes.filterIsInstance<GPUPreparedNativeScopeOperand.Copy>().single()
        val renders = scopes.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        val composite = renders.last()
        assertEquals(GPUEncoderOperationKind.CopyDestination, copy.operationKind)
        assertTrue(copy.sourceStepIndex < composite.sourceStepIndex)
        // The snapshot copy LAYOUT is the full target extent with zero origins: the
        // destination-read composite samples the true scene texel under the blur and
        // the copy never touches the CPU.
        val textureLayout = requireNotNull(copy.textureLayout)
        assertEquals(0, textureLayout.sourceOriginX)
        assertEquals(0, textureLayout.sourceOriginY)
        assertEquals(0, textureLayout.destinationOriginX)
        assertEquals(0, textureLayout.destinationOriginY)
        assertEquals(TARGET_WIDTH, textureLayout.width)
        assertEquals(TARGET_HEIGHT, textureLayout.height)
        // The snapshot texture is created before the dst-read bind group.
        val textureEvent = fixture.native.events.indexOf(
            "createTexture:Kanvas.frame.maskBlur.destinationSnapshot",
        )
        assertTrue(textureEvent >= 0, fixture.native.events.toString())
        val dstBindGroupEvent = fixture.native.events.indexOf(
            "createBindGroup:Kanvas.frame.maskBlur.compositeDstBindGroup",
        )
        assertTrue(dstBindGroupEvent >= 0, fixture.native.events.toString())
        assertTrue(textureEvent < dstBindGroupEvent)
        // The dst-read composite rides the dst pipeline and samples the snapshot.
        val pipeline = composite.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
            .single().pipeline.pipeline
        val dstPipeline = fixture.native.createdHandles("Kanvas.session.maskBlur.composite-dst.pipeline").single()
        assertSame(dstPipeline, pipeline)
        // The dst uniform (48B) joins the four stage uniforms.
        assertEquals(5, fixture.native.writeBufferCalls.size)
        assertEquals(48uL, fixture.native.writeBufferCalls.last().dataBytes)
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `sRGB scene target materializes the dst lane with per-format composite pipelines`() {
        val fixture = fixture(dstRead = true, targetFormat = GPUColorFormat.RGBA8UnormSrgb)
        val materialized = fixture.materialize()

        val payload = materialized.draft.payload
        val copy = payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Copy>().single()
        assertEquals(TARGET_WIDTH, requireNotNull(copy.textureLayout).width)
        // The composite pipeline set is keyed by the sRGB scene format; the four
        // composite pipelines target the sRGB color format.
        val srgbCompositePipelines = fixture.native.renderPipelineDescriptors.filter {
            requireNotNull(it.label).contains("maskBlur.composite") ||
                requireNotNull(it.label).contains("maskBlur.solid")
        }
        assertEquals(4, srgbCompositePipelines.size)
        srgbCompositePipelines.forEach { descriptor ->
            assertEquals(
                GPUTextureFormat.RGBA8UnormSrgb,
                assertIs<io.ygdrasil.webgpu.ColorTargetState>(
                    requireNotNull(descriptor.fragment).targets.single(),
                ).format,
            )
        }
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `materializer reuses cache invariants intermediates and composite pipelines across frames`() {
        val fixture = fixture()
        val first = fixture.materialize()
        assertTrue(first.draft.disposeBeforeRegistration())
        fixture.native.events.clear()
        fixture.native.writeBufferCalls.clear()

        val second = fixture.materialize()

        val counters = fixture.maskBlurCache.counters()
        assertEquals(1L, counters.invariantCreations)
        assertEquals(1L, counters.invariantReuses)
        assertEquals(1L, counters.intermediateCreations)
        assertEquals(1L, counters.intermediateReuses)
        assertEquals(8, fixture.native.renderPipelineDescriptors.size)
        assertTrue(second.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `materializer is one-shot and refuses a second reuse`() {
        val fixture = fixture()
        val materializer = fixture.materializer()
        try {
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializer.materializeReusable(
                    fixture.plan,
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                ),
            )
            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                materializer.materializeReusable(
                    fixture.plan,
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                ),
            )
            assertEquals("unsupported.native-mask-blur.materializer-state", refused.code)
        } finally {
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `refusal matrix pins every invalid and unsupported mask blur code before native action`() {
        data class Scenario(
            val label: String,
            val expectedCode: String,
            val mutate: (Fixture) -> MaterializationInput,
        )

        fun input(fixture: Fixture) = MaterializationInput(
            fixture.plan,
            fixture.encoderPlan,
            fixture.resources,
            fixture.generationSeal,
        )
        fun input(
            fixture: Fixture,
            plan: GPUFramePlan,
            encoderPlan: GPUCommandEncoderPlan = fixture.encoderPlan,
            resources: GPUPreparedResourceSet = fixture.resources,
        ) = MaterializationInput(plan, encoderPlan, resources, fixture.generationSeal)
        fun maskStep(fixture: Fixture): GPUFrameStep.RenderPassStep = fixture.plan.steps[1] as GPUFrameStep.RenderPassStep
        fun compositeStep(fixture: Fixture): GPUFrameStep.RenderPassStep =
            fixture.plan.steps[fixture.plan.steps.size - 1] as GPUFrameStep.RenderPassStep
        fun replacePacket(
            fixture: Fixture,
            step: GPUFrameStep.RenderPassStep,
            transform: (GPUDrawPacket) -> GPUDrawPacket,
        ): MaterializationInput {
            val replacement = transform(step.drawPackets.single())
            return input(
                fixture,
                fixture.withReplacingStep(
                    step,
                    GPUFrameStep.RenderPassStep(
                        target = step.target,
                        loadStore = step.loadStore,
                        samplePlan = step.samplePlan,
                        resourceUses = step.resourceUses,
                        drawPackets = listOf(replacement),
                        sourceTaskIds = step.sourceTaskIds,
                    ),
                ),
            )
        }

        val scenarios = listOf(
            Scenario("render-scope", "invalid.native-mask-blur.render-scope") { fixture ->
                input(fixture, fixture.withSteps(fixture.plan.steps.filterNot { it is GPUFrameStep.RenderPassStep }))
            },
            Scenario("packet-count", "invalid.native-mask-blur.packet-count") { fixture ->
                val step = maskStep(fixture)
                val duplicate = step.drawPackets.single().withPacketId(GPUDrawPacketID("packet.mask-blur.duplicate"))
                input(
                    fixture,
                    fixture.withReplacingStep(
                        step,
                        GPUFrameStep.RenderPassStep(
                            target = step.target,
                            loadStore = step.loadStore,
                            samplePlan = step.samplePlan,
                            resourceUses = step.resourceUses,
                            drawPackets = listOf(step.drawPackets.single(), duplicate),
                            sourceTaskIds = step.sourceTaskIds,
                        ),
                    ),
                )
            },
            Scenario("stage", "invalid.native-mask-blur.stage") { fixture ->
                replacePacket(fixture, maskStep(fixture)) { packet ->
                    packet.withRenderStepId("mask-blur.foreign-stage")
                }
            },
            Scenario("packet-authority", "invalid.native-mask-blur.packet-authority") { fixture ->
                replacePacket(fixture, maskStep(fixture)) { packet ->
                    packet.withVertexSourceLabel("vertex.foreign")
                }
            },
            Scenario("chain-shape", "invalid.native-mask-blur.chain-shape") { fixture ->
                val plan = fixture.withSteps(fixture.plan.steps.filterIndexed { index, _ -> index != 4 })
                val scopes = plan.steps.withIndex()
                    .filterNot { (_, step) -> step is GPUFrameStep.PrepareResourcesStep }
                    .map { (index, step) -> scopeAt(index, step) }
                input(fixture, plan, fixture.encoderPlan.withScopes(scopes))
            },
            Scenario("chain-count", "invalid.native-mask-blur.chain-count") { fixture ->
                fixture.withCoreRenders(1)
            },
            Scenario("multi-core-render", "unsupported.native-mask-blur.multi-core-render") { fixture ->
                fixture.withCoreRenders(2)
            },
            Scenario("semantic-payload", "invalid.native-mask-blur.semantic-payload") { fixture ->
                replacePacket(fixture, maskStep(fixture)) { packet -> packet.withSemantic(null) }
            },
            Scenario("mixed-local-sizes", "unsupported.native-mask-blur.mixed-local-sizes") { fixture ->
                fixture.withSecondChain()
            },
            Scenario("scope-shape", "invalid.native-mask-blur.scope-shape") { fixture ->
                input(fixture, fixture.withSteps(fixture.plan.steps + fixture.extraCopyStep()))
            },
            Scenario("copy-plan", "invalid.native-mask-blur.copy-plan") { fixture ->
                input(
                    fixture,
                    fixture.plan,
                    fixture.encoderPlan.withScopes(
                        fixture.encoderPlan.scopes.filterNot {
                            it.operationKind == GPUEncoderOperationKind.CopyDestination
                        },
                    ),
                )
            },
            Scenario("dst-read-copy", "invalid.native-mask-blur.dst-read-copy") { fixture ->
                fixture.withoutCopyStep()
            },
            Scenario("unexpected-copy", "invalid.native-mask-blur.unexpected-copy") { fixture ->
                replacePacket(fixture, compositeStep(fixture)) { packet ->
                    packet.withBlendPlan(
                        GPUBlendPlan.FixedFunctionBlend(
                            mode = GPUBlendMode.SRC_OVER,
                            state = GPUFixedFunctionBlendState(
                                stateId = "test-src-over",
                                color = GPUFixedFunctionBlendComponent("one", "oneMinusSrcAlpha", "add"),
                                alpha = GPUFixedFunctionBlendComponent("one", "oneMinusSrcAlpha", "add"),
                                writeMask = "rgba",
                            ),
                            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
                        ),
                    )
                }
            },
            Scenario("scene-format", "invalid.native-mask-blur.scene-format") { fixture ->
                input(
                    fixture,
                    fixture.withScenePreparation(
                        GPUFrameTextureDescriptor(
                            TARGET_BOUNDS,
                            GPUColorFormat("rgba16float"),
                            1,
                        ),
                    ),
                )
            },
            Scenario("readback-plan", "invalid.native-mask-blur.readback-plan") { fixture ->
                input(fixture, fixture.withReadbackStep())
            },
            Scenario("scope-order", "invalid.native-mask-blur.scope-order") { fixture ->
                val foreign = fixture.encoderPlan.scopes.last()
                    .withSourceStepIndex(fixture.plan.steps.size + 1)
                input(
                    fixture,
                    fixture.plan,
                    fixture.encoderPlan.withScopes(fixture.encoderPlan.scopes + foreign),
                )
            },
            Scenario("target-alias", "invalid.native-mask-blur.target-alias") { fixture ->
                val blurV = fixture.plan.steps[3] as GPUFrameStep.RenderPassStep
                val blurH = fixture.plan.steps[2] as GPUFrameStep.RenderPassStep
                input(
                    fixture,
                    fixture.withReplacingStep(
                        blurV,
                        GPUFrameStep.RenderPassStep(
                            target = blurH.target,
                            loadStore = blurV.loadStore,
                            samplePlan = blurV.samplePlan,
                            resourceUses = blurV.resourceUses,
                            drawPackets = blurV.drawPackets,
                            sourceTaskIds = blurV.sourceTaskIds,
                        ),
                    ),
                )
            },
            Scenario("target-preparation", "invalid.native-mask-blur.target-preparation") { fixture ->
                val blurVTarget = (fixture.plan.steps[3] as GPUFrameStep.RenderPassStep).target
                input(fixture, fixture.withPreparationDropped(blurVTarget))
            },
            Scenario("prepared-resources", "invalid.native-mask-blur.prepared-resources") { fixture ->
                val resource = (fixture.plan.steps[3] as GPUFrameStep.RenderPassStep).target
                input(
                    fixture,
                    fixture.plan,
                    resources = GPUPreparedResourceSet(
                        fixture.resources.ordinaryResources,
                        fixture.resources.outputOwnedReadbacks,
                    ),
                ).copy(
                    generationSeal = GPUPreparedGenerationSeal(
                        fixture.generationSeal.deviceGeneration,
                        fixture.generationSeal.targetGeneration,
                        fixture.generationSeal.resourceGenerations - resource,
                        fixture.generationSeal.capabilitySealHash,
                    ),
                )
            },
            Scenario("pass-state", "invalid.native-mask-blur.pass-state") { fixture ->
                val step = maskStep(fixture)
                input(
                    fixture,
                    fixture.withReplacingStep(
                        step,
                        GPUFrameStep.RenderPassStep(
                            target = step.target,
                            loadStore = LOAD_LOAD_STORE,
                            samplePlan = step.samplePlan,
                            resourceUses = step.resourceUses,
                            drawPackets = step.drawPackets,
                            sourceTaskIds = step.sourceTaskIds,
                        ),
                    ),
                )
            },
            Scenario("composite-load", "invalid.native-mask-blur.composite-load") { fixture ->
                val step = compositeStep(fixture)
                input(
                    fixture,
                    fixture.withReplacingStep(
                        step,
                        GPUFrameStep.RenderPassStep(
                            target = step.target,
                            loadStore = GPULoadStorePlan("retained", GPUStorePlan.Store),
                            samplePlan = step.samplePlan,
                            resourceUses = step.resourceUses,
                            drawPackets = step.drawPackets,
                            sourceTaskIds = step.sourceTaskIds,
                        ),
                    ),
                )
            },
            Scenario("local-scissor", "invalid.native-mask-blur.local-scissor") { fixture ->
                replacePacket(fixture, maskStep(fixture)) { packet -> packet.withScissorBoundsHash("scissor.forged") }
            },
            Scenario("composite-scissor", "invalid.native-mask-blur.composite-scissor") { fixture ->
                replacePacket(fixture, compositeStep(fixture)) { packet -> packet.withScissorBoundsHash("scissor.forged") }
            },
            Scenario("target-state", "invalid.native-mask-blur.target-state") { fixture ->
                replacePacket(fixture, maskStep(fixture)) { packet -> packet.withTargetStateHash("target.forged") }
            },
            Scenario("clip", "unsupported.native-mask-blur.clip") { fixture ->
                // Task 7 admits only analytic DEVICE-RECT clips on the composite; a
                // complex-clip plan (analytic rrect) stays outside the lane scope.
                replacePacket(fixture, compositeStep(fixture)) { packet ->
                    packet.withClipExecutionPlan(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.RRect(
                                GPUBounds(1f, 1f, 31f, 31f),
                                listOf(2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f),
                            ),
                            scissor = null,
                            antiAlias = true,
                        ),
                    )
                }
            },
        )

        scenarios.forEach { scenario ->
            val fixture = fixture(dstRead = true)
            val materializationInput = scenario.mutate(fixture)
            fixture.native.events.clear()
            fixture.native.writeBufferCalls.clear()
            val cacheBefore = fixture.maskBlurCache.counters()

            val result = fixture.materializeResult(materializationInput)

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result, scenario.label)
            assertEquals(scenario.expectedCode, refused.code, "${scenario.label}: ${refused.message}")
            assertEquals(emptyList(), fixture.native.events, scenario.label)
            assertEquals(emptyList(), fixture.native.writeBufferCalls, scenario.label)
            assertEquals(cacheBefore, fixture.maskBlurCache.counters(), scenario.label)
            fixture.close()
        }
    }

    @Test
    fun `composite clip refusal predicate pins the lane scope boundary`() {
        val fixture = fixture()
        val composite = compositePacket(fixture)
        assertEquals(null, topLevelMaskBlurCompositeClipRefusal(composite))
        val scissorOnly = composite.withClipExecutionPlan(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(0, 0, 16, 16)),
        )
        assertEquals(null, topLevelMaskBlurCompositeClipRefusal(scissorOnly))
        val analyticRect = composite.withClipExecutionPlan(
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUBounds(1f, 1f, 31f, 31f)),
                scissor = null,
                antiAlias = true,
            ),
        )
        assertEquals(null, topLevelMaskBlurCompositeClipRefusal(analyticRect))
        val analyticRRect = composite.withClipExecutionPlan(
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.RRect(
                    GPUBounds(1f, 1f, 31f, 31f),
                    listOf(2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f),
                ),
                scissor = null,
                antiAlias = true,
            ),
        )
        assertEquals("unsupported.native-mask-blur.clip", topLevelMaskBlurCompositeClipRefusal(analyticRRect))
        val coverageMask = composite.withClipExecutionPlan(
            GPUClipExecutionPlan.CoverageMask(
                contentKey = "clip.mask-blur.coverage-mask",
                bounds = GPUPixelBounds(0, 0, 16, 16),
                sampleCount = 1,
                depthStencilRequired = false,
                orderingToken = GPUClipOrderingToken("token.clip.mask-blur.coverage-mask"),
                producers = listOf(
                    GPUClipMaskProducerPlan(
                        sourceOrder = 0,
                        geometry = GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 16f, 16f)),
                        combine = GPUClipMaskCombine.Intersect,
                        antiAlias = false,
                    ),
                    GPUClipMaskProducerPlan(
                        sourceOrder = 1,
                        geometry = GPUClipExecutionGeometry.Rect(GPUBounds(2f, 2f, 14f, 14f)),
                        combine = GPUClipMaskCombine.Intersect,
                        antiAlias = false,
                    ),
                ),
                consumer = GPUClipMaskConsumerPlan(),
            ),
        )
        assertEquals("unsupported.native-mask-blur.clip", topLevelMaskBlurCompositeClipRefusal(coverageMask))
        val stencil = composite.withClipExecutionPlan(stencilClipPlan())
        assertEquals("unsupported.native-mask-blur.clip", topLevelMaskBlurCompositeClipRefusal(stencil))
        fixture.close()
    }

    private fun compositePacket(fixture: Fixture): GPUDrawPacket =
        (fixture.plan.steps[5] as GPUFrameStep.RenderPassStep).drawPackets.single()

    private fun stencilClipPlan(): GPUClipExecutionPlan.StencilCoverage =
        GPUClipExecutionPlan.StencilCoverage(
            contentKey = "clip.mask-blur.stencil",
            bounds = GPUPixelBounds(0, 0, 16, 16),
            sampleCount = 1,
            atomicGroup = GPUClipAtomicGroupID("atomic.clip.mask-blur.stencil"),
            orderingToken = GPUClipOrderingToken("token.clip.mask-blur.stencil"),
            producer = GPUClipStencilProducerPlan(
                geometry = GPUClipExecutionGeometry.Path(
                    vertices = listOf(2f, 2f, 14f, 2f, 14f, 14f, 2f, 14f),
                    contourStarts = listOf(0),
                    fillRule = GPUClipFillRule.Winding,
                    inverseFill = false,
                ),
                scissor = GPUPixelBounds(0, 0, 16, 16),
                fillRule = GPUClipFillRule.Winding,
                reference = 0u,
                compare = GPUClipStencilCompare.Always,
                frontPassOperation = GPUClipStencilOperation.IncrementWrap,
                backPassOperation = GPUClipStencilOperation.DecrementWrap,
                loadOperation = GPUClipStencilLoadOperation.Clear,
                storeOperation = GPUClipStencilStoreOperation.Store,
                clearValue = 0u,
            ),
            consumer = GPUClipStencilConsumerPlan(
                scissor = GPUPixelBounds(0, 0, 16, 16),
                reference = 0u,
                compare = GPUClipStencilCompare.NotEqual,
            ),
        )

    // ---- Fixture ----

    private fun fixture(
        dstRead: Boolean = false,
        targetFormat: GPUColorFormat = GPUColorFormat.RGBA8Unorm,
    ): Fixture {
        val capabilities = capabilities()
        val capabilitySeal = GPUFrameCapabilitySeal.capture(FRAME_ID, GENERATION, capabilities)
        val semantic = maskBlurSemantic(commandId = 1, bounds = GPUBounds(8f, 8f, 17f, 17f))
        val generations = linkedMapOf<GPUFrameResourceRef, Long>()
        fun register(resource: GPUFrameResourceRef): Long {
            val generation = (generations.size + 1L)
            generations[resource] = generation
            return generation
        }
        register(SCENE_TARGET)
        val preparations = mutableListOf<GPUResourcePreparationRequest>()
        preparations += GPUResourcePreparationRequest(
            resource = SCENE_TARGET,
            descriptor = GPUFrameTextureDescriptor(TARGET_BOUNDS, targetFormat, 1),
            role = GPUFrameResourceRole.SceneTarget,
            usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = TARGET_WIDTH.toLong() * TARGET_HEIGHT * 4L,
            diagnosticLabel = "mask-blur.scene-target",
        )
        val chainLocalTargets = listOf(
            GPUFrameTargetRef("frame.mask-blur.mask.1"),
            GPUFrameTargetRef("frame.mask-blur.blur-h.1"),
            GPUFrameTargetRef("frame.mask-blur.blur-v.1"),
            GPUFrameTargetRef("frame.mask-blur.styled.1"),
        )
        val localDescriptor = GPUFrameTextureDescriptor(
            GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight),
            GPUColorFormat.RGBA8Unorm,
            1,
        )
        chainLocalTargets.forEach { target ->
            register(target)
            preparations += GPUResourcePreparationRequest(
                resource = target,
                descriptor = localDescriptor,
                role = GPUFrameResourceRole.FilterTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = semantic.localWidth.toLong() * semantic.localHeight * 4L,
                diagnosticLabel = "mask-blur.local.${target.value}",
            )
        }
        val localScissor = topLevelMaskBlurScissorAuthority(
            GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight),
        )
        val replaceBlend = replaceBlendPlan("mask-blur-local")
        val compositeBlend = if (dstRead) {
            GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.DARKEN,
                formulaId = "darken@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            )
        } else {
            srcOverBlendPlan()
        }
        val maskPacket = stagePacket(
            semantic,
            stepId = TOP_LEVEL_MASK_BLUR_MASK_STEP,
            targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK,
            layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_MASK,
            scissor = GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight),
            blendPlan = replaceBlend,
            isComposite = false,
        )
        val blurHPacket = stagePacket(
            semantic,
            stepId = TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP,
            targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
            layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR,
            scissor = GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight),
            blendPlan = replaceBlend,
            isComposite = false,
        )
        val blurVPacket = stagePacket(
            semantic,
            stepId = TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP,
            targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
            layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR,
            scissor = GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight),
            blendPlan = replaceBlend,
            isComposite = false,
        )
        val stylePacket = stagePacket(
            semantic,
            stepId = TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP,
            targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
            layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_STYLE,
            scissor = GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight),
            blendPlan = replaceBlend,
            isComposite = false,
        )
        val compositePacket = stagePacket(
            semantic,
            stepId = MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY,
            targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE,
            layoutHash = if (dstRead) TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE_DST else TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE,
            scissor = semantic.scissorBounds,
            blendPlan = compositeBlend,
            isComposite = true,
        )
        val steps = mutableListOf<GPUFrameStep>()
        steps += GPUFrameStep.PrepareResourcesStep(preparations.toList(), listOf(GPUTaskID("task.prepare")))
        steps += renderStep(chainLocalTargets[0], CLEAR_LOAD_STORE, maskPacket)
        steps += renderStep(chainLocalTargets[1], CLEAR_LOAD_STORE, blurHPacket)
        steps += renderStep(chainLocalTargets[2], CLEAR_LOAD_STORE, blurVPacket)
        steps += renderStep(chainLocalTargets[3], CLEAR_LOAD_STORE, stylePacket)
        if (dstRead) {
            // The snapshot copy precedes the consuming composite pass in step order
            // (Graphite DrawContext.cpp recipe: copy-then-formula).
            steps += copyStep()
        }
        steps += renderStep(SCENE_TARGET, LOAD_LOAD_STORE, compositePacket)
        val plan = GPUFramePlan(
            frameId = FRAME_ID,
            capabilitySeal = capabilitySeal,
            recordingSeals = emptyList(),
            steps = steps,
            memoryBudget = GPUFrameMemoryBudgetPlan(
                peakFrameTransientBytes = 0L,
                targetResidentBytes = 0L,
                categoryTotals = emptyMap(),
                deviceLimitFacts = emptyList(),
                configuredAggregateBudgetBytes = 0L,
                diagnostic = null,
            ),
            diagnostics = emptyList(),
        )
        val encoderPlan = GPUCommandEncoderPlan.ordered(
            planId = "mask-blur.proxy.encoder",
            contextIdentity = "target.scene",
            deviceGeneration = GENERATION,
            targetGeneration = 1L,
            scopes = steps.withIndex().filterNot { (_, step) -> step is GPUFrameStep.PrepareResourcesStep }
                .map { (index, step) -> scopeAt(index, step) },
        )
        val resources = GPUPreparedResourceSet(
            ordinaryResources = preparations.map { request ->
                GPUPreparedResourceEvidence(
                    logicalResource = request.resource,
                    concreteResource = GPUPreparedConcreteResourceRef.Texture(
                        GPUTextureResourceRef("prepared.${request.resource.value}"),
                    ),
                    role = request.role,
                    deviceGeneration = GENERATION,
                    resourceGeneration = generations.getValue(request.resource),
                )
            },
            outputOwnedReadbacks = emptyList(),
        )
        val generationSeal = GPUPreparedGenerationSeal(
            GENERATION,
            1L,
            generations,
            plan.capabilitySeal.sealHash,
        )
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            native.device,
            TARGET_WIDTH,
            TARGET_HEIGHT,
            when (targetFormat) {
                GPUColorFormat.RGBA8Unorm -> GPUTextureFormat.RGBA8Unorm
                GPUColorFormat.RGBA8UnormSrgb -> GPUTextureFormat.RGBA8UnormSrgb
                else -> error("Unsupported mask blur fixture target format: ${targetFormat.value}")
            },
            GENERATION,
            1L,
            GPUWgpu4kPreparedSceneTargetLifecycle(),
            setup,
        )
        setup.commit()
        return Fixture(
            plan,
            encoderPlan,
            resources,
            generationSeal,
            native,
            target,
            GPUWgpu4kMaskBlurSessionCache(native.device),
            GPUWgpu4kCorePrimitiveSessionCache(native.device, GENERATION),
            requireNotNull(capabilities.limits),
        )
    }

    private fun renderStep(
        target: GPUFrameTargetRef,
        loadStore: GPULoadStorePlan,
        packet: GPUDrawPacket,
    ): GPUFrameStep.RenderPassStep = GPUFrameStep.RenderPassStep(
        target = target,
        loadStore = loadStore,
        samplePlan = GPUSamplePlan.SingleSampleFrame,
        resourceUses = emptyList(),
        drawPackets = listOf(packet),
        sourceTaskIds = listOf(GPUTaskID("task.${target.value}.${loadStore.loadOp}")),
    )

    private fun copyStep(): GPUFrameStep.CopyDestinationStep = GPUFrameStep.CopyDestinationStep(
        source = SCENE_TARGET,
        sourceKey = GPUDestinationSnapshotGroupKey(
            target = GPUTargetIdentity("target.scene"),
            targetGeneration = 1L,
            deviceGeneration = GENERATION,
            format = GPUColorFormat.RGBA8Unorm,
            colorInterpretation = GPUColorInterpretation.LinearPremul,
            sampleContinuation = null,
            sourceIntermediate = null,
        ),
        snapshot = org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef("texture.mask-blur.snapshot"),
        logicalBounds = TARGET_BOUNDS,
        copyLayout = GPUTextureCopyLayout(bytesPerRow = TARGET_WIDTH.toLong() * 4L, rowsPerImage = TARGET_HEIGHT),
        consumers = listOf(
            GPUDestinationSnapshotConsumerRef(
                groupingCommandId = "mask-blur-1",
                renderTaskId = GPUTaskID("task.mask-blur.composite.1"),
                packetId = GPUDrawPacketID("packet.mask-blur.composite.1"),
                commandId = GPUDrawCommandID(1),
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.mask-blur.copy")),
    )

    private fun scopeAt(index: Int, step: GPUFrameStep): GPUCommandEncoderScopePlan {
        if (step is GPUFrameStep.RenderPassStep) {
            val stream = GPUPassCommandStream.fromDrawPacketStream(
                streamId = "stream.mask-blur.$index",
                packetStream = GPUDrawPacketStream(
                    "stream.mask-blur.$index",
                    step.drawPackets.first().passId,
                    step.drawPackets,
                ),
                targetStateHash = step.drawPackets.first().targetStateHash,
                loadStoreLabel = step.loadStore.loadOp,
            )
            return GPUCommandEncoderScopePlan(
                sourceStepIndex = index,
                operationKind = GPUEncoderOperationKind.Render,
                scopeLabel = "step.$index",
                sourceTaskIds = step.sourceTaskIds,
                sourcePacketIds = step.drawPackets.map(GPUDrawPacket::packetId),
                facadeOperationClasses = stream.commandLabels,
                targetGeneration = 1L,
                resourceGenerationLabels = listOf("resource@1"),
                passCommandStream = stream,
            ).attachNativeOperandKeys(maskBlurRenderKeys())
        }
        val operationKind = when (step) {
            is GPUFrameStep.CopyDestinationStep -> GPUEncoderOperationKind.CopyDestination
            else -> error("Unencodable mask blur fixture step ${step::class.simpleName}")
        }
        return GPUCommandEncoderScopePlan(
            sourceStepIndex = index,
            operationKind = operationKind,
            scopeLabel = "step.$index",
            sourceTaskIds = step.sourceTaskIds,
            facadeOperationClasses = listOf("encode"),
            targetGeneration = 1L,
            resourceGenerationLabels = listOf("resource@1"),
        ).attachNativeOperandKeys(maskBlurCopyKeys())
    }

    private fun maskBlurRenderKeys(): List<GPUPreparedNativeOperandKey> = listOf(
        operandKey(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView),
        operandKey(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline),
        operandKey(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup),
    )

    private fun maskBlurCopyKeys(): List<GPUPreparedNativeOperandKey> = listOf(
        operandKey(GPUPreparedNativeOperandRole.CopySource, GPUPreparedNativeOperandKind.Texture),
        operandKey(GPUPreparedNativeOperandRole.CopyDestination, GPUPreparedNativeOperandKind.Texture),
    )

    private fun operandKey(
        role: GPUPreparedNativeOperandRole,
        kind: GPUPreparedNativeOperandKind,
    ) = GPUPreparedNativeOperandKey(
        role = role,
        kind = kind,
        bindingKey = gpuPreparedNativeBindingKey("mask-blur.${role.name}"),
        ownership = GPUPreparedNativeOperandOwnership.Borrowed,
    )

    private fun maskBlurSemantic(commandId: Int, bounds: GPUBounds): GPUDrawSemanticPayload.MaskBlur {
        val plan = assertIs<MaskBlurPlan.Ready>(
            MaskBlurPlanner.plan(
                MaskBlurRequest(
                    bounds = bounds,
                    clipBounds = bounds,
                    targetWidth = TARGET_WIDTH,
                    targetHeight = TARGET_HEIGHT,
                    style = NormalizedBlurStyle.NORMAL,
                    sigma = 2f,
                    maxTextureDimension2D = 4096,
                    maxIntermediateBytes = 1L shl 30,
                ),
            ),
        )
        val (taps, weights) = gaussianKernel(plan.effectiveSigma)
        return GPUMaskBlurPayloadGatherer().gatherSemantic(
            commandIdValue = commandId,
            sourceFamily = "FillRect",
            deviceBounds = plan.deviceBounds,
            localWidth = plan.localWidth,
            localHeight = plan.localHeight,
            scale = plan.scale,
            style = NormalizedBlurStyle.NORMAL,
            effectiveSigma = plan.effectiveSigma,
            tapCount = taps,
            weights = weights,
            localGeometry = GPUMaskBlurLocalGeometry.Rect(
                0f,
                0f,
                plan.localWidth.toFloat(),
                plan.localHeight.toFloat(),
            ),
            premultipliedRgba = floatArrayOf(0.5f, 0f, 0f, 0.5f),
            targetBounds = TARGET_BOUNDS,
            scissorBounds = TARGET_BOUNDS,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            clipExecutionPlanIdentity = null,
            blendPlanIdentity = "fixed:SRC_OVER:None:test-src-over:one:oneMinusSrcAlpha:add:one:oneMinusSrcAlpha:add:rgba",
        )
    }

    private fun gaussianKernel(sigma: Float): Pair<Int, FloatArray> {
        val activeSigma = max(0.5f, sigma)
        val taps = (ceil(activeSigma.toDouble()).toInt() * 2 + 1).coerceIn(3, MAX_MASK_BLUR_TAPS)
        val half = taps / 2
        val active = FloatArray(taps) { index ->
            val x = (index - half).toFloat()
            exp(-(x * x) / (2f * activeSigma * activeSigma))
        }
        val sum = active.sum()
        val weights = FloatArray(MAX_MASK_BLUR_TAPS)
        active.forEachIndexed { index, value -> weights[index] = value / sum }
        return taps to weights
    }

    private fun stagePacket(
        semantic: GPUDrawSemanticPayload.MaskBlur,
        stepId: String,
        targetStateHash: String,
        layoutHash: String,
        scissor: GPUPixelBounds,
        blendPlan: GPUBlendPlan,
        isComposite: Boolean,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.mask-blur.${semantic.payloadRef.commandIdValue}.$stepId"),
        commandIdValue = semantic.payloadRef.commandIdValue,
        analysisRecordId = "analysis.mask-blur.${semantic.payloadRef.commandIdValue}",
        passId = "pass.mask-blur.$stepId",
        layerId = "root",
        bindingListId = "bindings.mask-blur.$stepId",
        insertionReasonCode = "mask-blur-$stepId",
        sortKey = 0L,
        sortKeyPreimage = "mask-blur:$stepId:${semantic.payloadRef.commandIdValue}",
        renderStepId = GPURenderStepID(stepId),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = blendPlan,
        renderPipelineKey = GPURenderPipelineKey("pipeline.mask-blur.$stepId"),
        bindingLayoutHash = layoutHash,
        uniformSlot = semantic.payloadRef.uniformSlot,
        semanticPayload = semantic,
        vertexSourceLabel = TOP_LEVEL_MASK_BLUR_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = topLevelMaskBlurScissorAuthority(scissor),
        targetStateHash = targetStateHash,
        originalPaintOrder = 1,
        resourceGeneration = 1L,
        frameProvenance = GPUFrameProvenance.GmContent,
        clipCoveragePlan = semantic.clipCoveragePlan,
        clipExecutionPlan = if (isComposite) GPUClipExecutionPlan.NoClip else null,
    )

    private fun replaceBlendPlan(stateId: String) = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC,
        state = GPUFixedFunctionBlendState(
            stateId = stateId,
            color = GPUFixedFunctionBlendComponent("one", "zero", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "zero", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun srcOverBlendPlan() = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "test-src-over",
            color = GPUFixedFunctionBlendComponent("one", "oneMinusSrcAlpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "oneMinusSrcAlpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit", "supported", true, "core"),
            GPUCapabilityFact("first_slice.scissor.native", "unit", "supported", true, "core"),
        ),
        snapshotId = "mask-blur-proxy",
        limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            GPUTextureFormat.RGBA8Unorm,
            GPUTextureFormat.RGBA8UnormSrgb,
        ),
        textureFormatSampleSupport =
            org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport(
                mapOf(
                    GPUTextureFormat.RGBA8Unorm to
                        org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport(
                            renderAttachmentSampleCounts = setOf(1),
                            resolveSourceSampleCounts = emptySet(),
                        ),
                    GPUTextureFormat.RGBA8UnormSrgb to
                        org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport(
                            renderAttachmentSampleCounts = setOf(1),
                            resolveSourceSampleCounts = emptySet(),
                        ),
                ),
            ),
        rendererFeatures = setOf(GPURendererFeature.RenderPass),
    )

    private inner class Fixture(
        val plan: GPUFramePlan,
        val encoderPlan: GPUCommandEncoderPlan,
        val resources: GPUPreparedResourceSet,
        val generationSeal: GPUPreparedGenerationSeal,
        val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
        val target: GPUWgpu4kPreparedSceneTarget,
        val maskBlurCache: GPUWgpu4kMaskBlurSessionCache,
        val corePrimitiveCache: GPUWgpu4kCorePrimitiveSessionCache,
        val limits: GPULimits,
    ) {
        fun materializer() = GPUWgpu4kMaskBlurFramePayloadMaterializer(
            native.device,
            native.queue,
            target,
            maskBlurCache,
            corePrimitiveCache,
            limits,
        )

        fun materializeResult(
            input: MaterializationInput,
        ): GPUPreparedNativeFramePayloadMaterialization {
            val materializer = materializer()
            return materializer.materializeReusable(
                input.plan,
                input.encoderPlan,
                input.resources,
                input.generationSeal,
            ).also { materializer.close() }
        }

        fun materializeResult(): GPUPreparedNativeFramePayloadMaterialization =
            materializeResult(
                MaterializationInput(plan, encoderPlan, resources, generationSeal),
            )

        fun materialize(): GPUPreparedNativeFramePayloadMaterialization.Materialized {
            val result = materializeResult()
            return assertIs(
                result,
                (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let {
                    "${it.code}: ${it.message}"
                },
            )
        }

        fun withReplacingStep(
            original: GPUFrameStep,
            replacement: GPUFrameStep,
        ): GPUFramePlan = GPUFramePlan(
            frameId = plan.frameId,
            capabilitySeal = plan.capabilitySeal,
            recordingSeals = plan.recordingSeals,
            steps = plan.steps.map { if (it === original) replacement else it },
            memoryBudget = plan.memoryBudget,
            diagnostics = plan.diagnostics,
            dependencies = plan.dependencies,
            phaseOrder = plan.phaseOrder,
            elidedNoOpDraws = plan.elidedNoOpDraws,
        )

        fun withSteps(steps: List<GPUFrameStep>): GPUFramePlan = GPUFramePlan(
            frameId = plan.frameId,
            capabilitySeal = plan.capabilitySeal,
            recordingSeals = plan.recordingSeals,
            steps = steps,
            memoryBudget = plan.memoryBudget,
            diagnostics = plan.diagnostics,
            dependencies = plan.dependencies,
            phaseOrder = plan.phaseOrder,
            elidedNoOpDraws = plan.elidedNoOpDraws,
        )

        fun extraCopyStep(): GPUFrameStep.CopyDestinationStep = copyStep()

        fun withScenePreparation(
            descriptor: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceDescriptor,
        ): GPUFramePlan = withPreparation(SCENE_TARGET, descriptor)

        fun withPreparation(
            resource: GPUFrameResourceRef,
            descriptor: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceDescriptor,
        ): GPUFramePlan {
            val preparation = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .single { it.resource == resource }
            val replacement = GPUResourcePreparationRequest(
                resource = preparation.resource,
                descriptor = descriptor,
                role = preparation.role,
                usages = preparation.usages,
                lifetime = preparation.lifetime,
                byteSize = preparation.byteSize,
                diagnosticLabel = preparation.diagnosticLabel,
            )
            return withPreparationRequests { request -> if (request === preparation) replacement else request }
        }

        fun withPreparationDropped(resource: GPUFrameResourceRef): GPUFramePlan =
            withPreparationRequests { request -> if (request.resource == resource) null else request }

        private fun withPreparationRequests(
            transform: (GPUResourcePreparationRequest) -> GPUResourcePreparationRequest?,
        ): GPUFramePlan = GPUFramePlan(
            frameId = plan.frameId,
            capabilitySeal = plan.capabilitySeal,
            recordingSeals = plan.recordingSeals,
            steps = plan.steps.map { step ->
                if (step !is GPUFrameStep.PrepareResourcesStep) return@map step
                GPUFrameStep.PrepareResourcesStep(
                    requests = step.requests.mapNotNull(transform),
                    sourceTaskIds = step.sourceTaskIds,
                )
            },
            memoryBudget = plan.memoryBudget,
            diagnostics = plan.diagnostics,
            dependencies = plan.dependencies,
            phaseOrder = plan.phaseOrder,
            elidedNoOpDraws = plan.elidedNoOpDraws,
        )

        fun withReadbackStep(): GPUFramePlan = withSteps(
            plan.steps + GPUFrameStep.ReadbackCopyStep(
                source = SCENE_TARGET,
                staging = GPUFrameBufferRef("buffer.mask-blur.readback"),
                request = GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("readback.mask-blur"),
                    sourceBounds = TARGET_BOUNDS,
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
                ),
                sourceTaskIds = listOf(GPUTaskID("task.mask-blur.readback")),
            ),
        )

        /** The dst-read composite without its snapshot copy step and copy scope. */
        fun withoutCopyStep(): MaterializationInput {
            val planWithoutCopy = withSteps(
                plan.steps.filterNot { it is GPUFrameStep.CopyDestinationStep },
            )
            val scopes = planWithoutCopy.steps.withIndex()
                .filterNot { (_, step) -> step is GPUFrameStep.PrepareResourcesStep }
                .map { (index, step) -> scopeAt(index, step) }
            return MaterializationInput(
                planWithoutCopy,
                encoderPlan.withScopes(scopes),
                resources,
                generationSeal,
            )
        }

        /** A second chain with a different local size rides the frame with matching scopes. */
        fun withSecondChain(): MaterializationInput {
            val second = maskBlurSemantic(commandId = 2, bounds = GPUBounds(8f, 8f, 19f, 19f))
            val secondLocal = GPUPixelBounds(0, 0, second.localWidth, second.localHeight)
            val secondTargets = listOf(
                GPUFrameTargetRef("frame.mask-blur.mask.2"),
                GPUFrameTargetRef("frame.mask-blur.blur-h.2"),
                GPUFrameTargetRef("frame.mask-blur.blur-v.2"),
                GPUFrameTargetRef("frame.mask-blur.styled.2"),
            )
            val secondSteps = listOf(
                renderStep(secondTargets[0], CLEAR_LOAD_STORE, stagePacket(
                    second, TOP_LEVEL_MASK_BLUR_MASK_STEP, TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK,
                    TOP_LEVEL_MASK_BLUR_LAYOUT_MASK, secondLocal, replaceBlendPlan("mask-blur-local"), false,
                )),
                renderStep(secondTargets[1], CLEAR_LOAD_STORE, stagePacket(
                    second, TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP, TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
                    TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR, secondLocal, replaceBlendPlan("mask-blur-local"), false,
                )),
                renderStep(secondTargets[2], CLEAR_LOAD_STORE, stagePacket(
                    second, TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP, TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
                    TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR, secondLocal, replaceBlendPlan("mask-blur-local"), false,
                )),
                renderStep(secondTargets[3], CLEAR_LOAD_STORE, stagePacket(
                    second, TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP, TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
                    TOP_LEVEL_MASK_BLUR_LAYOUT_STYLE, secondLocal, replaceBlendPlan("mask-blur-local"), false,
                )),
                renderStep(SCENE_TARGET, LOAD_LOAD_STORE, stagePacket(
                    second, MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY, TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE,
                    TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE, second.scissorBounds, srcOverBlendPlan(), true,
                )),
            )
            val extendedPlan = withSteps(plan.steps + secondSteps)
            val extendedScopes = extendedPlan.steps.withIndex()
                .filterNot { (_, step) -> step is GPUFrameStep.PrepareResourcesStep }
                .map { (index, step) -> scopeAt(index, step) }
            return MaterializationInput(
                extendedPlan,
                encoderPlan.withScopes(extendedScopes),
                resources,
                generationSeal,
            )
        }

        /** A plan whose render steps are one or two core scene renders only (no blur lane). */
        fun withCoreRenders(count: Int): MaterializationInput {
            val coreSteps = (1..count).map { ordinal -> coreRenderStep(ordinal) }
            val corePlan = GPUFramePlan(
                frameId = plan.frameId,
                capabilitySeal = plan.capabilitySeal,
                recordingSeals = plan.recordingSeals,
                steps = coreSteps,
                memoryBudget = plan.memoryBudget,
                diagnostics = plan.diagnostics,
            )
            val scopes = coreSteps.withIndex().map { (index, step) -> scopeAt(index, step) }
            return MaterializationInput(
                corePlan,
                encoderPlan.withScopes(scopes),
                resources,
                generationSeal,
            )
        }

        private fun coreRenderStep(ordinal: Int): GPUFrameStep.RenderPassStep {
            val commandId = 100 + ordinal
            val packet = GPUDrawPacket(
                packetId = GPUDrawPacketID("packet.core.$commandId"),
                commandIdValue = commandId,
                analysisRecordId = "analysis.core.$commandId",
                passId = "pass.core.$commandId",
                layerId = "root",
                bindingListId = "bindings.core.$commandId",
                insertionReasonCode = "core-render",
                sortKey = 0L,
                sortKeyPreimage = "core:$commandId",
                renderStepId = GPURenderStepID("core-render.$commandId"),
                renderStepVersion = 1,
                role = GPUDrawPacketRole.Shading,
                blendPlan = srcOverBlendPlan(),
                renderPipelineKey = GPURenderPipelineKey("pipeline.core.$commandId"),
                bindingLayoutHash = "layout.core.$commandId",
                semanticPayload = GPUCorePrimitivePayloadGatherer().gatherSemantic(
                    GPUCorePrimitivePayloadInput(
                        commandIdValue = commandId,
                        sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                        geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 5f, 5f),
                        premultipliedRgba = listOf(0.5f, 0f, 0f, 0.5f),
                        targetBounds = TARGET_BOUNDS,
                        scissorBounds = TARGET_BOUNDS,
                        clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                        blendPlanIdentity = "fixed:SRC_OVER:None:test-src-over:one:oneMinusSrcAlpha:add:one:oneMinusSrcAlpha:add:rgba",
                        frameProvenance = GPUFrameProvenance.GmContent,
                        coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
                        analysisRecordId = "analysis.fill_rect.$commandId",
                        analysisCommandFamily = "FillRect",
                        rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                        rectGeometryAuthority = corePrimitiveRectGeometryAuthority(
                            GPURect(1f, 1f, 5f, 5f),
                            GPUTransformFacts.identity(),
                        ),
                    ),
                ),
                vertexSourceLabel = "core-triangle",
                scissorBoundsHash = topLevelMaskBlurScissorAuthority(TARGET_BOUNDS),
                targetStateHash = "target.core.$commandId",
                originalPaintOrder = ordinal,
                resourceGeneration = 1L,
                frameProvenance = GPUFrameProvenance.GmContent,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            )
            return GPUFrameStep.RenderPassStep(
                target = SCENE_TARGET,
                loadStore = LOAD_LOAD_STORE,
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                resourceUses = emptyList(),
                drawPackets = listOf(packet),
                sourceTaskIds = listOf(GPUTaskID("task.core.$commandId")),
            )
        }

        fun close() {
            runCatching { maskBlurCache.close() }
            runCatching { corePrimitiveCache.close() }
            target.close()
        }
    }

    private data class MaterializationInput(
        val plan: GPUFramePlan,
        val encoderPlan: GPUCommandEncoderPlan,
        val resources: GPUPreparedResourceSet,
        val generationSeal: GPUPreparedGenerationSeal,
    )

    private fun GPUCommandEncoderPlan.withScopes(
        scopes: List<GPUCommandEncoderScopePlan>,
    ): GPUCommandEncoderPlan = GPUCommandEncoderPlan.ordered(
        planId = planId,
        contextIdentity = contextIdentity,
        deviceGeneration = deviceGeneration,
        targetGeneration = targetGeneration,
        scopes = scopes,
    )

    private fun GPUCommandEncoderScopePlan.withSourceStepIndex(
        sourceStepIndex: Int,
    ): GPUCommandEncoderScopePlan {
        val keys = nativeOperandKeys
        return GPUCommandEncoderScopePlan(
            sourceStepIndex = sourceStepIndex,
            operationKind = operationKind,
            scopeLabel = scopeLabel,
            sourceTaskIds = sourceTaskIds,
            sourcePacketIds = sourcePacketIds,
            facadeOperationClasses = facadeOperationClasses,
            targetGeneration = targetGeneration,
            resourceGenerationLabels = resourceGenerationLabels,
            passCommandStream = passCommandStream,
        ).attachNativeOperandKeys(keys)
    }

    private fun GPUDrawPacket.withRenderStepId(stepId: String): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, GPURenderStepID(stepId),
        renderStepVersion, role, blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash,
        uniformSlot, resourceSlot, semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash,
        originalPaintOrder, resourceGeneration, frameProvenance, clipCoveragePlan, clipExecutionPlan,
        diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withVertexSourceLabel(label: String): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semanticPayload,
        label, scissorBoundsHash, targetStateHash, originalPaintOrder, resourceGeneration, frameProvenance,
        clipCoveragePlan, clipExecutionPlan, diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withSemantic(semantic: GPUDrawSemanticPayload?): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semantic,
        vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder, resourceGeneration,
        frameProvenance, clipCoveragePlan, clipExecutionPlan, diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withScissorBoundsHash(hash: String): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semanticPayload,
        vertexSourceLabel, hash, targetStateHash, originalPaintOrder, resourceGeneration, frameProvenance,
        clipCoveragePlan, clipExecutionPlan, diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withTargetStateHash(hash: String): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semanticPayload,
        vertexSourceLabel, scissorBoundsHash, hash, originalPaintOrder, resourceGeneration, frameProvenance,
        clipCoveragePlan, clipExecutionPlan, diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withClipExecutionPlan(plan: GPUClipExecutionPlan?): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semanticPayload,
        vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder, resourceGeneration,
        frameProvenance, clipCoveragePlan, plan, diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withBlendPlan(blendPlan: GPUBlendPlan): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semanticPayload,
        vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder, resourceGeneration,
        frameProvenance, clipCoveragePlan, clipExecutionPlan, diagnostics, clipProducerAuthority,
    )

    private fun GPUDrawPacket.withPacketId(packetId: GPUDrawPacketID): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role, blendPlan,
        renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot, semanticPayload,
        vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder, resourceGeneration,
        frameProvenance, clipCoveragePlan, clipExecutionPlan, diagnostics, clipProducerAuthority,
    )
}
