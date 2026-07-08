package org.graphiks.kanvas.gpu.renderer.paintblend

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialAssemblyPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourcePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintPipelinePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintStagePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidColorPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidMaterialDictionary
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidMaterialLowering
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadField
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.state.GPUAlphaPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistGatePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistPlanner
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

/** Verifies KGPU-M11-009 paint dictionary and blend-plan execution boundary contracts. */
class PaintBlendExecutionBoundaryTest {
    @Test
    fun `fixed function paint blend execution materializes payload pipeline and commands`() {
        val request = paintBlendExecutionRequest()
        val result = ValidatingPaintBlendExecutionBoundary().materialize(
            request = request,
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        val commandStream = checkNotNull(result.commandStream)
        val renderKey = assertIs<GPUPassCommand.SetRenderPipeline>(
            commandStream.commands.single { command -> command.commandLabel == "setRenderPipeline" },
        ).pipelineKey.value
        val expectedRenderKey = GPUPaintBlendExecutionKeys.renderPipelineKey(
            request.paintPlan,
            request.materialAssembly,
            request.blendGate,
            request.payloadRequest.reflectedBindingLayoutHash,
        ).value
        val expectedCacheKey = GPUPaintBlendExecutionKeys.pipelineCacheKey(
            request.paintPlan,
            request.materialAssembly,
            request.blendGate,
            request.payloadRequest.reflectedBindingLayoutHash,
        ).value

        assertEquals(expectedRenderKey, renderKey)
        assertFalse(renderKey == expectedCacheKey)
        assertEquals(
            listOf(
                "setBindGroup" to GPUMaterializedCommandOperandKind.UniformBuffer,
                "setBindGroup" to GPUMaterializedCommandOperandKind.BindGroup,
                "setRenderPipeline" to GPUMaterializedCommandOperandKind.RenderPipeline,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.commandLabel to binding.operand.kind },
        )
        assertEquals(
            listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass"),
            commandStream.commandLabels,
        )

        val lines = result.dumpLines()
        assertContains(
            lines,
            "paint-blend:execution row=gpu-renderer.paint-blend.execution-boundary " +
                "material=${request.paintPlan.materialKey.value} program=program:${request.paintPlan.materialKey.value} " +
                "dictionary=material-dictionary:solid:v1 root=sourceRoot:solid-color snippets=material.solid_color.v1 " +
                "blend=SrcOver plan=FixedFunctionBlend target=rgba8unorm " +
                "blendState=${request.blendGate.blendStateHash} renderKey=$renderKey pipelineCache=$expectedCacheKey " +
                "payload=uniform-fingerprint-solid-paint " +
                "destinationRead=FixedFunctionAttachmentBlend;plan=missing;planStrategy=none;activeAttachmentSampled=false " +
                "blendConstants=none uniformValuesInKey=false destinationResourcesInKey=false " +
                "adapterBacked=false productActivation=true",
        )
        assertContains(
            lines,
            "paint-blend:payload materialKey=${request.paintPlan.materialKey.value} " +
                "payloadPlan=payload:SolidMaterialBlock.color.vec4f32@group1.binding0 " +
                "payloadFingerprint=uniform-fingerprint-solid-paint materialKeyIncludesPayload=false " +
                "pipelineKeyIncludesPayload=false concreteResourcesInKey=false",
        )
        assertContains(
            lines,
            "paint-blend:destination-read " +
                "strategy=FixedFunctionAttachmentBlend;plan=missing;planStrategy=none;activeAttachmentSampled=false",
        )
        assertContains(
            lines,
            "passes.command beginRenderPass target=${request.targetStateHash} loadStore=load-store:load-store",
        )
        assertContains(
            lines,
            "resource.materialization:operand operand=paint-blend-pipeline:${request.paintPlan.materialKey.value} kind=render-pipeline " +
                "deviceGeneration=19 owner=paint-blend-pipeline-cache usage=render " +
                "invalidation=material-dictionary descriptor=$expectedCacheKey " +
                "facts=blend=SrcOver;blendState=${request.blendGate.blendStateHash};dictionary=material-dictionary:solid:v1;" +
                "material=${request.paintPlan.materialKey.value};program=program:${request.paintPlan.materialKey.value};" +
                "root=sourceRoot:solid-color;snippets=material.solid_color.v1;uniformValuesInKey=false",
        )
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.joinToString("\n").contains("@0x"))
    }

    @Test
    fun `paint blend execution refuses shader blend stale key payload and material mismatches`() {
        val shaderBlendGate = GPUBlendAllowlistPlanner().plan(
            blendRequest(
                mode = GPUBlendMode.Screen,
                commandId = "blend:screen",
                destinationReadPlan = destinationReadGate(),
            ),
        )
        val cases = listOf(
            RefusalCase(
                expectedCode = "unsupported.paint_blend.shader_blend_unvalidated",
                request = paintBlendExecutionRequest(blendGate = shaderBlendGate),
                expectedEvidence = "paint-blend:destination-read " +
                    "strategy=TargetCopySnapshot;plan=gpu-renderer.destination-read.strategy:accepted;" +
                    "planStrategy=TargetCopySnapshot;activeAttachmentSampled=false",
            ),
            RefusalCase(
                expectedCode = "unsupported.paint_blend.target_state_mismatch",
                request = paintBlendExecutionRequest(targetStateHash = "target-state:rgba8unorm:stale"),
            ),
            RefusalCase(
                expectedCode = "unsupported.paint_blend.pipeline_cache_key_mismatch",
                request = paintBlendExecutionRequest(pipelineCacheKey = "render-pipeline:render:stale"),
            ),
            RefusalCase(
                expectedCode = "unsupported.paint_blend.material_key_mismatch",
                request = paintBlendExecutionRequest(expectedMaterialKey = "material:other"),
            ),
            RefusalCase(
                expectedCode = "unsupported.paint_blend.uniform_payload_schema_mismatch",
                request = paintBlendExecutionRequest(
                    payloadRequest = solidPayloadRequest(
                        uniformFields = listOf(
                            GPUUniformPayloadField(
                                fieldPath = "color",
                                byteOffset = 0L,
                                byteSize = 12L,
                                valueClass = "vec4<f32>",
                            ),
                        ),
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.resource.command_operand_usage_missing",
                request = paintBlendExecutionRequest(
                    payloadRequest = solidPayloadRequest(availableUniformUsageLabels = setOf("copy_dst")),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.blend.mode_unimplemented",
                request = paintBlendExecutionRequest(
                    blendGate = GPUBlendAllowlistPlanner().plan(
                        blendRequest(mode = GPUBlendMode.Custom, commandId = "blend:custom"),
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.blend.dst_read_requires_intermediate",
                request = paintBlendExecutionRequest(
                    blendGate = GPUBlendAllowlistPlanner().plan(
                        blendRequest(mode = GPUBlendMode.Screen, commandId = "blend:screen"),
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.blend.destination_read_plan_mismatch",
                request = paintBlendExecutionRequest(
                    blendGate = GPUBlendAllowlistPlanner().plan(
                        blendRequest(
                            mode = GPUBlendMode.Screen,
                            commandId = "blend:screen",
                            destinationReadPlan = destinationReadGate(commandId = "blend:other"),
                        ),
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.blend.alpha_plan_unaccepted",
                request = paintBlendExecutionRequest(
                    blendGate = GPUBlendAllowlistPlanner().plan(
                        blendRequest(
                            mode = GPUBlendMode.SrcOver,
                            commandId = "blend:src-over-alpha",
                            alphaPlan = GPUAlphaPlan(
                                inputAlpha = "unpremultiplied",
                                outputAlpha = "premultiplied",
                                premultiply = true,
                                clamp = true,
                            ),
                        ),
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.destination_read.active_attachment_sampled",
                request = paintBlendExecutionRequest(
                    blendGate = GPUBlendAllowlistPlanner().plan(
                        blendRequest(
                            mode = GPUBlendMode.Screen,
                            commandId = "blend:screen-active",
                            activeAttachmentSampled = true,
                        ),
                    ),
                ),
                expectedEvidence = "paint-blend:destination-read " +
                    "strategy=RefuseDiagnostic;plan=missing;planStrategy=none;activeAttachmentSampled=true",
            ),
        )

        cases.forEach { case ->
            val result = ValidatingPaintBlendExecutionBoundary().materialize(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode)
            val dump = result.dumpLines().joinToString("\n")
            case.expectedEvidence?.let { expected -> assertContains(dump, expected) }
            assertFalse(dump.contains("setRenderPipeline"))
            assertFalse(dump.contains("adapterBacked=true"))
        }
    }
}

private data class RefusalCase(
    val expectedCode: String,
    val request: GPUPaintBlendExecutionRequest,
    val expectedEvidence: String? = null,
)

private fun paintBlendExecutionRequest(
    paintPlan: GPUPaintPipelinePlan = solidPaintPlan(),
    materialAssembly: GPUMaterialAssemblyPlan = solidAssembly(paintPlan),
    blendGate: GPUBlendAllowlistGatePlan = fixedFunctionBlendGate(paintPlan),
    payloadRequest: GPUPayloadMaterializationRequest = solidPayloadRequest(),
    expectedMaterialKey: String = paintPlan.materialKey.value,
    pipelineCacheKey: String = GPUPaintBlendExecutionKeys.pipelineCacheKey(
        paintPlan,
        materialAssembly,
        blendGate,
        payloadRequest.reflectedBindingLayoutHash,
    ).value,
    targetStateHash: String = GPUPaintBlendExecutionKeys.targetStateHash(blendGate),
): GPUPaintBlendExecutionRequest =
    GPUPaintBlendExecutionRequest(
        label = "solid-src-over",
        paintPlan = paintPlan,
        materialAssembly = materialAssembly,
        blendGate = blendGate,
        payloadRequest = payloadRequest,
        expectedMaterialKey = expectedMaterialKey,
        expectedDictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
        expectedRootSetId = "sourceRoot:solid-color",
        expectedSnippetIds = listOf(GPUSolidMaterialDictionary.SolidColorSnippetID.value),
        expectedPayloadPlanHash = "payload:SolidMaterialBlock.color.vec4f32@group1.binding0",
        pipelineCacheKey = pipelineCacheKey,
        targetStateHash = targetStateHash,
        loadStoreLabel = "load-store:load-store",
        passId = "pass-paint-blend",
        packetStreamId = "paint-blend:packets",
        streamId = "paint-blend:command-stream",
        vertexSourceLabel = "rect-fill-vertices",
    )

private fun solidPaintPlan(): GPUPaintPipelinePlan =
    GPUSolidMaterialLowering.planPaint(
        descriptor = GPUPaintDescriptor(
            paintId = "paint-solid-src-over",
            source = GPUMaterialSourceDescriptor.Solid(
                GPUSolidColorPlan(
                    r = 0.25f,
                    g = 0.5f,
                    b = 1f,
                    a = 1f,
                    colorSpecLabel = "unpremul-srgb-f32",
                ),
            ),
            blendModeLabel = "SrcOver",
            alpha = 1f,
            colorSpaceLabel = "srgb",
        ),
        context = loweringContext(),
    )

private fun solidAssembly(paintPlan: GPUPaintPipelinePlan): GPUMaterialAssemblyPlan =
    GPUSolidMaterialDictionary.expandSolidMaterial(
        materialKey = paintPlan.materialKey,
        dictionary = GPUSolidMaterialDictionary.create(),
    )

private fun fixedFunctionBlendGate(paintPlan: GPUPaintPipelinePlan): GPUBlendAllowlistGatePlan =
    GPUBlendAllowlistPlanner().plan(
        blendRequest(
            mode = GPUBlendMode.SrcOver,
            commandId = "blend:src-over",
            materialKeyHash = paintPlan.materialKey.value,
        ),
    )

private fun blendRequest(
    mode: GPUBlendMode,
    commandId: String,
    materialKeyHash: String = solidPaintPlan().materialKey.value,
    destinationReadPlan: org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyGatePlan? = null,
    alphaPlan: GPUAlphaPlan = GPUAlphaPlan(
        inputAlpha = "premultiplied",
        outputAlpha = "premultiplied",
        premultiply = false,
        clamp = true,
    ),
    activeAttachmentSampled: Boolean = false,
): GPUBlendAllowlistRequest =
    GPUBlendAllowlistRequest(
        commandId = commandId,
        mode = mode,
        targetFormatClass = "rgba8unorm",
        materialKeyHash = materialKeyHash,
        renderStepIdentity = "rect-fill",
        alphaPlan = alphaPlan,
        destinationReadPlan = destinationReadPlan,
        destinationReadCopyBoundsLabel = destinationReadPlan?.plan?.bounds?.copyBoundsLabel,
        destinationReadGeneration = destinationReadPlan?.plan?.binding?.generation,
        activeAttachmentSampled = activeAttachmentSampled,
    )

private fun destinationReadGate(commandId: String = "blend:screen") =
    GPUDestinationReadStrategyPlanner().plan(
        GPUDestinationReadStrategyRequest(
            label = "accepted",
            commandId = commandId,
            requirement = GPUDestinationReadRequirement.TargetCopy,
            strategy = GPUDestinationReadStrategy.CopyTarget,
            action = GPUDestinationReadAction.SplitPassAndCopyTarget,
            bounds = GPUDestinationReadBounds(
                boundsLabel = "shape-local",
                conservative = true,
                pixelAligned = true,
                requestedBoundsLabel = "shape-local",
                unclippedBoundsLabel = "0,0,80,40",
                clippedBoundsLabel = "4,8,64,32",
                copyBoundsLabel = "4,8,64,32",
                originX = 4,
                originY = 8,
                width = 64,
                height = 32,
                targetWidth = 256,
                targetHeight = 128,
            ),
            sourceTargetLabel = "target:main",
            sourceUsageLabels = setOf("render_attachment", "copy_src"),
            copyUsageLabels = setOf("copy_dst", "texture_binding"),
            targetFormatClass = "rgba8unorm",
            targetGeneration = 42,
        ),
    )

private fun solidPayloadRequest(
    availableUniformUsageLabels: Set<String> = setOf("copy_dst", "uniform"),
    uniformFields: List<GPUUniformPayloadField> = listOf(
        GPUUniformPayloadField(
            fieldPath = "color",
            byteOffset = 0L,
            byteSize = 16L,
            valueClass = "vec4<f32>",
        ),
    ),
): GPUPayloadMaterializationRequest =
    GPUPayloadMaterializationRequest(
        targetId = "root-target",
        packetId = "paint-blend:solid-src-over:draw",
        taskIds = listOf("task-paint-blend"),
        resourcePlanLabels = listOf("paint-blend:solid-src-over"),
        uniformBlock = GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-solid-paint"),
            packingPlanHash = GPUSolidMaterialDictionary.SolidMaterialLayoutHash,
            byteSize = 16L,
            zeroedPadding = true,
            scope = "pass-paint-blend",
            bytes = listOf(0, 0, 128, 62, 0, 0, 0, 63, 0, 0, 128, 63, 0, 0, 128, 63),
            fields = uniformFields,
        ),
        uniformSlot = GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("pass-paint-blend:uniform:0"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-solid-paint"),
            byteOffset = 0L,
        ),
        resourceBlock = GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-solid-paint"),
            bindingPlanHash = GPUSolidMaterialDictionary.SolidMaterialLayoutHash,
            bindingCount = 1,
            resourceDescriptorLabels = listOf("uniform:SolidMaterialBlock"),
            dynamicOffsets = emptyList(),
        ),
        resourceSlot = GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("pass-paint-blend:resource:0"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-solid-paint"),
            bindingIndex = 0,
        ),
        uploadPlan = GPUPayloadUploadPlan(
            planHash = "upload-solid-paint-v1",
            byteRanges = listOf(0L..15L),
            stagingScope = "pass-paint-blend-staging",
            budgetClass = "unit-test",
            beforeUseToken = "before-paint-blend-draw",
        ),
        reflectedBindingLayoutHash = GPUSolidMaterialDictionary.SolidMaterialLayoutHash,
        deviceGeneration = 19L,
        payloadGeneration = 3L,
        alignmentBytes = 16L,
        uploadBudgetBytes = 64L,
        uploadCapabilityAvailable = true,
        maxDynamicOffsets = 0,
        requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
        availableUniformUsageLabels = availableUniformUsageLabels,
    )

private fun loweringContext(): GPUMaterialLoweringContext =
    GPUMaterialLoweringContext(
        capabilityClass = "first-route-solid",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
    )

private fun targetPreparationContext(): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 19L,
        budgetClass = "unit-test",
    )
