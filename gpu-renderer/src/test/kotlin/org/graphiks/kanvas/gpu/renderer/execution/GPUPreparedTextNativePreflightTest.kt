package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedColorGlyphPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextA8PayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

class GPUPreparedTextNativePreflightTest {
    @Test
    fun `accepted TextA8 frame passes pure prepared surface preflight`() {
        val fixture = preparedTextNativePreflightFixture()

        assertNull(
            GPUPreparedSurfaceNativePreflight()
                .validateFramePlan(
                    fixture.framePlan,
                    fixture.context,
                    fixture.capabilities,
                ),
        )
    }

    @Test
    fun `accepted ColorGlyph frame passes the same pure prepared surface preflight`() {
        val fixture = preparedTextNativePreflightFixture(includeColorGlyph = true)

        assertNull(
            GPUPreparedSurfaceNativePreflight()
                .validateFramePlan(
                    fixture.framePlan,
                    fixture.context,
                    fixture.capabilities,
                ),
        )
    }

    @Test
    fun `stale atlas generation refuses before native creation`() {
        val fixture = preparedTextNativePreflightFixture()
        val atlasTexture = fixture.framePlan.steps
            .filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.PrepareResourcesStep>()
            .flatMap { step -> step.requests }
            .single { request ->
                request.role ==
                    org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.GlyphAtlas
            }
            .resource
        val staleContext = GPUFramePreflightContext(
            targetId = fixture.context.targetId,
            deviceGeneration = fixture.context.deviceGeneration,
            targetGeneration = fixture.context.targetGeneration,
            resourceGenerations = fixture.context.resourceGenerations +
                (atlasTexture to (GPUPreparedTextPreflightFixture.GENERATION + 1L)),
        )
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(fixture, staleContext),
        )

        assertEquals(
            GPUPreparedTextPreflightRefusalCodes.STALE_ATLAS_GENERATION,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `accepted pure text preflight stops at Task 10 guard before native creation`() {
        val fixture = preparedTextNativePreflightFixture()
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(fixture),
        )

        assertEquals(
            GPUPreparedTextPreflightRefusalCodes.PREPARED_TEXT_UNMATERIALIZED,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `global pure validation still precedes the Task 10 guard`() {
        val fixture = preparedTextNativePreflightFixture()
        val mutated = fixture.copy(
            framePlan = fixture.framePlan.rebuilt(
                steps = fixture.framePlan.steps.map { step ->
                    if (step is GPUFrameStep.PrepareResourcesStep) {
                        val duplicated = step.requests.single { request ->
                            request.role == GPUFrameResourceRole.GlyphAtlas
                        }
                        GPUFrameStep.PrepareResourcesStep(
                            requests = step.requests + duplicated,
                            sourceTaskIds = step.sourceTaskIds,
                        )
                    } else {
                        step
                    }
                },
            ),
        )
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(mutated),
        )

        assertEquals(
            "invalid.preflight.resource_preparation_duplicate",
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `material uniform buffer substitution refuses before native creation`() {
        val fixture = preparedTextNativePreflightFixture()
        val originalBinding = fixture.framePlan.preparedTextBindings().first()
        val originalPlan = requireNotNull(originalBinding.materialUniformBufferPlan)
        val forgedBytes = originalPlan.bytesForUpload().also { bytes ->
            bytes[originalBinding.materialUniformOffsetBytes.toInt()] =
                (bytes[originalBinding.materialUniformOffsetBytes.toInt()].toInt() xor 0x7f)
                    .toByte()
        }
        val forgedPlan =
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextMaterialUniformBufferPlan(
                bufferRef = originalPlan.bufferRef,
                alignmentBytes = originalPlan.alignmentBytes,
                byteSize = originalPlan.byteSize,
                contentHash = forgedBytes.sha256(),
                uploadBytes = forgedBytes,
            )
        val mutated = fixture.withBindingMutation { index, binding ->
            if (index == 0) {
                binding.rebuilt(materialUniformBufferPlan = forgedPlan)
            } else {
                binding
            }
        }
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(mutated),
        )

        assertEquals(
            GPUPreparedTextPreflightRefusalCodes.MATERIAL_UNIFORMS,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `material sampled plan substitution refuses before native creation`() {
        val fixture = preparedTextNativePreflightFixture(
            materialProgram = sampledPreparedTextMaterialProgram("source:primary"),
        )
        val foreign = preparedTextNativePreflightFixture(
            materialProgram = sampledPreparedTextMaterialProgram("source:foreign"),
        )
        val foreignPlan = foreign.framePlan.preparedTextBindings().first()
            .materialSampledResourcePlans
            .single()
        val mutated = fixture.withBindingMutation { index, binding ->
            if (index == 0) {
                binding.rebuilt(materialSampledResourcePlans = listOf(foreignPlan))
            } else {
                binding
            }
        }
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(mutated),
        )

        assertEquals(
            GPUPreparedTextPreflightRefusalCodes.MATERIAL_RESOURCES,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `missing sampled material upload refuses before native creation`() {
        val fixture = preparedTextNativePreflightFixture(
            materialProgram = sampledPreparedTextMaterialProgram("source:missing-upload"),
        )
        val mutated = fixture.copy(
            framePlan = fixture.framePlan.rebuilt(
                steps = fixture.framePlan.steps.filterNot { step ->
                    step is GPUFrameStep.UploadResourceStep &&
                        step.materialResourcePlan != null
                },
            ),
        )
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(mutated),
        )

        assertEquals(
            GPUPreparedTextPreflightRefusalCodes.UPLOAD_MISSING,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `sampled material duplicate and late uploads refuse before native creation`() {
        val fixture = preparedTextNativePreflightFixture(
            materialProgram = sampledPreparedTextMaterialProgram("source:upload-order"),
        )
        val upload = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { it.materialResourcePlan != null }
        val duplicated = fixture.copy(
            framePlan = fixture.framePlan.rebuilt(
                steps = fixture.framePlan.steps.toMutableList().apply {
                    add(indexOf(upload) + 1, upload)
                },
            ),
        )
        val withoutUpload = fixture.framePlan.steps.filterNot { it === upload }.toMutableList()
        val firstRenderIndex = withoutUpload.indexOfFirst {
            it is GPUFrameStep.RenderPassStep
        }
        val late = fixture.copy(
            framePlan = fixture.framePlan.rebuilt(
                steps = withoutUpload.apply { add(firstRenderIndex + 1, upload) },
            ),
        )

        listOf(
            duplicated to GPUPreparedTextPreflightRefusalCodes.UPLOAD_DUPLICATE,
            late to GPUPreparedTextPreflightRefusalCodes.UPLOAD_ORDER,
        ).forEach { (mutated, expectedCode) ->
            val probe = GPUPreparedTextNativeCreationProbe()
            val refused = assertIs<GPUFramePreflightResult.Refused>(
                probe.preflight(mutated),
            )
            assertEquals(expectedCode, refused.diagnostic.code.value)
            assertEquals(0, probe.totalCreations)
        }
    }

    @Test
    fun `every sampled material operand retains exact ownership before native creation`() {
        val fixture = preparedTextNativePreflightFixture(
            materialProgram = sampledPreparedTextMaterialProgram("source:ownership"),
        )
        val binding = fixture.framePlan.preparedTextBindings().first()
        val resourcesAndUsages = listOf(
            requireNotNull(binding.materialUniformBufferPlan).bufferRef to
                setOf(
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUFrameResourceUsage.CopyDestination,
                ),
            binding.materialSampledResourcePlans.single().stagingRef to
                setOf(
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUFrameResourceUsage.CopyDestination,
                ),
            binding.materialSampledResourcePlans.single().frameTextureRef to
                setOf(
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUFrameResourceUsage.TextureBinding,
                ),
        )

        resourcesAndUsages.forEach { (resource, forgedUsages) ->
            val mutated = fixture.copy(
                framePlan = fixture.framePlan.withPreparationMutation(resource) { request ->
                    request.rebuilt(usages = forgedUsages)
                },
            )
            val probe = GPUPreparedTextNativeCreationProbe()
            val refused = assertIs<GPUFramePreflightResult.Refused>(
                probe.preflight(mutated),
            )
            assertEquals(
                GPUPreparedTextPreflightRefusalCodes.OPERAND_OWNERSHIP,
                refused.diagnostic.code.value,
            )
            assertEquals(0, probe.totalCreations)
        }
    }

    @Test
    fun `overflowing sealed instance range refuses before native creation`() {
        val fixture = preparedTextNativePreflightFixture()
            .withBindingSealMutation { index, seal ->
                if (index == 0) {
                    seal.rebuilt(firstInstance = Int.MAX_VALUE)
                } else {
                    seal
                }
            }
        val probe = GPUPreparedTextNativeCreationProbe()

        val refused = assertIs<GPUFramePreflightResult.Refused>(
            probe.preflight(fixture),
        )

        assertEquals(
            GPUPreparedTextPreflightRefusalCodes.INSTANCE_BUFFER_RANGE,
            refused.diagnostic.code.value,
        )
        assertEquals(0, probe.totalCreations)
    }

    @Test
    fun `prepared text preflight seal participates in canonical frame identity`() {
        val fixture = preparedTextNativePreflightFixture()
        val mutated = fixture.withBindingSealMutation { index, seal ->
            if (index == 0) {
                seal.rebuilt(
                    instanceStrideBytes = GPUTextA8Instance.ENCODED_BYTE_SIZE + 4,
                )
            } else {
                seal
            }
        }

        assertNotEquals(fixture.framePlan.stableHash(), mutated.framePlan.stableHash())
        assertNotEquals(fixture.framePlan.dumpLines(), mutated.framePlan.dumpLines())
    }

    @TestFactory
    fun `every prepared text mutation refuses with its exact code before native creation`():
        List<DynamicTest> =
        GPUPreparedTextPreflightMutationMatrix.orderedMutations.map { mutation ->
            DynamicTest.dynamicTest(mutation.name) {
                val mutated = preparedTextNativePreflightFixture()
                    .withViolation(mutation.violationKind)
                val probe = GPUPreparedTextNativeCreationProbe()

                val refused = assertIs<GPUFramePreflightResult.Refused>(
                    probe.preflight(mutated),
                )

                assertEquals(
                    expectedPreparedTextRefusalCode(mutation.violationKind),
                    refused.diagnostic.code.value,
                )
                assertEquals(0, probe.totalCreations)
            }
        }
}

private class GPUPreparedTextNativeCreationProbe {
    private val adapter = GPURuntimeResourceAdapter()
    private val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
    private val materializer = CapturingPreparedNativeMaterializer()

    val totalCreations: Int
        get() = materializer.materializeCallCount + provider.telemetry.dumpEvents.size

    fun preflight(
        fixture: PreparedTextNativePreflightFixture,
        context: GPUFramePreflightContext = fixture.context,
    ): GPUFramePreflightResult =
        try {
            GPUFramePreflighter(
                context = context,
                capabilities = fixture.capabilities,
                resourceProvider = provider,
                completionProvider = PreparedTextFailingCompletionProvider,
                surfaceProvider = PreparedTextFailingSurfaceProvider,
                nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
            ).preflight(fixture.framePlan)
        } finally {
            adapter.close()
        }
}

private fun PreparedTextNativePreflightFixture.withViolation(
    kind: GPUPreparedTextViolationKind,
): PreparedTextNativePreflightFixture = when (kind) {
    GPUPreparedTextViolationKind.STALE_ATLAS_GENERATION -> {
        val atlasTexture = framePlan.preparedTextAtlasRequest().resource
        copy(
            context = context.withResourceGeneration(
                atlasTexture,
                GPUPreparedTextPreflightFixture.GENERATION + 1L,
            ),
        )
    }
    GPUPreparedTextViolationKind.MODIFIED_PAGE_BYTES ->
        withTextSemantic { semantic ->
            semantic.rebuilt(
                atlas = semantic.atlas.rebuilt(
                    bytes = semantic.atlas.tightBytesForUpload().also {
                        it[0] = (it[0].toInt() xor 0x7f).toByte()
                    },
                ),
            )
        }
    GPUPreparedTextViolationKind.MODIFIED_PAGE_DIMENSIONS ->
        withTextSemantic { semantic ->
            semantic.rebuilt(
                atlas = semantic.atlas.rebuilt(width = semantic.atlas.width / 2),
            )
        }
    GPUPreparedTextViolationKind.MODIFIED_PAGE_ROW_BYTES ->
        withTextSemantic { semantic ->
            semantic.rebuilt(
                atlas = semantic.atlas.rebuilt(
                    rowBytes = semantic.atlas.rowBytes + 1,
                    bytes = ByteArray(
                        (semantic.atlas.rowBytes + 1) * semantic.atlas.height,
                    ) { index -> index.toByte() },
                ),
            )
        }
    GPUPreparedTextViolationKind.R8UNORM_UNSUPPORTED ->
        copy(
            capabilities = capabilities.copy(
                supportedTextureFormats =
                    capabilities.supportedTextureFormats - GPUTextureFormat.R8Unorm,
            ),
        )
    GPUPreparedTextViolationKind.INSTANCE_UV_INVALID ->
        withTextSemantic { semantic ->
            val first = semantic.instances.first()
            semantic.rebuilt(
                instances = listOf(
                    GPUTextA8Instance.create(
                        glyphId = first.glyphId,
                        sourceGlyphIndex = first.sourceGlyphIndex,
                        deviceQuad = first.deviceQuad,
                        uvRect = GPUTextFloatRect(-0.25f, 0f, 0.25f, 0.25f),
                        pageIndex = first.pageIndex,
                        colorLayerIndex = first.colorLayerIndex,
                    ),
                ) + semantic.instances.drop(1),
            )
        }
    GPUPreparedTextViolationKind.INSTANCE_STRIDE_INCORRECT ->
        withBindingSealMutation { index, seal ->
            if (index == 0) {
                seal.rebuilt(
                    instanceStrideBytes = GPUTextA8Instance.ENCODED_BYTE_SIZE + 4,
                )
            } else {
                seal
            }
        }
    GPUPreparedTextViolationKind.INSTANCE_RANGES_OVERLAPPING ->
        withBindingSealMutation { index, seal ->
            if (index == 1) seal.rebuilt(firstInstance = 1) else seal
        }
    GPUPreparedTextViolationKind.INSTANCE_COUNT_OUT_OF_BUFFER ->
        withBindingSealMutation { index, seal ->
            if (index == 1) {
                seal.rebuilt(
                    instanceCount = framePlan.preparedTextBindings().first()
                        .instanceBufferPlan.instanceCount,
                )
            } else {
                seal
            }
        }
    GPUPreparedTextViolationKind.MATERIAL_ABI_MISMATCH ->
        withTextSemantic { semantic ->
            semantic.rebuilt(material = semantic.material.rebuilt(abiHash = "abi:forged"))
        }
    GPUPreparedTextViolationKind.WGSL_ENTRY_POINT_INCORRECT ->
        withTextSemantic { semantic ->
            semantic.rebuilt(material = semantic.material.rebuilt(entryPoint = "forged_entry"))
        }
    GPUPreparedTextViolationKind.BINDING_LAYOUT_INCORRECT ->
        withTextSemantic { semantic ->
            semantic.rebuilt(
                material = semantic.material.rebuilt(
                    wgslSource =
                        "struct ForgedBinding { value: vec4f, };\n" +
                            "@group(3) @binding(9) var<uniform> forged: ForgedBinding;\n" +
                            semantic.material.wgslSource,
                ),
            )
        }
    GPUPreparedTextViolationKind.MATERIAL_UNIFORMS_MODIFIED ->
        withTextSemantic { semantic ->
            semantic.rebuilt(
                material = semantic.material.rebuilt(
                    uniformBytes = semantic.material.uniformBytes.mapIndexed { index, value ->
                        if (index == 0) value xor 0x7f else value
                    },
                ),
            )
        }
    GPUPreparedTextViolationKind.MATERIAL_RESOURCES_MODIFIED ->
        withTextSemantic { semantic ->
            val forgedResource =
                org.graphiks.kanvas.gpu.renderer.materials
                    .GPUPreparedMaterialSampledResource(
                        width = 1,
                        height = 1,
                        samplingFilterMode = "nearest",
                        alphaOnly = false,
                        rgba8Bytes = byteArrayOf(1, 2, 3, 4),
                        resourceKey = "material:forged-resource",
                    )
            // The authenticated DTO intentionally makes this invalid state
            // unconstructible. Reflection is confined to this negative
            // preflight fixture so the downstream integrity gate remains
            // covered against post-construction memory corruption.
            semantic.material.javaClass.getDeclaredField("sampledResources").run {
                isAccessible = true
                set(semantic.material, listOf(forgedResource))
            }
            semantic
        }
    GPUPreparedTextViolationKind.UPLOAD_MISSING ->
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.filterNot { step ->
                    step is GPUFrameStep.UploadResourceStep && step.r8ResourcePlan != null
                },
            ),
        )
    GPUPreparedTextViolationKind.UPLOAD_DUPLICATED -> {
        val upload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { it.r8ResourcePlan != null }
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.toMutableList().apply {
                    add(indexOf(upload) + 1, upload)
                },
            ),
        )
    }
    GPUPreparedTextViolationKind.UPLOAD_AFTER_FIRST_CONSUMER -> {
        val upload = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single { it.r8ResourcePlan != null }
        val renderIndex = framePlan.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep }
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.filterNot { it === upload }.toMutableList().apply {
                    add(renderIndex + 1, upload)
                },
            ),
        )
    }
    GPUPreparedTextViolationKind.TARGET_MODIFIED ->
        withTextSemantic { semantic ->
            semantic.rebuilt(
                targetBounds = GPUPixelBounds(0, 0, 8, 8),
                scissorBounds = GPUPixelBounds(0, 0, 8, 8),
            )
        }
    GPUPreparedTextViolationKind.SCISSOR_MODIFIED ->
        withTextSemantic { semantic ->
            semantic.rebuilt(scissorBounds = GPUPixelBounds(1, 1, 8, 8))
        }
    GPUPreparedTextViolationKind.CLIP_MODIFIED ->
        withTextSemantic { semantic ->
            semantic.rebuilt(clipIdentity = "clip:forged")
        }
    GPUPreparedTextViolationKind.BLEND_MODIFIED ->
        withTextSemantic { semantic ->
            semantic.rebuilt(blendPlanIdentity = "blend:forged")
        }
    GPUPreparedTextViolationKind.RESOURCE_LIFETIME_NOT_FRAME_LOCAL -> {
        val atlas = framePlan.preparedTextAtlasRequest()
        copy(
            framePlan = framePlan.withPreparationMutation(atlas.resource) { request ->
                request.rebuilt(lifetime = GPUFrameResourceLifetime.SharedCache)
            },
        )
    }
    GPUPreparedTextViolationKind.DEPENDENCY_KEY_INCORRECT ->
        copy(
            framePlan = framePlan.rebuilt(
                steps = framePlan.steps,
                dependencies = framePlan.dependencies.mapIndexed { index, dependency ->
                    if (index == 0) {
                        GPUTaskDependency(
                            fromTaskId = dependency.fromTaskId,
                            toTaskId = dependency.toTaskId,
                            dependencyKind = dependency.dependencyKind,
                            useToken = dependency.useToken,
                            reasonCode = "prepared.text.forged-reason",
                        )
                    } else {
                        dependency
                    }
                },
            ),
        )
    GPUPreparedTextViolationKind.OPERAND_KEY_INCORRECT ->
        copy(
            framePlan = framePlan.rebuilt(
                framePlan.steps.mapIndexed { index, step ->
                    if (step is GPUFrameStep.RenderPassStep &&
                        step.preparedTextBindingsByPacketId.isNotEmpty() &&
                        framePlan.steps.take(index).none { prior ->
                            prior is GPUFrameStep.RenderPassStep &&
                                prior.preparedTextBindingsByPacketId.isNotEmpty()
                        }
                    ) {
                        step.rebuilt(resourceUses = step.resourceUses.dropLast(1))
                    } else {
                        step
                    }
                },
            ),
        )
    GPUPreparedTextViolationKind.OPERAND_OWNERSHIP_INCORRECT ->
        withPreparationMutationForRole(GPUFrameResourceRole.GlyphAtlas) { request ->
            request.rebuilt(usages = setOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding))
        }
    GPUPreparedTextViolationKind.TEXTURE_LIMIT_EXCEEDED ->
        copy(
            capabilities = capabilities.copy(
                limits = requireNotNull(capabilities.limits).copy(maxTextureDimension2D = 4),
            ),
        )
    GPUPreparedTextViolationKind.INSTANCE_BUFFER_LIMIT_EXCEEDED ->
        copy(
            capabilities = capabilities.copy(
                limits = requireNotNull(capabilities.limits).copy(maxBufferSize = 32),
            ),
        )
    GPUPreparedTextViolationKind.COPY_ALIGNMENT_UNMET ->
        copy(
            capabilities = capabilities.copy(
                limits = requireNotNull(capabilities.limits).copy(
                    copyBytesPerRowAlignment = 512,
                ),
            ),
        )
}

private fun PreparedTextNativePreflightFixture.withTextSemantic(
    transform: (GPUDrawSemanticPayload.TextA8) -> GPUDrawSemanticPayload.TextA8,
): PreparedTextNativePreflightFixture = copy(
    framePlan = framePlan.rebuilt(
        framePlan.steps.map { step ->
            if (step !is GPUFrameStep.RenderPassStep) {
                step
            } else {
                val packets = step.drawPackets.map { packet ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.TextA8
                        ?: return@map packet
                    packet.rebuilt(semanticPayload = transform(semantic))
                }
                step.rebuilt(drawPackets = packets)
            }
        },
    ),
)

private fun PreparedTextNativePreflightFixture.withBindingSealMutation(
    transform: (
        index: Int,
        seal: org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal,
    ) -> org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal,
): PreparedTextNativePreflightFixture {
    var bindingIndex = 0
    return copy(
        framePlan = framePlan.rebuilt(
            framePlan.steps.map { step ->
                if (step !is GPUFrameStep.RenderPassStep ||
                    step.preparedTextBindingsByPacketId.isEmpty()
                ) {
                    step
                } else {
                    step.rebuilt(
                        preparedTextBindingsByPacketId =
                            step.preparedTextBindingsByPacketId.mapValues { (_, binding) ->
                                val seal = binding.preflightSeal
                                val mutatedSeal = transform(bindingIndex++, seal)
                                binding.rebuilt(preflightSeal = mutatedSeal)
                            },
                    )
                }
            },
        ),
    )
}

private fun PreparedTextNativePreflightFixture.withBindingMutation(
    transform: (
        index: Int,
        binding:
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding,
    ) -> org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding,
): PreparedTextNativePreflightFixture {
    var bindingIndex = 0
    return copy(
        framePlan = framePlan.rebuilt(
            framePlan.steps.map { step ->
                if (step !is GPUFrameStep.RenderPassStep ||
                    step.preparedTextBindingsByPacketId.isEmpty()
                ) {
                    step
                } else {
                    step.rebuilt(
                        preparedTextBindingsByPacketId =
                            step.preparedTextBindingsByPacketId.mapValues { (_, binding) ->
                                transform(bindingIndex++, binding)
                            },
                    )
                }
            },
        ),
    )
}

private fun GPUFramePlan.preparedTextBindings() =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap { it.preparedTextBindingsByPacketId.values }

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding.rebuilt(
    materialUniformBufferPlan:
        org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextMaterialUniformBufferPlan? =
        this.materialUniformBufferPlan,
    materialSampledResourcePlans:
        List<org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan> =
        this.materialSampledResourcePlans,
    preflightSeal:
        org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal =
        this.preflightSeal,
) = org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding(
    packetId = packetId,
    atlasResourcePlan = atlasResourcePlan,
    instanceBufferPlan = instanceBufferPlan,
    firstInstance = firstInstance,
    instanceCount = instanceCount,
    materialUniformBufferPlan = materialUniformBufferPlan,
    materialUniformOffsetBytes = materialUniformOffsetBytes,
    materialUniformSizeBytes = materialUniformSizeBytes,
    materialSampledResourcePlans = materialSampledResourcePlans,
    preflightSeal = preflightSeal,
)

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal.rebuilt(
    instanceStrideBytes: Int = this.instanceStrideBytes,
    firstInstance: Int = this.firstInstance,
    instanceCount: Int = this.instanceCount,
    materialUniformOffsetBytes: Long = this.materialUniformOffsetBytes,
) = org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal(
    semanticCanonicalHash = semanticCanonicalHash,
    atlasKey = atlasKey,
    atlasWidth = atlasWidth,
    atlasHeight = atlasHeight,
    atlasRowBytes = atlasRowBytes,
    atlasGeneration = atlasGeneration,
    atlasContentHash = atlasContentHash,
    pageIndex = pageIndex,
    instanceStrideBytes = instanceStrideBytes,
    firstInstance = firstInstance,
    instanceCount = instanceCount,
    instanceBufferByteSize = instanceBufferByteSize,
    instanceBufferContentHash = instanceBufferContentHash,
    materialUniformOffsetBytes = materialUniformOffsetBytes,
    materialUniformSizeBytes = materialUniformSizeBytes,
    materialKey = materialKey,
    materialWgslSourceHash = materialWgslSourceHash,
    materialEntryPoint = materialEntryPoint,
    materialAbiHash = materialAbiHash,
    materialUniformContentHash = materialUniformContentHash,
    materialSampledResourceFacts = materialSampledResourceFacts,
    targetBounds = targetBounds,
    scissorBounds = scissorBounds,
    clipIdentity = clipIdentity,
    blendPlanIdentity = blendPlanIdentity,
    capabilitySnapshotHash = capabilitySnapshotHash,
)

private fun PreparedTextNativePreflightFixture.withPreparationMutationForRole(
    role: GPUFrameResourceRole,
    transform: (GPUResourcePreparationRequest) -> GPUResourcePreparationRequest,
): PreparedTextNativePreflightFixture {
    val resource = framePlan.steps
        .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap { it.requests }
        .single { it.role == role }
        .resource
    return copy(framePlan = framePlan.withPreparationMutation(resource, transform))
}

private fun GPUFramePlan.withPreparationMutation(
    resource: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef,
    transform: (GPUResourcePreparationRequest) -> GPUResourcePreparationRequest,
): GPUFramePlan = rebuilt(
    steps.map { step ->
        if (step !is GPUFrameStep.PrepareResourcesStep) {
            step
        } else {
            GPUFrameStep.PrepareResourcesStep(
                requests = step.requests.map { request ->
                    if (request.resource == resource) transform(request) else request
                },
                sourceTaskIds = step.sourceTaskIds,
            )
        }
    },
)

private fun GPUFramePreflightContext.withResourceGeneration(
    resource: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef,
    generation: Long,
): GPUFramePreflightContext = GPUFramePreflightContext(
    targetId = targetId,
    deviceGeneration = deviceGeneration,
    targetGeneration = targetGeneration,
    resourceGenerations = resourceGenerations + (resource to generation),
    surfaceGeneration = surfaceGeneration,
)

private fun GPUFramePlan.preparedTextAtlasRequest(): GPUResourcePreparationRequest =
    steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap { it.requests }
        .single { it.role == GPUFrameResourceRole.GlyphAtlas }

private fun GPUPreparedR8UploadArtifact.rebuilt(
    width: Int = this.width,
    height: Int = this.height,
    rowBytes: Int = this.rowBytes,
    bytes: ByteArray = tightBytesForUpload(),
): GPUPreparedR8UploadArtifact = GPUPreparedR8UploadArtifact(
    key = key,
    width = width,
    height = height,
    rowBytes = rowBytes,
    generation = generation,
    contentHash = bytes.sha256(),
    bytes = bytes,
)

private fun org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram.rebuilt(
    materialKey: String = this.materialKey,
    wgslSource: String = this.wgslSource,
    entryPoint: String = this.entryPoint,
    composableFragment:
        org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment =
        this.composableFragment,
    uniformBytes: List<Int> = this.uniformBytes,
    sampledResources:
        List<org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialSampledResource> =
        this.sampledResources,
    paintAlpha: Float = this.paintAlpha,
    sourceKind: org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceKind =
        this.sourceKind,
    abiHash: String = this.abiHash,
): org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
    org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram(
        materialKey = materialKey,
        wgslSource = wgslSource,
        entryPoint = entryPoint,
        composableFragment = composableFragment,
        uniformBytes = uniformBytes,
        sampledResources = sampledResources,
        paintAlpha = paintAlpha,
        sourceKind = sourceKind,
        abiHash = abiHash,
        expectedFragmentIdentity = this.composableFragment.authenticatedIdentity,
    )

private fun GPUDrawSemanticPayload.TextA8.rebuilt(
    atlas: GPUPreparedR8UploadArtifact = this.atlas,
    instances: List<GPUTextA8Instance> = this.instances,
    material: org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
        this.material,
    targetBounds: GPUPixelBounds = this.targetBounds,
    scissorBounds: GPUPixelBounds = this.scissorBounds,
    clipIdentity: String = this.clipIdentity,
    blendPlanIdentity: String = this.blendPlanIdentity,
): GPUDrawSemanticPayload.TextA8 = GPUPreparedTextPayloadGatherer().gather(
    GPUPreparedTextA8PayloadInput(
        commandIdValue = payloadRef.commandIdValue,
        atlas = atlas,
        atlasGeneration = atlasGeneration,
        pageIndex = pageIndex,
        instances = instances,
        material = material,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipIdentity = clipIdentity,
        blendPlanIdentity = blendPlanIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        frameProvenance = frameProvenance,
    ),
)

private fun GPUFramePlan.rebuilt(
    steps: List<GPUFrameStep>,
    dependencies: List<GPUTaskDependency> = this.dependencies,
): GPUFramePlan =
    GPUFramePlan(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        steps = steps,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        elidedNoOpDraws = elidedNoOpDraws,
        atomicallyRefused = atomicallyRefused,
    )

private fun GPUFrameStep.RenderPassStep.rebuilt(
    drawPackets: List<GPUDrawPacket> = this.drawPackets,
    resourceUses:
        List<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse> =
        this.resourceUses,
    preparedTextBindingsByPacketId:
        Map<GPUDrawPacketID, org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding> =
        this.preparedTextBindingsByPacketId,
): GPUFrameStep.RenderPassStep = GPUFrameStep.RenderPassStep(
    target = target,
    loadStore = loadStore,
    samplePlan = samplePlan,
    resourceUses = resourceUses,
    drawPackets = drawPackets,
    sourceTaskIds = sourceTaskIds,
    batches = batches,
    sampleContinuation = sampleContinuation,
    depthStencilLoadStore = depthStencilLoadStore,
    preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
    preparedTextBindingsByPacketId = preparedTextBindingsByPacketId,
)

private fun GPUDrawPacket.rebuilt(
    semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
): GPUDrawPacket = GPUDrawPacket(
    packetId = packetId,
    commandIdValue = commandIdValue,
    analysisRecordId = analysisRecordId,
    passId = passId,
    layerId = layerId,
    bindingListId = bindingListId,
    insertionReasonCode = insertionReasonCode,
    sortKey = sortKey,
    sortKeyPreimage = sortKeyPreimage,
    renderStepId = renderStepId,
    renderStepVersion = renderStepVersion,
    role = role,
    blendPlan = blendPlan,
    renderPipelineKey = renderPipelineKey,
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = bindingLayoutHash,
    uniformSlot = uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semanticPayload,
    vertexSourceLabel = vertexSourceLabel,
    scissorBoundsHash = scissorBoundsHash,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoveragePlan,
    clipExecutionPlan = clipExecutionPlan,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun GPUResourcePreparationRequest.rebuilt(
    lifetime: GPUFrameResourceLifetime = this.lifetime,
    usages: Set<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage> =
        this.usages,
): GPUResourcePreparationRequest = GPUResourcePreparationRequest(
    resource = resource,
    descriptor = when (val value = descriptor) {
        is GPUFrameBufferDescriptor -> value.copy()
        is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor -> value.copy()
    },
    role = role,
    usages = usages,
    lifetime = lifetime,
    byteSize = byteSize,
    diagnosticLabel = diagnosticLabel,
)

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun expectedPreparedTextRefusalCode(kind: GPUPreparedTextViolationKind): String =
    when (kind) {
        GPUPreparedTextViolationKind.STALE_ATLAS_GENERATION ->
            GPUPreparedTextPreflightRefusalCodes.STALE_ATLAS_GENERATION
        GPUPreparedTextViolationKind.MODIFIED_PAGE_BYTES ->
            GPUPreparedTextPreflightRefusalCodes.PAGE_BYTES
        GPUPreparedTextViolationKind.MODIFIED_PAGE_DIMENSIONS ->
            GPUPreparedTextPreflightRefusalCodes.PAGE_DIMENSIONS
        GPUPreparedTextViolationKind.MODIFIED_PAGE_ROW_BYTES ->
            GPUPreparedTextPreflightRefusalCodes.PAGE_ROW_BYTES
        GPUPreparedTextViolationKind.R8UNORM_UNSUPPORTED ->
            GPUPreparedTextPreflightRefusalCodes.R8UNORM
        GPUPreparedTextViolationKind.INSTANCE_UV_INVALID ->
            GPUPreparedTextPreflightRefusalCodes.INSTANCE_UV
        GPUPreparedTextViolationKind.INSTANCE_STRIDE_INCORRECT ->
            GPUPreparedTextPreflightRefusalCodes.INSTANCE_STRIDE
        GPUPreparedTextViolationKind.INSTANCE_RANGES_OVERLAPPING ->
            GPUPreparedTextPreflightRefusalCodes.INSTANCE_RANGE_OVERLAP
        GPUPreparedTextViolationKind.INSTANCE_COUNT_OUT_OF_BUFFER ->
            GPUPreparedTextPreflightRefusalCodes.INSTANCE_BUFFER_RANGE
        GPUPreparedTextViolationKind.MATERIAL_ABI_MISMATCH ->
            GPUPreparedTextPreflightRefusalCodes.MATERIAL_ABI
        GPUPreparedTextViolationKind.WGSL_ENTRY_POINT_INCORRECT ->
            GPUPreparedTextPreflightRefusalCodes.WGSL_ENTRY_POINT
        GPUPreparedTextViolationKind.BINDING_LAYOUT_INCORRECT ->
            GPUPreparedTextPreflightRefusalCodes.BINDING_LAYOUT
        GPUPreparedTextViolationKind.MATERIAL_UNIFORMS_MODIFIED ->
            GPUPreparedTextPreflightRefusalCodes.MATERIAL_UNIFORMS
        GPUPreparedTextViolationKind.MATERIAL_RESOURCES_MODIFIED ->
            GPUPreparedTextPreflightRefusalCodes.MATERIAL_RESOURCES
        GPUPreparedTextViolationKind.UPLOAD_MISSING ->
            GPUPreparedTextPreflightRefusalCodes.UPLOAD_MISSING
        GPUPreparedTextViolationKind.UPLOAD_DUPLICATED ->
            GPUPreparedTextPreflightRefusalCodes.UPLOAD_DUPLICATE
        GPUPreparedTextViolationKind.UPLOAD_AFTER_FIRST_CONSUMER ->
            GPUPreparedTextPreflightRefusalCodes.UPLOAD_ORDER
        GPUPreparedTextViolationKind.TARGET_MODIFIED ->
            GPUPreparedTextPreflightRefusalCodes.TARGET
        GPUPreparedTextViolationKind.SCISSOR_MODIFIED ->
            GPUPreparedTextPreflightRefusalCodes.SCISSOR
        GPUPreparedTextViolationKind.CLIP_MODIFIED ->
            GPUPreparedTextPreflightRefusalCodes.CLIP
        GPUPreparedTextViolationKind.BLEND_MODIFIED ->
            GPUPreparedTextPreflightRefusalCodes.BLEND
        GPUPreparedTextViolationKind.RESOURCE_LIFETIME_NOT_FRAME_LOCAL ->
            GPUPreparedTextPreflightRefusalCodes.RESOURCE_LIFETIME
        GPUPreparedTextViolationKind.DEPENDENCY_KEY_INCORRECT ->
            GPUPreparedTextPreflightRefusalCodes.DEPENDENCY
        GPUPreparedTextViolationKind.OPERAND_KEY_INCORRECT ->
            GPUPreparedTextPreflightRefusalCodes.OPERAND
        GPUPreparedTextViolationKind.OPERAND_OWNERSHIP_INCORRECT ->
            GPUPreparedTextPreflightRefusalCodes.OPERAND_OWNERSHIP
        GPUPreparedTextViolationKind.TEXTURE_LIMIT_EXCEEDED ->
            GPUPreparedTextPreflightRefusalCodes.TEXTURE_LIMIT
        GPUPreparedTextViolationKind.INSTANCE_BUFFER_LIMIT_EXCEEDED ->
            GPUPreparedTextPreflightRefusalCodes.INSTANCE_BUFFER_LIMIT
        GPUPreparedTextViolationKind.COPY_ALIGNMENT_UNMET ->
            GPUPreparedTextPreflightRefusalCodes.COPY_ALIGNMENT
    }

private object PreparedTextFailingCompletionProvider : GPUQueueCompletionProvider {
    override fun reserveTicket(
        request: GPUQueueCompletionTicketRequest,
    ): GPUQueueCompletionTicketReservation =
        error("Prepared-text refusal must precede completion-ticket reservation")

    override fun abandonReservedTicket(
        ticket: GPUQueueCompletionTicket,
    ): GPUQueueCompletionTicketAbandonResult =
        error("Prepared-text refusal never owns a completion ticket")
}

private object PreparedTextFailingSurfaceProvider : GPUSurfaceOutputProvider {
    override fun acquire(
        request: GPUSurfaceAcquisitionRequest,
    ): GPUSurfaceAcquisitionResult =
        error("Prepared-text refusal must precede surface acquisition")

    override fun release(
        output: GPUAcquiredSurfaceOutput,
    ): GPUSurfaceReleaseResult =
        error("Prepared-text refusal never owns a surface")
}

internal data class PreparedTextNativePreflightFixture(
    val framePlan: GPUFramePlan,
    val capabilities: GPUCapabilities,
    val context: GPUFramePreflightContext,
)

internal fun preparedTextNativePreflightFixture(
    includeColorGlyph: Boolean = false,
    materialProgram:
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
        GPUPreparedTextPreflightFixture.baselineMaterialProgram(),
): PreparedTextNativePreflightFixture {
    val capabilities = preparedTextPreflightCapabilities()
    val frameId = GPUFrameID(109)
    val recordingId = GPURecordingID("recording.prepared-text.native-preflight")
    val target = GPUFrameTargetRef("target.prepared-text.native-preflight")
    val bounds = GPUPixelBounds(0, 0, 16, 16)
    val commandIds = listOf(0, 1)
    val packets = commandIds.map { commandId ->
        preparedTextPreflightPacket(
            commandId = commandId,
            renderStepIdentity = if (includeColorGlyph && commandId == commandIds.last()) {
                COLOR_GLYPH_RENDER_STEP_IDENTITY
            } else {
                "text.a8_mask.sample"
            },
        )
    }
    val capabilitySeal = GPUFrameCapabilitySeal.capture(
        frameId,
        GPUDeviceGenerationID(19),
        capabilities,
    )
    val base = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = listOf(
            GPURecordingSeal(
                recordingId,
                0,
                "compat:prepared-text",
                "replay:prepared-text",
                capabilitySeal.sealHash,
            ),
        ),
        expectedReplayKeyHash = "replay:prepared-text",
        tasks = packets.map { packet ->
            GPUTask.Render(
                taskId = GPUTaskID("task.base.prepared-text.${packet.commandIdValue}"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = target,
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                provisionalSegmentKey =
                    GPUProvisionalRenderSegmentKey(
                        "segment.prepared-text.${packet.commandIdValue}",
                    ),
                drawPackets = listOf(packet),
                batchEligibilityByPacketId = mapOf(
                    packet.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.Isolated,
                        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                    ),
                ),
            )
        },
        dependencies = emptyList(),
        phaseOrder = GPUTaskPhase.entries,
        memoryBudget = GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = 0,
            targetResidentBytes = 0,
            categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
            deviceLimitFacts = emptyList(),
            configuredAggregateBudgetBytes = 1,
            diagnostic = null,
        ),
    )
    val page = GPUPreparedTextPreflightFixture.baselinePage0()
    val atlas = page.toPreparedR8UploadArtifact()
    val semantics = packets.associate { packet ->
        packet.commandIdValue to
            if (packet.renderStepId.value == COLOR_GLYPH_RENDER_STEP_IDENTITY) {
                preparedColorGlyphSemantic(packet, atlas, bounds, capabilities)
            } else {
                GPUPreparedTextPayloadGatherer().gather(
                    GPUPreparedTextA8PayloadInput(
                        commandIdValue = packet.commandIdValue,
                        atlas = atlas,
                        atlasGeneration = page.artifactKey.generation,
                        pageIndex = page.pageIndex,
                        instances = GPUPreparedTextPreflightFixture.baselineA8Instances(page),
                        material = materialProgram,
                        targetBounds = bounds,
                        scissorBounds = bounds,
                        clipIdentity = "clip:none",
                        blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                        capabilitySnapshotHash = capabilities.canonicalSnapshotHash(),
                        frameProvenance = GPUFrameProvenance.GmContent,
                    ),
                )
            }
    }
    val result = GPUPreparedSurfaceFrameTaskListBuilder().build(
        GPUPreparedSurfaceFrameRequest(
            baseTaskList = base,
            capabilities = capabilities,
            target = target,
            targetBounds = bounds,
            semanticsByCommandId = semantics,
            readbackRequestId =
                GPUReadbackRequestID("readback.prepared-text.native-preflight"),
            targetFormat = GPUColorFormat.RGBA8UnormSrgb,
        ),
    )
    val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
        result,
        (result as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
    ).taskList
    val targetGeneration = taskList.tasks
        .filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask.Render::drawPackets)
        .first()
        .resourceGeneration
    val resourceGenerations = taskList.tasks
        .filterIsInstance<GPUTask.PrepareResources>()
        .flatMap(GPUTask.PrepareResources::requests)
        .associate { request ->
            request.resource to if (request.resource == target) targetGeneration else atlas.generation
        }
    return PreparedTextNativePreflightFixture(
        framePlan = GPUFramePlanner.plan(taskList),
        capabilities = capabilities,
        context = GPUFramePreflightContext(
            targetId = target.value,
            deviceGeneration = capabilitySeal.deviceGeneration,
            targetGeneration = targetGeneration,
            resourceGenerations = resourceGenerations,
        ),
    )
}

private fun sampledPreparedTextMaterialProgram(
    sourceId: String,
): org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram {
    val capabilities = preparedTextPreflightCapabilities()
    val result =
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler.compile(
            descriptor =
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.ImageDraw(
                    imageSourceId = sourceId,
                    imageWidth = 2,
                    imageHeight = 2,
                    rgbaPixels = ByteArray(16) { index ->
                        (index * 17 + sourceId.sumOf(Char::code)).toByte()
                    },
                    samplingFilterMode = "linear",
                    alphaOnly = false,
                ),
            paintAlpha = 1f,
            context =
                org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext(
                    capabilityClass = capabilities.canonicalSnapshotHash(),
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:bitmap-shader:v1",
                ),
        )
    return assertIs<
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult.Ready
    >(result).program
}

private fun preparedColorGlyphSemantic(
    packet: GPUDrawPacket,
    atlas: GPUPreparedR8UploadArtifact,
    bounds: GPUPixelBounds,
    capabilities: GPUCapabilities,
): GPUDrawSemanticPayload.ColorGlyph {
    val generation = GPUTextArtifactGeneration(atlas.generation.toInt())
    val planKey = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440091")),
        generation,
        "task9-color-plan",
    )
    val atlasKey = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440092")),
        generation,
        "task9-color-atlas",
    )
    return GPUColorGlyphPayloadGatherer().gatherPreparedSemantic(
        GPUPreparedColorGlyphPayloadInput(
            commandIdValue = packet.commandIdValue,
            planArtifactKey = planKey,
            atlasArtifactKey = atlasKey,
            atlas = atlas,
            instances = listOf(
                GPUTextA8Instance.create(
                    glyphId = 21,
                    sourceGlyphIndex = GPUTextSourceGlyphIndex(packet.commandIdValue),
                    deviceQuad = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
                    uvRect = GPUTextFloatRect(0f, 0f, 0.25f, 0.25f),
                    pageIndex = 0,
                    colorLayerIndex = 0,
                ),
            ),
            layers = listOf(
                GPUColorGlyphLayerPayloadInput(
                    planArtifactKey = planKey,
                    layerGlyphID = 21u,
                    paletteIndex = 0,
                    atlasBounds = GPUPixelBounds(0, 0, 2, 2),
                    deviceBounds = GPUPixelBounds(1, 1, 5, 5),
                    premultipliedRgba = floatArrayOf(0.5f, 0f, 0f, 0.5f),
                    useForeground = false,
                    foregroundResolved = true,
                    placementProof = GPUColorGlyphAtlasPlacementProofInput(
                        atlasArtifactKey = atlasKey,
                        strikeGlyphId = 21,
                        strikeSize = 16f,
                        strikeSubpixelX = 0,
                        strikeSubpixelY = 0,
                        atlasBounds = GPUPixelBounds(0, 0, 2, 2),
                    ),
                    colorLayerIndex = 0,
                ),
            ),
            material = GPUPreparedTextPreflightFixture.baselineMaterialProgram(),
            targetBounds = bounds,
            scissorBounds = bounds,
            clipIdentity = "clip:none",
            blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
            capabilitySnapshotHash = capabilities.canonicalSnapshotHash(),
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )
}

private fun preparedTextPreflightPacket(
    commandId: Int,
    renderStepIdentity: String = "text.a8_mask.sample",
): GPUDrawPacket =
    GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.prepared-text.$commandId"),
        commandIdValue = commandId,
        analysisRecordId = "analysis.prepared-text.$commandId",
        passId = "pass.prepared-text.$commandId",
        layerId = "root",
        bindingListId = "bindings.prepared-text.$commandId",
        insertionReasonCode = "prepared-text",
        sortKey = commandId.toLong(),
        sortKeyPreimage = "paint-order:$commandId",
        renderStepId = GPURenderStepID(renderStepIdentity),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = GPUBlendPlan.FixedFunctionBlend(
            mode = GPUBlendMode.SRC_OVER,
            state = GPUFixedFunctionBlendState(
                stateId = "one_isa",
                color = GPUFixedFunctionBlendComponent(
                    "one",
                    "one-minus-src-alpha",
                    "add",
                ),
                alpha = GPUFixedFunctionBlendComponent(
                    "one",
                    "one-minus-src-alpha",
                    "add",
                ),
                writeMask = "rgba",
            ),
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        ),
        renderPipelineKey = GPURenderPipelineKey("pending.pipeline.prepared-text"),
        bindingLayoutHash = "pending.layout.prepared-text",
        vertexSourceLabel = "prepared-text-instance-quad",
        targetStateHash = "target.rgba8unorm-srgb.16x16",
        originalPaintOrder = commandId,
        resourceGeneration = GPUPreparedTextPreflightFixture.GENERATION.toLong(),
        frameProvenance = GPUFrameProvenance.GmContent,
    )

private fun preparedTextPreflightCapabilities(): GPUCapabilities =
    GPUCapabilities(
        implementation = GPUImplementationIdentity(
            "GPU",
            "prepared-text-test",
            "adapter",
            "device",
        ),
        facts = listOf(
            GPUCapabilityFact(
                "first_slice.draw_text_run.a8_atlas",
                "test",
                "supported",
                true,
                "task9",
            ),
        ),
        snapshotId = "prepared-text-native-preflight",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            GPUTextureFormat.R8Unorm,
            GPUTextureFormat.RGBA8UnormSrgb,
        ),
    )
