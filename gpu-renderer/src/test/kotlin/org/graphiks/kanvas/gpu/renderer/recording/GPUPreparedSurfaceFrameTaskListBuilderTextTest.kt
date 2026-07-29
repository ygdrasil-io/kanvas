package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.GPUTextureFormat
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflight
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceKind
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialFragment
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
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedColorGlyphPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextA8PayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import kotlin.uuid.Uuid

class GPUPreparedSurfaceFrameTaskListBuilderTextTest {
    @Test
    fun `shared TextA8 page records one FrameLocal upload before every ordered consumer`() {
        val atlas = atlas("atlas:shared", generation = 7, bytes = byteArrayOf(1, 2, 3, 4))
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to textSemantic(commandId = 0, atlas = atlas, glyphId = 11),
            1 to textSemantic(commandId = 1, atlas = atlas, glyphId = 12),
        )

        val result = GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = baseTaskList(semantics.keys.toList()),
                capabilities = capabilities(),
                target = TARGET,
                targetBounds = BOUNDS,
                semanticsByCommandId = semantics,
                readbackRequestId = null,
            ),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            result,
            (result as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
        ).taskList
        val upload = taskList.tasks.filterIsInstance<GPUTask.Upload>().single()
        val plan = assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan>(
            upload.r8ResourcePlan,
        )
        assertTrue(plan.preparationRequests.all {
            it.lifetime == GPUFrameResourceLifetime.FrameLocal
        })
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(listOf(0, 1), renders.flatMap { it.drawPackets }.map { it.commandIdValue })
        assertTrue(renders.all { it.drawPackets.isNotEmpty() })
        renders.forEach { render ->
            assertTrue(taskList.dependencies.any { dependency ->
                dependency.fromTaskId == upload.taskId &&
                    dependency.toTaskId == render.taskId &&
                    dependency.reasonCode == "prepared.text.upload-before-consumer"
            })
        }
    }

    @Test
    fun `text subruns share one aligned immutable frame instance buffer with contiguous ranges`() {
        val atlas = atlas("atlas:instances", generation = 7, bytes = byteArrayOf(1, 2, 3, 4))
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to textSemantic(commandId = 0, atlas = atlas, glyphId = 11),
            1 to textSemantic(commandId = 1, atlas = atlas, glyphId = 12, instanceCount = 2),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = baseTaskList(semantics.keys.toList()),
                    capabilities = capabilities(),
                    target = TARGET,
                    targetBounds = BOUNDS,
                    semanticsByCommandId = semantics,
                    readbackRequestId = null,
                ),
            ),
        ).taskList

        val bindings = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap { render -> render.preparedTextBindingsByPacketId.values }
        assertEquals(listOf(0, 1), bindings.map { it.firstInstance })
        assertEquals(listOf(1, 2), bindings.map { it.instanceCount })
        assertSame(bindings.first().instanceBufferPlan, bindings.last().instanceBufferPlan)
        val instancePlan = bindings.first().instanceBufferPlan
        assertEquals(64, instancePlan.strideBytes)
        assertEquals(16, instancePlan.alignmentBytes)
        assertEquals(3, instancePlan.instanceCount)
        assertEquals(192L, instancePlan.byteSize)
        assertEquals(192, instancePlan.bytesForUpload().size)
        val unmodifiedSnapshot = instancePlan.bytesForUpload()
        val callerMutation = instancePlan.bytesForUpload()
        callerMutation[0] = (callerMutation[0].toInt() xor 0xff).toByte()
        assertNotEquals(callerMutation[0], instancePlan.bytesForUpload()[0])
        assertContentEquals(unmodifiedSnapshot, instancePlan.bytesForUpload())

        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        val instancePreparation = preparations.single { it.role == GPUFrameResourceRole.VertexData }
        assertEquals(instancePlan.bufferRef, instancePreparation.resource)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, instancePreparation.lifetime)
        assertEquals(192L, instancePreparation.byteSize)
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.GlyphAtlas })
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.UniformData })
        val uniformPlans = bindings.mapNotNull { it.materialUniformBufferPlan }.distinct()
        assertEquals(1, uniformPlans.size)
        assertTrue(bindings.all { binding ->
            binding.materialUniformOffsetBytes % capabilities()
                .limits!!.minUniformBufferOffsetAlignment == 0L
        })
        assertEquals(listOf(16L, 16L), bindings.map { it.materialUniformSizeBytes })
    }

    @Test
    fun `material uniforms and sampled resources are frame global deduplicated and upload ordered`() {
        val atlas = atlas("atlas:material", generation = 7, bytes = byteArrayOf(1, 2, 3, 4))
        val material = sampledMaterial()
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to textSemantic(0, atlas, 11, material = material),
            1 to textSemantic(1, atlas, 12, material = material),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = baseTaskList(semantics.keys.toList()),
                    capabilities = capabilities(),
                    target = TARGET,
                    targetBounds = BOUNDS,
                    semanticsByCommandId = semantics,
                    readbackRequestId = null,
                ),
            ),
        ).taskList

        val bindings = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap { it.preparedTextBindingsByPacketId.values }
        assertEquals(2, bindings.size)
        assertSame(bindings[0].materialUniformBufferPlan, bindings[1].materialUniformBufferPlan)
        assertEquals(
            listOf(0L, 0L),
            bindings.map { it.materialUniformOffsetBytes },
        )
        assertEquals(listOf(32L, 32L), bindings.map { it.materialUniformSizeBytes })
        assertEquals(1, bindings.flatMap { it.materialSampledResourcePlans }.distinct().size)

        val materialUpload = taskList.tasks.filterIsInstance<GPUTask.Upload>().single {
            it.materialResourcePlan != null
        }
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        renders.forEach { render ->
            assertTrue(taskList.dependencies.any { dependency ->
                dependency.fromTaskId == materialUpload.taskId &&
                    dependency.toTaskId == render.taskId &&
                    dependency.reasonCode == "prepared.text.material-upload-before-consumer"
            })
        }
        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.UniformData })
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.GlyphAtlas })
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.StorageData })
        val sampledTexture = preparations.single { it.role == GPUFrameResourceRole.StorageData }
        assertEquals(
            GPUColorFormat.RGBA8UnormSrgb,
            assertIs<GPUFrameTextureDescriptor>(sampledTexture.descriptor).format,
        )
        assertTrue(preparations.filter { it.role == GPUFrameResourceRole.UniformData }
            .all { it.lifetime == GPUFrameResourceLifetime.FrameLocal })
    }

    @Test
    fun `alpha only sampled material keeps a linear UNORM texture descriptor`() {
        val atlas = atlas("atlas:alpha-material", generation = 7, bytes = byteArrayOf(1, 2, 3, 4))
        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = baseTaskList(listOf(0)),
                    capabilities = capabilities(),
                    target = TARGET,
                    targetBounds = BOUNDS,
                    semanticsByCommandId = mapOf(
                        0 to textSemantic(0, atlas, 11, material = sampledMaterial(alphaOnly = true)),
                    ),
                    readbackRequestId = null,
                ),
            ),
        ).taskList

        val sampledTexture = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .single { it.role == GPUFrameResourceRole.StorageData }
        assertEquals(
            GPUColorFormat.RGBA8Unorm,
            assertIs<GPUFrameTextureDescriptor>(sampledTexture.descriptor).format,
        )
    }

    @Test
    fun `Core only missing limits keeps the established Core refusal authority`() {
        val missingLimits = capabilities().copy(limits = null)
        val semantic = coreSemantic(0)
        val result = GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = baseTaskList(
                    commandIds = listOf(0),
                    renderStepByCommandId = mapOf(0 to CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
                    capabilities = missingLimits,
                ),
                capabilities = missingLimits,
                target = TARGET,
                targetBounds = BOUNDS,
                semanticsByCommandId = mapOf(0 to semantic),
                readbackRequestId = null,
            ),
        )

        assertEquals(
            "unsupported.recording.core_primitive_limits_unavailable",
            assertIs<GPUPreparedSurfaceFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `R8 sharing requires exact key generation content and layout identity`() {
        val shared = atlas("atlas:identity", generation = 7, bytes = byteArrayOf(1, 2, 3, 4))
        val nextGeneration = atlas(
            "atlas:identity",
            generation = 8,
            bytes = byteArrayOf(1, 2, 3, 4),
        )
        val nextContent = atlas(
            "atlas:identity",
            generation = 7,
            bytes = byteArrayOf(4, 3, 2, 1),
        )
        val nextLayout = atlas(
            "atlas:identity",
            generation = 7,
            bytes = byteArrayOf(1, 2, 0, 0, 3, 4, 0, 0),
            rowBytes = 4,
        )
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to textSemantic(0, shared, 11),
            1 to textSemantic(1, shared, 12),
            2 to textSemantic(2, nextGeneration, 13),
            3 to textSemantic(3, nextContent, 14),
            4 to textSemantic(4, nextLayout, 15),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = baseTaskList(semantics.keys.toList()),
                    capabilities = capabilities(),
                    target = TARGET,
                    targetBounds = BOUNDS,
                    semanticsByCommandId = semantics,
                    readbackRequestId = null,
                ),
            ),
        ).taskList

        val plans = taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .map { upload -> assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan>(
                upload.r8ResourcePlan,
            ) }
        assertEquals(4, plans.size)
        assertEquals(4, plans.map { it.frameTextureRef }.distinct().size)
        assertTrue(plans.flatMap { it.preparationRequests }.all {
            it.lifetime == GPUFrameResourceLifetime.FrameLocal
        })
        assertNotEquals(plans[0].frameTextureRef, plans[1].frameTextureRef)
    }

    @Test
    fun `every structural and aggregate budget refuses atomically`() {
        val atlas = atlas("atlas:budget", generation = 7, bytes = byteArrayOf(1, 2, 3, 4))
        val semantics = mapOf<Int, GPUDrawSemanticPayload>(
            0 to textSemantic(0, atlas, 11),
        )
        val request = GPUPreparedSurfaceFrameRequest(
            baseTaskList = baseTaskList(listOf(0)),
            capabilities = capabilities(),
            target = TARGET,
            targetBounds = BOUNDS,
            semanticsByCommandId = semantics,
            readbackRequestId = null,
        )
        val recorded = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(request),
        ).taskList
        val bufferAllocations = recorded.memoryBudget.allocations.count {
            it.resourceKind ==
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind.Buffer
        }
        val textureAllocations = recorded.memoryBudget.allocations.count {
            it.resourceKind ==
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind.Texture2D
        }
        val taskCount = recorded.tasks.size
        val dependencyCount = recorded.dependencies.size
        val allocationCount = recorded.memoryBudget.allocations.size
        val cases = listOf(
            GPUPreparedSurfaceTaskGraphLimits(maxBufferAllocations = bufferAllocations - 1) to
                "unsupported.recording.prepared_surface_buffer_allocation_budget",
            GPUPreparedSurfaceTaskGraphLimits(maxTextureAllocations = textureAllocations - 1) to
                "unsupported.recording.prepared_surface_texture_allocation_budget",
            GPUPreparedSurfaceTaskGraphLimits(maxAllocations = allocationCount - 1) to
                "unsupported.recording.prepared_surface_allocation_budget",
            GPUPreparedSurfaceTaskGraphLimits(maxTasks = taskCount - 1) to
                "unsupported.recording.prepared_surface_task_budget",
            GPUPreparedSurfaceTaskGraphLimits(maxDependencies = dependencyCount - 1) to
                "unsupported.recording.prepared_surface_dependency_budget",
            GPUPreparedSurfaceTaskGraphLimits(maxInstanceRanges = 0) to
                "unsupported.recording.prepared_text_instance_range_budget",
        )
        cases.forEach { (limits, expectedCode) ->
            val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
                GPUPreparedSurfaceFrameTaskListBuilder().build(
                    request = request,
                    taskGraphLimits = limits,
                ),
                expectedCode,
            )
            assertEquals(expectedCode, refused.diagnostic.code.value)
        }

        val aggregate = recorded.memoryBudget.let {
            it.targetResidentBytes + it.peakFrameTransientBytes
        }
        val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request = request,
                configuredAggregateBudgetBytes = aggregate - 1,
            ),
        )
        assertEquals(
            "unsupported.frame_memory.aggregate_budget_exceeded",
            refused.diagnostic.code.value,
        )
    }

    @Test
    fun `mixed Core Text Image Text Color graph preserves exact semantic and paint order`() {
        val sharedAtlas = atlas(
            "atlas:mixed",
            generation = 7,
            bytes = byteArrayOf(1, 2, 3, 4),
        )
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to coreSemantic(commandId = 0),
            1 to textSemantic(commandId = 1, atlas = sharedAtlas, glyphId = 11),
            2 to imageSemantic(commandId = 2),
            3 to textSemantic(commandId = 3, atlas = sharedAtlas, glyphId = 12),
            4 to colorSemantic(commandId = 4, atlas = sharedAtlas),
        )
        val renderSteps = semantics.mapValues { (_, semantic) ->
            semantic.payloadRef.renderStepIdentity
        }
        val requestId = GPUReadbackRequestID("readback.task8.mixed")
        val result = GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = baseTaskList(
                    semantics.keys.toList(),
                    renderStepByCommandId = renderSteps,
                ),
                capabilities = capabilities(),
                target = TARGET,
                targetBounds = BOUNDS,
                semanticsByCommandId = semantics,
                readbackRequestId = requestId,
                targetFormat = GPUColorFormat.RGBA8UnormSrgb,
            ),
        )

        val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            result,
            (result as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
        ).taskList
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        val orderedPackets = renders.flatMap(GPUTask.Render::drawPackets)
        assertEquals(
            listOf("CorePrimitive", "TextA8", "SampledImage", "TextA8", "ColorGlyph"),
            orderedPackets.map { packet -> requireNotNull(packet.semanticPayload).canonicalType },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), orderedPackets.map { it.commandIdValue })
        assertEquals(listOf(0, 1, 2, 3, 4), orderedPackets.map { it.originalPaintOrder })
        assertEquals(
            renders.indices.map { index ->
                "task.prepared-surface.render.${taskList.frameId.value}.$index"
            },
            renders.map { it.taskId.value },
        )
        assertTrue(renders.all { it.drawPackets.isNotEmpty() })
        assertEquals(
            orderedPackets.map { it.commandIdValue },
            renders.flatMap { render ->
                render.drawPackets.map { packet -> packet.commandIdValue }
            },
        )
        assertEquals(1, taskList.tasks.filterIsInstance<GPUTask.Readback>().size)
        assertEquals(
            requestId,
            taskList.tasks.filterIsInstance<GPUTask.Readback>().single().request.requestId,
        )

        val plan = GPUFramePlanner.plan(taskList)
        assertEquals(
            listOf("CorePrimitive", "TextA8", "SampledImage", "TextA8", "ColorGlyph"),
            plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                .map { packet -> requireNotNull(packet.semanticPayload).canonicalType },
        )
        assertEquals(1, plan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().size)
        assertTrue(plan.dumpLines().any { line ->
            "preparedTextBindings=" in line && "packet.text.1" in line
        })
        val textBindingLine = plan.dumpLines().first { "preparedTextBindings=" in it }
        assertTrue("uploadBytes=" !in textBindingLine.substringAfter("preparedTextBindings="))
        assertTrue("preparations=" !in textBindingLine.substringAfter("preparedTextBindings="))
        assertEquals(plan.stableHash(), GPUFramePlanner.plan(taskList).stableHash())
        assertNull(
            GPUPreparedSurfaceNativePreflight().validateFramePlan(
                framePlan = plan,
                capabilities = capabilities().let { observed ->
                    observed.copy(
                        supportedTextureFormats =
                            observed.supportedTextureFormats + GPUTextureFormat.R8Unorm,
                    )
                },
            ),
        )
    }

    private fun coreSemantic(commandId: Int): GPUDrawSemanticPayload.CorePrimitive =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = commandId,
                sourceFamily = GPUCorePrimitiveSourceFamily.Color,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = BOUNDS,
                scissorBounds = BOUNDS,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
                blendPlanIdentity = requireNotNull(
                    packet(commandId, CORE_PRIMITIVE_RENDER_STEP_IDENTITY).blendPlan,
                ).canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
                coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
            ),
        )

    private fun imageSemantic(commandId: Int): GPUDrawSemanticPayload.SampledImage {
        val artifact = assertIs<GPUPreparedImageArtifactResult.Ready>(
            GPUPreparedImageArtifactFactory.prepare(
                GPUPreparedImageSourceInput(
                    sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                    sourceId = "image:task8:mixed",
                    width = 2,
                    height = 2,
                    sourceFormat = GPUPreparedImageSourceFormat.Rgba8,
                    alphaType = AlphaType.PREMUL,
                    sourceRowBytes = 8,
                    profile = GPUPreparedImageProfile.Srgb,
                    orientation = GPUPreparedImageOrientation.AppliedIdentity,
                    provenance = GPUPreparedImageProvenance.CallerPixels,
                    sourceGeneration = 0,
                    pixelBytes = ByteArray(16) { index -> (index + 1).toByte() },
                ),
            ),
        ).artifact
        return GPUPreparedImagePayloadGatherer().gatherSemantic(
            GPUPreparedImagePayloadInput(
                payloadRef = GPUDrawPayloadRef(commandId, "image.draw.texture_upload"),
                artifact = artifact,
                geometry = GPUPreparedImageGeometry(
                    GPUPreparedImageGeometryClass.Rect,
                    listOf(
                        GPUPreparedImageVertex(1f, 1f, 0f, 0f),
                        GPUPreparedImageVertex(8f, 1f, 1f, 0f),
                        GPUPreparedImageVertex(8f, 8f, 1f, 1f),
                        GPUPreparedImageVertex(1f, 8f, 0f, 1f),
                    ),
                    listOf(0, 1, 2, 0, 2, 3),
                ),
                sampling = GPUPreparedImageSampling.Nearest,
                tintPremultipliedRgba = listOf(1f, 1f, 1f, 1f),
                atlasColorPremultipliedRgba = null,
                atlasSourceBlend = null,
                targetBounds = BOUNDS,
                scissorBounds = BOUNDS,
                blendPlanIdentity = requireNotNull(
                    packet(commandId, "image.draw.texture_upload").blendPlan,
                ).canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )
    }

    private fun colorSemantic(
        commandId: Int,
        atlas: GPUPreparedR8UploadArtifact,
    ): GPUDrawSemanticPayload.ColorGlyph {
        val planKey = GPUTextArtifactKey(
            GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440071")),
            GPUTextArtifactGeneration(atlas.generation.toInt()),
            "task8-color-plan",
        )
        val atlasKey = GPUTextArtifactKey(
            GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440072")),
            GPUTextArtifactGeneration(atlas.generation.toInt()),
            "task8-color-atlas",
        )
        val layer = GPUColorGlyphLayerPayloadInput(
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
        )
        return GPUColorGlyphPayloadGatherer().gatherPreparedSemantic(
            GPUPreparedColorGlyphPayloadInput(
                commandIdValue = commandId,
                planArtifactKey = planKey,
                atlasArtifactKey = atlasKey,
                atlas = atlas,
                instances = listOf(
                    GPUTextA8Instance.create(
                        glyphId = 21,
                        sourceGlyphIndex = GPUTextSourceGlyphIndex(commandId),
                        deviceQuad = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
                        uvRect = GPUTextFloatRect(0f, 0f, 1f, 1f),
                        pageIndex = 0,
                        colorLayerIndex = 0,
                    ),
                ),
                layers = listOf(layer),
                material = preparedMaterial("material:color:$commandId"),
                targetBounds = BOUNDS,
                scissorBounds = BOUNDS,
                clipIdentity = "clip:none",
                blendPlanIdentity = requireNotNull(
                    packet(commandId, COLOR_GLYPH_RENDER_STEP_IDENTITY).blendPlan,
                ).canonicalIdentity(),
                capabilitySnapshotHash = "capability:text",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )
    }

    private fun textSemantic(
        commandId: Int,
        atlas: GPUPreparedR8UploadArtifact,
        glyphId: Int,
        instanceCount: Int = 1,
        material: GPUPreparedMaterialProgram = preparedMaterial("material:text:$commandId"),
    ): GPUDrawSemanticPayload.TextA8 = GPUPreparedTextPayloadGatherer().gather(
        GPUPreparedTextA8PayloadInput(
            commandIdValue = commandId,
            atlas = atlas,
            atlasGeneration = GPUTextArtifactGeneration(atlas.generation.toInt()),
            pageIndex = 0,
            instances = List(instanceCount) { instanceIndex ->
                GPUTextA8Instance.create(
                    glyphId = glyphId + instanceIndex,
                    sourceGlyphIndex = GPUTextSourceGlyphIndex(commandId + instanceIndex),
                    deviceQuad = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
                    uvRect = GPUTextFloatRect(0f, 0f, 1f, 1f),
                    pageIndex = 0,
                )
            },
            material = material,
            targetBounds = BOUNDS,
            scissorBounds = BOUNDS,
            clipIdentity = "clip:none",
            blendPlanIdentity = requireNotNull(
                packet(commandId, "text.a8_mask.sample").blendPlan,
            ).canonicalIdentity(),
            capabilitySnapshotHash = "capability:text",
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )

    private fun preparedMaterial(key: String) = GPUPreparedMaterialProgram(
        materialKey = key,
        wgslSource =
            "@fragment fn prepared_material_fragment() -> @location(0) vec4f { " +
                "return vec4f(1.0); }",
        entryPoint = "prepared_material_fragment",
        composableFragment = stubPreparedMaterialFragment(uniformByteCount = 16),
        uniformBytes = List(16) { index -> index },
        sampledResources = emptyList(),
        paintAlpha = 1f,
        sourceKind = GPUMaterialSourceKind.SolidColor,
        abiHash = "abi:text",
        expectedFragmentIdentity =
            stubPreparedMaterialFragment(uniformByteCount = 16).authenticatedIdentity,
    )

    private fun sampledMaterial(alphaOnly: Boolean = false): GPUPreparedMaterialProgram {
        val result = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = GPUMaterialDescriptor.ImageDraw(
                imageSourceId = "material:task8:checker",
                imageWidth = 2,
                imageHeight = 2,
                rgbaPixels = ByteArray(16) { index -> (index * 13).toByte() },
                samplingFilterMode = "linear",
                alphaOnly = alphaOnly,
            ),
            paintAlpha = 1f,
            context = GPUMaterialLoweringContext(
                capabilityClass = capabilities().canonicalSnapshotHash(),
                targetFormatClass = "rgba8unorm",
                dictionaryVersion = "material-dictionary:bitmap-shader:v1",
            ),
        )
        return assertIs<GPUPreparedMaterialProgramResult.Ready>(result).program
    }

    private fun atlas(
        key: String,
        generation: Long,
        bytes: ByteArray,
        rowBytes: Int = 2,
    ): GPUPreparedR8UploadArtifact = GPUPreparedR8UploadArtifact(
        key = key,
        width = 2,
        height = 2,
        rowBytes = rowBytes,
        generation = generation,
        contentHash = sha256(bytes),
        bytes = bytes,
    )

    private fun baseTaskList(
        commandIds: List<Int>,
        renderStepByCommandId: Map<Int, String> = commandIds.associateWith {
            "text.a8_mask.sample"
        },
        capabilities: GPUCapabilities = capabilities(),
    ): GPUTaskList {
        val frameId = GPUFrameID(41)
        val recordingId = GPURecordingID("recording.text.task8")
        val seal = GPUFrameCapabilitySeal.capture(
            frameId,
            GPUDeviceGenerationID(9),
            capabilities,
        )
        val renders = commandIds.map { commandId ->
            val packet = packet(commandId, renderStepByCommandId.getValue(commandId))
            GPUTask.Render(
                taskId = GPUTaskID("task.base.text.$commandId"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = TARGET,
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                provisionalSegmentKey = GPUProvisionalRenderSegmentKey("segment.text.$commandId"),
                drawPackets = listOf(packet),
                batchEligibilityByPacketId = mapOf(
                    packet.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.Isolated,
                        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                    ),
                ),
            )
        }
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(recordingId, 0, "compat:text", "replay:text", seal.sealHash),
            ),
            expectedReplayKeyHash = "replay:text",
            tasks = renders,
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
    }

    private fun packet(
        commandId: Int,
        renderStepIdentity: String = "text.a8_mask.sample",
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.text.$commandId"),
        commandIdValue = commandId,
        analysisRecordId = "analysis.text.$commandId",
        passId = "pass.text.$commandId",
        layerId = "root",
        bindingListId = "bindings.text.$commandId",
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
                color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
                alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
                writeMask = "rgba",
            ),
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        ),
        renderPipelineKey = GPURenderPipelineKey("pending.pipeline.text"),
        bindingLayoutHash = "pending.layout.text",
        vertexSourceLabel = "prepared-text-instance-quad",
        targetStateHash = "target.rgba8unorm.16x16",
        originalPaintOrder = commandId,
        resourceGeneration = 7,
        frameProvenance = GPUFrameProvenance.GmContent,
        clipCoveragePlan = if (
            renderStepIdentity == CORE_PRIMITIVE_RENDER_STEP_IDENTITY ||
            renderStepIdentity == "image.draw.texture_upload"
        ) {
            GPUClipCoveragePlan.NoClip
        } else {
            null
        },
        clipExecutionPlan = if (
            renderStepIdentity == CORE_PRIMITIVE_RENDER_STEP_IDENTITY ||
            renderStepIdentity == "image.draw.texture_upload"
        ) {
            GPUClipExecutionPlan.NoClip
        } else {
            null
        },
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact(
                "first_slice.draw_text_run.a8_atlas",
                "test",
                "supported",
                true,
                "task8",
            ),
            GPUCapabilityFact(
                "first_slice.fill_rect.native",
                "test",
                "supported",
                true,
                "task8",
            ),
            GPUCapabilityFact(
                "first_slice.draw_image_rect.prepared",
                "test",
                "supported",
                true,
                "task8",
            ),
        ),
        snapshotId = "task8-text",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        val TARGET = GPUFrameTargetRef("target.task8.text")
        val BOUNDS = GPUPixelBounds(0, 0, 16, 16)
    }
}
