package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawImageRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialProgram
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
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextA8PayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextDeviceToLocalAffine
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesTopologyIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

class GPUPreparedSurfaceVerticesTaskListBuilderTest {
    @Test
    fun `one artifact used by two draws emits one vertex upload and both draws depend on it`() {
        val artifact = artifact(vertexCount = 6)
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to verticesSemantic(0, artifact),
            1 to verticesSemantic(1, artifact),
        )

        val taskList = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0, 1)), semantics),
            ),
        )
        val vertexUploads = taskList.tasks
            .filterIsInstance<GPUTask.Upload>()
            .filter { upload -> upload.taskId.value.contains("vertices-vertex-upload") }
        assertEquals(1, vertexUploads.size)
        val renderers = taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(2, renderers.size)
        val token = "prepared-vertices.upload-before-consumer:${artifact.key}"
        renderers.forEach { render ->
            assertTrue(
                taskList.dependencies.any { dependency ->
                    dependency.fromTaskId == vertexUploads.single().taskId &&
                        dependency.toTaskId == render.taskId &&
                        dependency.reasonCode == "prepared.vertices.upload-before-consumer" &&
                        dependency.useToken?.value == token
                },
            )
        }
    }

    @Test
    fun `two distinct artifacts emit two uploads and each draw depends only on its own`() {
        val firstArtifact = artifact(vertexCount = 6, indexed = true)
        val secondArtifact = artifact(vertexCount = 9)
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to verticesSemantic(0, firstArtifact),
            1 to verticesSemantic(1, secondArtifact),
        )

        val taskList = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0, 1)), semantics),
            ),
        )
        val vertexUploads = taskList.tasks
            .filterIsInstance<GPUTask.Upload>()
            .filter { upload -> upload.taskId.value.contains("vertices-vertex-upload") }
        assertEquals(2, vertexUploads.size)
        val indexUploads = taskList.tasks
            .filterIsInstance<GPUTask.Upload>()
            .filter { upload -> upload.taskId.value.contains("vertices-index-upload") }
        assertEquals(1, indexUploads.size)
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        val firstRender = renders.single { render -> render.drawPackets.single().commandIdValue == 0 }
        val secondRender = renders.single { render -> render.drawPackets.single().commandIdValue == 1 }
        val firstUpload = vertexUploads.single { upload ->
            upload.destination.value.contains(firstArtifact.key)
        }
        val secondUpload = vertexUploads.single { upload ->
            upload.destination.value.contains(secondArtifact.key)
        }
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == firstUpload.taskId && dependency.toTaskId == firstRender.taskId
            },
        )
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == secondUpload.taskId && dependency.toTaskId == secondRender.taskId
            },
        )
        assertTrue(
            taskList.dependencies.none { dependency ->
                dependency.fromTaskId == secondUpload.taskId && dependency.toTaskId == firstRender.taskId
            },
        )
        assertTrue(
            taskList.dependencies.none { dependency ->
                dependency.fromTaskId == firstUpload.taskId && dependency.toTaskId == secondRender.taskId
            },
        )
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == indexUploads.single().taskId &&
                    dependency.toTaskId == firstRender.taskId
            },
        )
    }

    @Test
    fun `indexed draw emits one index upload and the draw depends on both uploads`() {
        val artifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint16")
        val semantics = mapOf(0 to verticesSemantic(0, artifact))

        val taskList = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0)), semantics),
            ),
        )
        val vertexUpload = taskList.tasks
            .filterIsInstance<GPUTask.Upload>()
            .single { upload -> upload.taskId.value.contains("vertices-vertex-upload") }
        val indexUpload = taskList.tasks
            .filterIsInstance<GPUTask.Upload>()
            .single { upload -> upload.taskId.value.contains("vertices-index-upload") }
        assertEquals(GPUTaskPhase.Upload, indexUpload.phase)
        assertEquals(GPUUploadDestinationKind.Buffer, indexUpload.destinationKind)
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        val token = "prepared-vertices.upload-before-consumer:${artifact.key}"
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == vertexUpload.taskId &&
                    dependency.toTaskId == render.taskId &&
                    dependency.useToken?.value == token
            },
        )
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == indexUpload.taskId &&
                    dependency.toTaskId == render.taskId &&
                    dependency.useToken?.value == token
            },
        )
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == taskList.tasks
                    .filterIsInstance<GPUTask.PrepareResources>().single().taskId &&
                    dependency.toTaskId == indexUpload.taskId
            },
        )
    }

    @Test
    fun `mixed core image text and vertices frame preserves exact paint order`() {
        val recorded = recording(coreCommand(0, 0), imageCommand(1, 1)).taskList
        val template = recorded.tasks.filterIsInstance<GPUTask.Render>().first()
        val verticesPacket = packet(2, PREPARED_VERTICES_RENDER_STEP_IDENTITY)
        val textPacket = packet(3, "text.a8_mask.sample")
        val base = recorded.appendingRenders(
            appendedRender(
                template = template,
                taskId = GPUTaskID("task.base.vertices.2"),
                packet = verticesPacket,
                provisionalSegmentKey = GPUProvisionalRenderSegmentKey("segment.vertices.2"),
            ),
            appendedRender(
                template = template,
                taskId = GPUTaskID("task.base.text.3"),
                packet = textPacket,
                provisionalSegmentKey = GPUProvisionalRenderSegmentKey("segment.text.3"),
            ),
        )
        val verticesArtifact = artifact(vertexCount = 6)
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to coreSemantic(base, 0),
            1 to imageSemantic(base, 1),
            2 to verticesSemantic(2, verticesArtifact),
            3 to textSemantic(3, atlas("atlas:mixed", 7, byteArrayOf(1, 2, 3, 4))),
        )

        val taskList = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(base, semantics, GPUColorFormat.RGBA8UnormSrgb),
            ),
        )
        val paintOrder = taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap { render -> render.drawPackets.map { packet -> packet.commandIdValue } }
        assertEquals(listOf(0, 1, 2, 3), paintOrder)
        assertTrue(
            taskList.tasks.filterIsInstance<GPUTask.Upload>().any { upload ->
                upload.taskId.value.contains("vertices-vertex-upload")
            },
        )
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        renders.zipWithNext().forEachIndexed { index, (from, to) ->
            assertTrue(
                taskList.dependencies.any { dependency ->
                    dependency.fromTaskId == from.taskId &&
                        dependency.toTaskId == to.taskId &&
                        dependency.reasonCode == "preserve.prepared-scene.order"
                },
                "paint-order dependency $index must chain consecutive renders",
            )
        }
    }

    @Test
    fun `material sampled resources upload precedes the consuming vertices draw`() {
        val material = sampledMaterial()
        assertEquals(1, material.sampledResources.size)
        val semantics = mapOf(
            0 to verticesSemantic(0, artifact(vertexCount = 6), material = material),
        )

        val taskList = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0)), semantics),
            ),
        )
        val materialUpload = taskList.tasks
            .filterIsInstance<GPUTask.Upload>()
            .single { upload -> upload.materialResourcePlan != null }
        val render = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == materialUpload.taskId &&
                    dependency.toTaskId == render.taskId &&
                    dependency.reasonCode == "prepared.vertices.material-upload-before-consumer"
            },
        )
        assertTrue(
            taskList.dependencies.any { dependency ->
                dependency.fromTaskId == taskList.tasks
                    .filterIsInstance<GPUTask.PrepareResources>().single().taskId &&
                    dependency.toTaskId == materialUpload.taskId
            },
        )
    }

    @Test
    fun `frame vertex upload budget refusal constructs no partial task list`() {
        val semantics = mapOf(
            0 to verticesSemantic(0, artifact(vertexCount = 6, indexed = true, indexFormat = "uint16")),
        )

        val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0)), semantics),
                configuredAggregateBudgetBytes = 1024L,
            ),
        )
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", refused.diagnostic.code.value)
    }

    @Test
    fun `unsupported vertices semantic refuses without a partial task list`() {
        val semantics = mapOf(
            0 to verticesSemantic(
                0,
                artifact(vertexCount = 6),
                targetBounds = GPUPixelBounds(0, 0, 32, 32),
            ),
        )

        val refused = assertIs<GPUPreparedSurfaceFrameResult.Refused>(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0)), semantics),
            ),
        )
        assertEquals("invalid.recording.prepared_vertices_semantic", refused.diagnostic.code.value)
    }

    @Test
    fun `identical inputs produce an identical deterministic task list`() {
        val artifact = artifact(vertexCount = 6, indexed = true, indexFormat = "uint16")
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>(
            0 to verticesSemantic(0, artifact),
            1 to verticesSemantic(1, artifact),
        )

        val first = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0, 1)), semantics),
            ),
        )
        val second = recorded(
            GPUPreparedSurfaceFrameTaskListBuilder().build(
                request(verticesBaseTaskList(listOf(0, 1)), semantics),
            ),
        )
        assertEquals(first.dumpLines(), second.dumpLines())
    }

    private fun recorded(result: GPUPreparedSurfaceFrameResult): GPUTaskList =
        assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            result,
            (result as? GPUPreparedSurfaceFrameResult.Refused)?.diagnostic.toString(),
        ).taskList

    private fun request(
        base: GPUTaskList,
        semantics: Map<Int, GPUDrawSemanticPayload>,
        targetFormat: GPUColorFormat = GPUColorFormat.RGBA8Unorm,
    ) = GPUPreparedSurfaceFrameRequest(
        baseTaskList = base,
        capabilities = capabilities(),
        target = GPUFrameTargetRef("target.prepared-surface"),
        targetBounds = BOUNDS,
        semanticsByCommandId = semantics,
        readbackRequestId = null,
        targetFormat = targetFormat,
    )

    private fun verticesBaseTaskList(commandIds: List<Int>): GPUTaskList {
        val frameId = GPUFrameID(61)
        val recordingId = GPURecordingID("recording.vertices.task9")
        val seal = GPUFrameCapabilitySeal.capture(frameId, GPUDeviceGenerationID(5), capabilities())
        val renders = commandIds.map { commandId ->
            val packet = packet(commandId, PREPARED_VERTICES_RENDER_STEP_IDENTITY)
            GPUTask.Render(
                taskId = GPUTaskID("task.base.vertices.$commandId"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = TARGET,
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                provisionalSegmentKey = GPUProvisionalRenderSegmentKey("segment.vertices.$commandId"),
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
                GPURecordingSeal(recordingId, 0, "compat:vertices", "replay:vertices", seal.sealHash),
            ),
            expectedReplayKeyHash = "replay:vertices",
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

    private fun verticesSemantic(
        commandId: Int,
        artifact: GPUPreparedVerticesUploadArtifact,
        material: GPUPreparedMaterialProgram = stubPreparedMaterialProgram(red = 0.5f),
        targetBounds: GPUPixelBounds = BOUNDS,
        scissorBounds: GPUPixelBounds = BOUNDS,
    ): GPUDrawSemanticPayload.Vertices {
        val result = GPUPreparedVerticesPayloadGatherer.gather(
            GPUPreparedVerticesPayloadInput(
                payloadRef = GPUDrawPayloadRef(commandId, PREPARED_VERTICES_RENDER_STEP_IDENTITY),
                artifact = artifact,
                material = material,
                materialFrameSnapshot = null,
                topologyIdentity = GPUPreparedVerticesTopologyIdentity.Triangles,
                transformBytes = listOf(
                    1f.toRawBits(), 0f.toRawBits(), 0f.toRawBits(),
                    0f.toRawBits(), 1f.toRawBits(), 0f.toRawBits(),
                    0f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(),
                ),
                targetBounds = targetBounds,
                scissorBounds = scissorBounds,
                targetFormat = "rgba8unorm",
                clipIdentity = "clip:none",
                clipCoverageIdentity = "clip-coverage:none",
                primitiveColorPresent = false,
                primitiveBlendIdentity = null,
                finalBlendIdentity = "src-over",
                capabilitySnapshotHash = "capability:vertices",
                drawProvenance = "test",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )
        return assertIs<GPUPreparedVerticesPayloadResult.Ready>(result).payload
    }

    private fun coreSemantic(base: GPUTaskList, commandId: Int): GPUDrawSemanticPayload.CorePrimitive {
        val basePacket = packetFrom(base, commandId)
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = commandId,
                sourceFamily = GPUCorePrimitiveSourceFamily.Color,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = BOUNDS,
                scissorBounds = BOUNDS,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
                blendPlanIdentity = requireNotNull(basePacket.blendPlan).canonicalIdentity(),
                frameProvenance = basePacket.frameProvenance,
                coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
            ),
        )
    }

    private fun imageSemantic(base: GPUTaskList, commandId: Int): GPUDrawSemanticPayload.SampledImage {
        val basePacket = packetFrom(base, commandId)
        return GPUPreparedImagePayloadGatherer().gatherSemantic(
            GPUPreparedImagePayloadInput(
                payloadRef = GPUDrawPayloadRef(commandId, "image.draw.texture_upload"),
                artifact = imageArtifact(),
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
                blendPlanIdentity = requireNotNull(basePacket.blendPlan).canonicalIdentity(),
                frameProvenance = basePacket.frameProvenance,
            ),
        )
    }

    private fun textSemantic(
        commandId: Int,
        atlas: GPUPreparedR8UploadArtifact,
    ): GPUDrawSemanticPayload.TextA8 = GPUPreparedTextPayloadGatherer().gather(
        GPUPreparedTextA8PayloadInput(
            commandIdValue = commandId,
            atlas = atlas,
            atlasGeneration = GPUTextArtifactGeneration(atlas.generation.toInt()),
            pageIndex = 0,
            instances = listOf(
                GPUTextA8Instance.create(
                    glyphId = 11,
                    sourceGlyphIndex = GPUTextSourceGlyphIndex(commandId),
                    deviceQuad = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
                    uvRect = GPUTextFloatRect(0f, 0f, 1f, 1f),
                    pageIndex = 0,
                ),
            ),
            material = stubPreparedMaterialProgram(red = 0.5f),
            deviceToLocal = GPUPreparedTextDeviceToLocalAffine(
                m00 = 1f,
                m01 = 0f,
                m02 = 0f,
                m10 = 0f,
                m11 = 1f,
                m12 = 0f,
            ),
            targetBounds = BOUNDS,
            scissorBounds = BOUNDS,
            clipIdentity = "clip:none",
            blendPlanIdentity = requireNotNull(
                packet(3, "text.a8_mask.sample").blendPlan,
            ).canonicalIdentity(),
            capabilitySnapshotHash = "capability:text",
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )

    private fun sampledMaterial(): GPUPreparedMaterialProgram {
        val result = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = GPUMaterialDescriptor.ImageDraw(
                imageSourceId = "material:task9:checker",
                imageWidth = 2,
                imageHeight = 2,
                rgbaPixels = ByteArray(16) { index -> (index * 13).toByte() },
                samplingFilterMode = "linear",
                alphaOnly = false,
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
    ): GPUPreparedR8UploadArtifact = GPUPreparedR8UploadArtifact(
        key = key,
        width = 2,
        height = 2,
        rowBytes = 2,
        generation = generation,
        contentHash = sha256(bytes),
        bytes = bytes,
    )

    private fun artifact(
        vertexCount: Int,
        indexed: Boolean = false,
        indexFormat: String = "uint16",
    ): GPUPreparedVerticesUploadArtifact = GPUPreparedVerticesUploadArtifact(
        topology = GPUVertexMode.Triangles,
        layout = GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = false),
        vertexBytes = ByteArray(vertexCount * 8),
        indexBytes = if (indexed) {
            ByteArray(vertexCount * (if (indexFormat == "uint16") 2 else 4))
        } else {
            null
        },
        vertexCount = vertexCount,
        indexCount = if (indexed) vertexCount else null,
        indexFormat = if (indexed) indexFormat else null,
        provenance = "test",
        canonicalizationIdentity = GPUPreparedVerticesCanonicalizationIdentity.IdentityV1,
    )

    private fun packet(
        commandId: Int,
        renderStepIdentity: String,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.vertices.$commandId"),
        commandIdValue = commandId,
        analysisRecordId = "analysis.vertices.$commandId",
        passId = "pass.vertices.$commandId",
        layerId = "root",
        bindingListId = "bindings.vertices.$commandId",
        insertionReasonCode = "prepared-vertices",
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
            sourceCoverageEncoding = if (renderStepIdentity == "text.a8_mask.sample") {
                GPUSourceCoverageEncoding.ModulateRGBA
            } else {
                GPUSourceCoverageEncoding.None
            },
        ),
        renderPipelineKey = GPURenderPipelineKey("pending.pipeline.vertices"),
        bindingLayoutHash = "pending.layout.vertices",
        vertexSourceLabel = "prepared-vertices",
        targetStateHash = "target.rgba8unorm.16x16",
        originalPaintOrder = commandId,
        resourceGeneration = 7,
        frameProvenance = GPUFrameProvenance.GmContent,
        clipCoveragePlan = GPUClipCoveragePlan.NoClip,
        clipExecutionPlan = GPUClipExecutionPlan.NoClip,
    )

    private fun packetFrom(base: GPUTaskList, commandId: Int): GPUDrawPacket =
        base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
            .single { it.commandIdValue == commandId }

    private fun recording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
        rawRecording(*commands).let { recording ->
            recording.copy(
                taskList = recording.taskList.transformPackets { packet ->
                    if (packet.renderStepId.value.startsWith("image.draw.")) {
                        packet
                    } else {
                        packet.rebuilt(
                            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                            clipExecutionPlan = GPUClipExecutionPlan.NoClip,
                        )
                    }
                },
            )
        }

    private fun rawRecording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
        GPURecorder(
            GPURecordingID("recording.prepared-surface"),
            GPUFrameID(17),
            capabilities(),
        ).apply { commands.forEach(::record) }.close()

    private fun GPUTaskList.transformPackets(
        transform: (GPUDrawPacket) -> GPUDrawPacket,
    ): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) {
                task
            } else {
                val transformed = task.drawPackets.map(transform)
                GPUTask.Render(
                    taskId = task.taskId,
                    recordingId = task.recordingId,
                    phase = task.phase,
                    target = task.target,
                    loadStore = task.loadStore,
                    samplePlan = task.samplePlan,
                    resourceUses = task.resourceUses,
                    provisionalSegmentKey = task.provisionalSegmentKey,
                    drawPackets = transformed,
                    batchEligibilityByPacketId = transformed.associate { packet ->
                        packet.packetId to task.batchEligibilityByPacketId.getValue(packet.packetId)
                    },
                    sampleContinuationKey = task.sampleContinuationKey,
                    compositeMembership = task.compositeMembership,
                    depthStencilLoadStore = task.depthStencilLoadStore,
                )
            }
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUTaskList.appendingRenders(vararg render: GPUTask.Render): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks + render,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun appendedRender(
        template: GPUTask.Render,
        taskId: GPUTaskID,
        packet: GPUDrawPacket,
        provisionalSegmentKey: GPUProvisionalRenderSegmentKey,
    ): GPUTask.Render = GPUTask.Render(
        taskId = taskId,
        recordingId = template.recordingId,
        phase = template.phase,
        target = template.target,
        loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
        samplePlan = template.samplePlan,
        provisionalSegmentKey = provisionalSegmentKey,
        drawPackets = listOf(packet),
        batchEligibilityByPacketId = mapOf(
            packet.packetId to GPUPassBatchEligibility(
                kind = GPUPassBatchKind.Isolated,
                queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
            ),
        ),
    )

    private fun GPUDrawPacket.rebuilt(
        clipCoveragePlan: GPUClipCoveragePlan? = this.clipCoveragePlan,
        clipExecutionPlan: GPUClipExecutionPlan? = this.clipExecutionPlan,
    ) = GPUDrawPacket(
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

    private fun coreCommand(commandId: Int, paintOrder: Int) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 8f, 8f),
        target = target,
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        paintOrder = paintOrder,
        source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
    )

    private fun imageCommand(
        commandId: Int,
        paintOrder: Int,
    ) = GPUDrawImageRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        imageSourceId = "shared-image",
        src = GPURect(0f, 0f, 3f, 2f),
        dst = GPURect(1f, 1f, 8f, 8f),
        target = target,
        material = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "shared-image",
            imageWidth = 3,
            imageHeight = 2,
            rgbaPixels = imageArtifact().tightRgba8BytesForUpload(),
            samplingFilterMode = "nearest",
        ),
        samplingFilterMode = "nearest",
        pixelsWidth = 3,
        pixelsHeight = 2,
        pixelsRowBytes = 12,
        pixelsContentHash = imageArtifact().contentHash,
        pixelsProvenance = "test",
        paintOrder = paintOrder,
        source = GPUCommandSource("test", "drawImageRect", GPUFrameProvenance.GmContent),
    )

    private fun imageArtifact() = (GPUPreparedImageArtifactFactory.prepare(
        GPUPreparedImageSourceInput(
            GPUPreparedImageSourceClass.DecodedCpu,
            "shared-image",
            3,
            2,
            GPUPreparedImageSourceFormat.Rgba8,
            AlphaType.PREMUL,
            12,
            GPUPreparedImageProfile.Srgb,
            GPUPreparedImageOrientation.AppliedIdentity,
            GPUPreparedImageProvenance.CallerPixels,
            0,
            ByteArray(24) { (it + 1).toByte() },
        ),
    ) as GPUPreparedImageArtifactResult.Ready).artifact

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "test", "supported", true, "test"),
            GPUCapabilityFact("first_slice.draw_image_rect.prepared", "test", "supported", true, "test"),
            GPUCapabilityFact("first_slice.draw_vertices.prepared", "test", "supported", true, "test"),
            GPUCapabilityFact("first_slice.scissor.native", "test", "supported", true, "test"),
        ),
        snapshotId = "prepared-surface",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        val TARGET = GPUFrameTargetRef("target.task9.vertices")
        val BOUNDS = GPUPixelBounds(0, 0, 16, 16)
        val target = GPUTargetFacts(16, 16, "rgba8unorm")
    }
}
