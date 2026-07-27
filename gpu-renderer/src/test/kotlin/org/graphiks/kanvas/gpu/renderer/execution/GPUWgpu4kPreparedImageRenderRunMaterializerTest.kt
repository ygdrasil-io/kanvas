package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUniformAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.buildPreparedImageFrameResourcePlanFromBindings
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUWgpu4kPreparedImageRenderRunMaterializerTest {
    @Test
    fun `upload data owns a defensive non-closeable byte snapshot and exact preflight key`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val key = GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.UploadSource,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("prepared-image-upload-data:staging"),
        )
        val data = GPUPreparedNativeUploadData(key, bytes)
        bytes[0] = 99

        assertContentEquals(byteArrayOf(1, 2, 3, 4), data.bytes())
        assertFalse(AutoCloseable::class.java.isInstance(data))
    }

    @Test
    fun `texture upload preserves the borrowed preflight key for an owned native texture`() {
        val texture = fakeNativeTextureOperand(GPUDeviceGenerationID(7))
        val dataKey = GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.UploadSource,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("prepared-image-upload-data:staging"),
        )
        val destinationKey = GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.UploadDestination,
            GPUPreparedNativeOperandKind.Texture,
            gpuPreparedNativeBindingKey("GPUFrameTextureRef:image@2"),
        )
        val layout = preparedImageUploadLayoutForTest()
        val upload = GPUPreparedNativeScopeOperand.TextureUpload(
            sourceStepIndex = 4,
            data = GPUPreparedNativeUploadData(dataKey, layout.bytesForUpload()),
            destination = texture,
            destinationKey = destinationKey,
            layout = layout,
        )

        assertEquals(listOf(texture), upload.operands)
        assertEquals(listOf(dataKey, destinationKey), upload.exactOperandKeys)
        assertEquals(GPUPreparedNativeOperandOwnership.Borrowed, destinationKey.ownership)
        assertEquals(
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            texture.ownership,
        )
        assertEquals(4, upload.sourceStepIndex)
    }

    @Test
    fun `materialization result is operand-only and has no draft inheritance`() {
        val readyType = GPUPreparedRenderRunMaterialization.Ready::class
        assertEquals(
            setOf("ownedResources", "scopeOperands", "uniformUploads"),
            readyType.java.declaredMethods.map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
                .intersect(setOf("ownedResources", "scopeOperands", "uniformUploads", "draft")),
        )
        assertFalse(GPUPreparedRenderRunMaterialization::class.java.isAssignableFrom(
            GPUPreparedNativeFrameDraft::class.java,
        ))
    }

    @Test
    fun `accepted run reuses binding keys while dynamic offsets select three uniform records`() {
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(17),
        )
        val factory = RecordingPreparedImageHandleFactory()
        val artifact = preparedImageArtifact()
        val resource = buildPreparedImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = listOf(
                GPUPreparedImageBindingInput("packet.nearest.a", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.nearest.b", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.linear", GPUPreparedImageSampling.Linear),
            ),
            bindingLayoutHash =
                "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
            capabilities = preparedImageCapabilities(),
            frameIdentity = "frame.task5",
            uploadTaskId = GPUTaskID("task.upload.image"),
        )
        val allocations = listOf(
            GPUPreparedImageUniformAllocation("packet.nearest.a", 0L, 112L),
            GPUPreparedImageUniformAllocation("packet.nearest.b", 256L, 112L),
            GPUPreparedImageUniformAllocation("packet.linear", 512L, 112L),
        )
        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                        preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 5f),
                        preparedImageSemantic(artifact, GPUPreparedImageSampling.Linear, 9f),
                    ),
                    resources = listOf(resource),
                    uniformAllocations = allocations,
                ),
                GPUDeviceGenerationID(17),
            ) as GPUPreparedRenderRunMaterialization.Ready

        val upload = result.scopeOperands.filterIsInstance<
            GPUPreparedNativeScopeOperand.TextureUpload
        >().single()
        val renders = result.scopeOperands.filterIsInstance<
            GPUPreparedNativeScopeOperand.PreparedImageRenderRun
        >()
        assertEquals(
            listOf(1, 2),
            result.scopeOperands.map { it.sourceStepIndex },
        )
        assertEquals(
            listOf(
                GPUEncoderOperationKind.Upload,
                GPUEncoderOperationKind.Render,
            ),
            result.scopeOperands.map { it.operationKind },
            "uniform writes must not add a second native scope",
        )
        val uniformUpload = result.uniformUploads.single()
        assertSame(factory.uniformBuffers.single(), uniformUpload.destination.buffer)
        assertTrue(factory.bindGroupUniformBuffers.all {
            it === uniformUpload.destination.buffer
        })
        assertEquals(0L, uniformUpload.destinationOffset)
        assertEquals(listOf(2), uniformUpload.consumerSourceStepIndices)
        val drawEntries = renders.single().drawEntries
        assertContentEquals(
            drawEntries[0].uniformBytes(),
            uniformUpload.data.bytes().copyOfRange(0, 112),
        )
        assertContentEquals(
            drawEntries[1].uniformBytes(),
            uniformUpload.data.bytes().copyOfRange(256, 368),
        )
        assertContentEquals(
            drawEntries[2].uniformBytes(),
            uniformUpload.data.bytes().copyOfRange(512, 624),
        )
        assertTrue(upload.sourceStepIndex < renders.single().sourceStepIndex)
        assertEquals(listOf(0L, 256L, 512L), drawEntries.map { it.dynamicUniformOffset })
        assertNotEquals(
            drawEntries[0].uniformBytes().toList(),
            drawEntries[1].uniformBytes().toList(),
        )
        assertSame(drawEntries[0].bindGroup.bindGroup, drawEntries[1].bindGroup.bindGroup)
        assertNotSame(drawEntries[0].bindGroup.bindGroup, drawEntries[2].bindGroup.bindGroup)
        assertTrue(drawEntries.drop(1).all {
            it.pipeline.pipeline === drawEntries.first().pipeline.pipeline
        })
        assertEquals(1, nativeDevice.pipelineCreates)
        assertEquals(listOf("nearest", "linear"), factory.samplerFilters)
        assertEquals(1, factory.textureCreates)
        assertEquals(1, factory.textureViewCreates)
        assertEquals(2, factory.samplerCreates)
        assertEquals(1, factory.uniformBufferCreates)
        assertEquals(2, factory.bindGroupCreates)

        result.ownedResources.single().close()
        result.ownedResources.single().close()
        assertTrue(factory.closeCounts.values.all { it == 1 })

        cache.invalidateForDeviceLoss()
        assertTrue(nativeDevice.closeCounts.values.all { it == 1 })
        cache.close()
        assertTrue(nativeDevice.closeCounts.values.all { it == 1 })
    }

    @Test
    fun `two artifacts with the same descriptor share one sampler across the frame`() {
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(171),
        )
        val factory = RecordingPreparedImageHandleFactory()
        val firstArtifact = preparedImageArtifact(pixelSeed = 31)
        val secondArtifact = preparedImageArtifact(pixelSeed = 47)
        val firstResource = preparedImageResource(
            firstArtifact,
            "packet.artifact.first",
        )
        val secondResource = preparedImageResource(
            secondArtifact,
            "packet.artifact.second",
        )

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2, 3),
                    packets = listOf(
                        preparedImageSemantic(
                            firstArtifact,
                            GPUPreparedImageSampling.Nearest,
                            1f,
                        ),
                        preparedImageSemantic(
                            secondArtifact,
                            GPUPreparedImageSampling.Nearest,
                            7f,
                        ),
                    ),
                    resources = listOf(firstResource, secondResource),
                    uniformAllocations = listOf(
                        firstResource.bindingRequests.single().uniformAllocation,
                        secondResource.bindingRequests.single().uniformAllocation,
                    ),
                ),
                GPUDeviceGenerationID(171),
            )
        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(result)

        assertEquals(1, factory.samplerCreates)
        assertEquals(listOf("nearest"), factory.samplerFilters)
        assertEquals(2, factory.textureCreates)
        assertEquals(2, factory.textureViewCreates)
        assertEquals(2, factory.uniformBufferCreates)
        assertEquals(2, factory.bindGroupCreates)

        ready.ownedResources.single().close()
        cache.close()
    }

    @Test
    fun `authoritative generation mismatch refuses before every native handle creation`() {
        val generation7 = GPUDeviceGenerationID(7)
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(nativeDevice.device, generation7)
        val factory = RecordingPreparedImageHandleFactory()
        val artifact = preparedImageArtifact(pixelSeed = 71)
        val resource = preparedImageResource(artifact, "packet.generation-mismatch")

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(
                            artifact,
                            GPUPreparedImageSampling.Nearest,
                            1f,
                        ),
                    ),
                    resources = listOf(resource),
                    uniformAllocations =
                        resource.bindingRequests.map { it.uniformAllocation },
                ),
                GPUDeviceGenerationID(8),
            )

        val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_GENERATION, refused.code)
        assertEquals("native", refused.facts["boundary"])
        assertEquals(0, nativeDevice.handleCreates)
        assertEquals(0, factory.handleCreates)
        cache.close()
    }

    @Test
    fun `valid then unsupported pipeline keys refuse atomically before cache or run handles`() {
        val generation = GPUDeviceGenerationID(172)
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(nativeDevice.device, generation)
        val factory = RecordingPreparedImageHandleFactory()
        val artifact = preparedImageArtifact(pixelSeed = 72)
        val valid = preparedImageSemantic(
            artifact,
            GPUPreparedImageSampling.Nearest,
            1f,
        )
        val unsupported = preparedImageSemantic(
            artifact,
            GPUPreparedImageSampling.Nearest,
            7f,
        )
        unsupported.javaClass.getDeclaredField("pipelineKey").apply {
            isAccessible = true
            set(
                unsupported,
                unsupported.pipelineKey.copy(targetFormat = "BGRA8Unorm"),
            )
        }
        val resource = buildPreparedImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = listOf(
                GPUPreparedImageBindingInput("packet.valid", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput(
                    "packet.unsupported",
                    GPUPreparedImageSampling.Nearest,
                ),
            ),
            bindingLayoutHash =
                "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
            capabilities = preparedImageCapabilities(),
            frameIdentity = "frame.atomic-pipeline-refusal",
            uploadTaskId = GPUTaskID("task.upload.atomic-pipeline-refusal"),
        )

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(valid, unsupported),
                    resources = listOf(resource),
                    uniformAllocations =
                        resource.bindingRequests.map { it.uniformAllocation },
                ),
                generation,
            )

        val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.IMAGE_PROFILE_CONVERSION, refused.code)
        assertEquals("RGBA8UnormSrgb", valid.pipelineKey.targetFormat)
        assertEquals("BGRA8Unorm", unsupported.pipelineKey.targetFormat)
        assertEquals(0, nativeDevice.handleCreates)
        assertEquals(0, factory.handleCreates)
        cache.close()
    }

    @Test
    fun `mismatched native binding identity refuses before any handle creation`() {
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(18),
        )
        val factory = RecordingPreparedImageHandleFactory()
        val artifact = preparedImageArtifact(pixelSeed = 2)
        val resource = preparedImageResource(artifact, "packet.foreign").let { plan ->
            plan.copy(
                bindingRequests = plan.bindingRequests.map { request ->
                    request.copy(bindingLayoutHash = "foreign.prepared-image.binding-layout")
                },
            )
        }

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(
                            artifact,
                            GPUPreparedImageSampling.Nearest,
                            1f,
                        ),
                    ),
                    resources = listOf(resource),
                    uniformAllocations =
                        resource.bindingRequests.map { it.uniformAllocation },
                ),
                GPUDeviceGenerationID(18),
            )

        val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals("native", refused.facts["boundary"])
        assertEquals(0, factory.handleCreates)
        assertEquals(0, nativeDevice.pipelineCreates)
        cache.close()
    }

    @Test
    fun `mismatched packet pipeline binding identity refuses instead of repairing the key`() {
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(181),
        )
        val factory = RecordingPreparedImageHandleFactory()
        val artifact = preparedImageArtifact(pixelSeed = 12)
        val packet = preparedImageSemantic(
            artifact,
            GPUPreparedImageSampling.Nearest,
            1f,
        )
        packet.javaClass.getDeclaredField("pipelineKey").apply {
            isAccessible = true
            set(
                packet,
                packet.pipelineKey.copy(
                    bindingLayoutHash = "foreign.prepared-image.pipeline-layout",
                ),
            )
        }
        val resource = preparedImageResource(artifact, "packet.foreign-pipeline")

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(packet),
                    resources = listOf(resource),
                    uniformAllocations =
                        resource.bindingRequests.map { it.uniformAllocation },
                ),
                GPUDeviceGenerationID(181),
            )

        val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals("native", refused.facts["boundary"])
        assertEquals(0, factory.handleCreates)
        assertEquals(0, nativeDevice.pipelineCreates)
        cache.close()
    }

    @Test
    fun `missing packet binding preserves canonical refusal through native boundary`() {
        val artifact = preparedImageArtifact(pixelSeed = 19)
        val completeResource = buildPreparedImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = listOf(
                GPUPreparedImageBindingInput("packet.one", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.two", GPUPreparedImageSampling.Nearest),
            ),
            bindingLayoutHash = "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
            capabilities = preparedImageCapabilities(),
            frameIdentity = "frame.missing-binding",
            uploadTaskId = GPUTaskID("task.upload.missing-binding"),
        )
        val resource = completeResource.copy(
            bindingRequests = completeResource.bindingRequests.take(1),
        )
        val allocations = listOf(
            GPUPreparedImageUniformAllocation("packet.one", 0L, 112L),
            GPUPreparedImageUniformAllocation("packet.two", 256L, 112L),
        )
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(182),
        )

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(
            cache,
            RecordingPreparedImageHandleFactory(),
        ).materializeAcceptedRun(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 2f),
                ),
                resources = listOf(resource),
                uniformAllocations = allocations,
            ),
            GPUDeviceGenerationID(182),
        )

        val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
        assertEquals("native", refused.facts["boundary"])
        assertFalse(refused.code.startsWith("unsupported.surface.prepared.image-source."))
        cache.close()
    }

    @Test
    fun `accepted run copies exact preflight scope keys without rebuilding labels`() {
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(19),
        )
        val artifact = preparedImageArtifact(pixelSeed = 3)
        val resource = preparedImageResource(artifact, "packet.exact")
        val exactScopeKeys = preparedImagePreflightScopeKeys(
            listOf(resource),
            resource.bindingRequests.map { it.uniformAllocation },
            listOf(4, 9),
        )
        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(
            cache,
            RecordingPreparedImageHandleFactory(),
        ).materializeAcceptedRun(
            GPUPreparedImageRenderRunPlan(
                sourceScopeIndices = listOf(4, 9),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                ),
                resources = listOf(resource),
                uniformAllocations = resource.bindingRequests.map { it.uniformAllocation },
                exactScopeKeys = exactScopeKeys,
            ),
            GPUDeviceGenerationID(19),
        ) as GPUPreparedRenderRunMaterialization.Ready

        assertEquals(
            exactScopeKeys.map(GPUPreparedNativeScopeKey::operandKeys),
            result.scopeOperands.map(GPUPreparedNativeScopeOperand::exactOperandKeys),
        )
        val render = result.scopeOperands.filterIsInstance<
            GPUPreparedNativeScopeOperand.PreparedImageRenderRun
        >().single()
        val draw = render.drawEntries.single()
        assertEquals(listOf(draw.pipeline, draw.bindGroup), render.operands)
        assertEquals(
            GPUPreparedNativeOperandRole.RenderColorTarget,
            render.exactOperandKeys.first().role,
        )
        assertTrue(render.operands.none { it is GPUPreparedNativeTextureViewOperand })

        result.ownedResources.single().close()
        cache.close()
    }

    @Test
    fun `plan refuses a binding whose artifact differs from its packet`() {
        val artifact = preparedImageArtifact(pixelSeed = 1)
        val foreignArtifact = preparedImageArtifact(pixelSeed = 17)
        val resource = preparedImageResource(artifact, "packet.one").copy(
            bindingRequests = preparedImageResource(artifact, "packet.one")
                .bindingRequests
                .map { it.copy(artifactKey = foreignArtifact.key) },
        )

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f)),
                resources = listOf(resource),
                uniformAllocations = listOf(
                    GPUPreparedImageUniformAllocation("packet.one", 0L, 112L),
                ),
            ),
            "unsupported.prepared_image.artifact_identity",
        )
    }

    @Test
    fun `plan refuses complete bindings and packet from a different artifact than the resource`() {
        val resourceArtifact = preparedImageArtifact(pixelSeed = 5)
        val foreignArtifact = preparedImageArtifact(pixelSeed = 21)
        val resource = preparedImageResource(resourceArtifact, "packet.swap").copy(
            bindingRequests = preparedImageResource(foreignArtifact, "packet.swap").bindingRequests,
        )

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(
                    preparedImageSemantic(
                        foreignArtifact,
                        GPUPreparedImageSampling.Nearest,
                        1f,
                    ),
                ),
                resources = listOf(resource),
                uniformAllocations = listOf(
                    GPUPreparedImageUniformAllocation("packet.swap", 0L, 112L),
                ),
            ),
            "unsupported.prepared_image.artifact_identity",
        )
    }

    @Test
    fun `plan refuses copied upload bytes from a different same-sized artifact before handles`() {
        val artifactA = preparedImageArtifact(pixelSeed = 22)
        val artifactB = preparedImageArtifact(pixelSeed = 23)
        val resourceA = preparedImageResource(artifactA, "packet.a")
        val resourceB = preparedImageResource(artifactB, "packet.b")
        val swapped = resourceB.copy(uploadLayout = resourceA.uploadLayout)
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(29),
        )
        val factory = RecordingPreparedImageHandleFactory()

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(
                            artifactB,
                            GPUPreparedImageSampling.Nearest,
                            1f,
                        ),
                    ),
                    resources = listOf(swapped),
                    uniformAllocations = swapped.bindingRequests.map { it.uniformAllocation },
                ),
                GPUDeviceGenerationID(29),
            )

        assertEquals(
            "unsupported.prepared_image.upload_provenance",
            assertIs<GPUPreparedRenderRunMaterialization.Refused>(result).code,
        )
        assertEquals(0, factory.handleCreates)
        assertEquals(0, nativeDevice.pipelineCreates)
        cache.close()
    }

    @Test
    fun `plan refuses constructor-forged upload bytes before handles`() {
        val artifactA = preparedImageArtifact(pixelSeed = 24)
        val artifactB = preparedImageArtifact(pixelSeed = 25)
        val resourceA = preparedImageResource(artifactA, "packet.a")
        val resourceB = preparedImageResource(artifactB, "packet.b")
        val forged = GPUPreparedImageFrameResourcePlan(
            stagingRef = resourceB.stagingRef,
            textureRef = resourceB.textureRef,
            frameTextureRef = resourceB.frameTextureRef,
            uniformRef = resourceB.uniformRef,
            textureDescriptor = resourceB.textureDescriptor,
            uploadLayout = resourceA.uploadLayout,
            uploadTaskLayout = resourceB.uploadTaskLayout,
            bindingRequests = resourceB.bindingRequests,
            preparationRequests = resourceB.preparationRequests,
            memoryAllocations = resourceB.memoryAllocations,
            uploadTaskId = resourceB.uploadTaskId,
            artifact = artifactB,
        )
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(29),
        )
        val factory = RecordingPreparedImageHandleFactory()

        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(
                            artifactB,
                            GPUPreparedImageSampling.Nearest,
                            1f,
                        ),
                    ),
                    resources = listOf(forged),
                    uniformAllocations = forged.bindingRequests.map { it.uniformAllocation },
                ),
                GPUDeviceGenerationID(29),
            )

        assertEquals(
            "unsupported.prepared_image.upload_provenance",
            assertIs<GPUPreparedRenderRunMaterialization.Refused>(result).code,
        )
        assertEquals(0, factory.handleCreates)
        assertEquals(0, nativeDevice.pipelineCreates)
        cache.close()
    }

    @Test
    fun `plan refuses a run allocation that differs from the sealed binding`() {
        val artifact = preparedImageArtifact(pixelSeed = 25)
        val resource = preparedImageResource(artifact, "packet.diverged").copy(
            bindingRequests = preparedImageResource(artifact, "packet.diverged")
                .bindingRequests
                .map { request ->
                    request.copy(
                        uniformAllocation = GPUPreparedImageUniformAllocation(
                            "packet.diverged",
                            256L,
                            112L,
                        ),
                    )
                },
        )

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                ),
                resources = listOf(resource),
                uniformAllocations = listOf(
                    GPUPreparedImageUniformAllocation("packet.diverged", 0L, 112L),
                ),
            ),
            "unsupported.prepared_image.uniform_identity",
        )
    }

    @Test
    fun `plan refuses an unaligned sealed uniform allocation`() {
        val artifact = preparedImageArtifact(pixelSeed = 29)
        val unaligned = GPUPreparedImageUniformAllocation("packet.unaligned", 1L, 112L)
        val resource = preparedImageResource(artifact, unaligned.packetId).copy(
            bindingRequests = preparedImageResource(artifact, unaligned.packetId)
                .bindingRequests
                .map { request -> request.copy(uniformAllocation = unaligned) },
        )

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                ),
                resources = listOf(resource),
                uniformAllocations = listOf(unaligned),
            ),
            "unsupported.prepared_image.uniform_alignment",
        )
    }

    @Test
    fun `plan refuses a sealed uniform allocation beyond prepared buffer capacity`() {
        val artifact = preparedImageArtifact(pixelSeed = 30)
        val outOfRange = GPUPreparedImageUniformAllocation("packet.out-of-range", 256L, 112L)
        val resource = preparedImageResource(artifact, outOfRange.packetId).copy(
            bindingRequests = preparedImageResource(artifact, outOfRange.packetId)
                .bindingRequests
                .map { request -> request.copy(uniformAllocation = outOfRange) },
        )

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                ),
                resources = listOf(resource),
                uniformAllocations = listOf(outOfRange),
            ),
            "unsupported.prepared_image.uniform_range",
        )
    }

    @Test
    fun `plan refuses overlapping sealed uniform allocations in one resource`() {
        val artifact = preparedImageArtifact(pixelSeed = 31)
        val resource = buildPreparedImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = listOf(
                GPUPreparedImageBindingInput("packet.first", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.second", GPUPreparedImageSampling.Linear),
            ),
            bindingLayoutHash =
                "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
            capabilities = preparedImageCapabilities(),
            frameIdentity = "frame.overlap",
            uploadTaskId = GPUTaskID("task.upload.overlap"),
        ).let { original ->
            original.copy(
                bindingRequests = original.bindingRequests.map { request ->
                    request.copy(
                        uniformAllocation = GPUPreparedImageUniformAllocation(
                            request.packetId,
                            0L,
                            112L,
                        ),
                    )
                },
            )
        }

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Linear, 9f),
                ),
                resources = listOf(resource),
                uniformAllocations = resource.bindingRequests.map { it.uniformAllocation },
            ),
            "unsupported.prepared_image.uniform_overlap",
        )
    }

    @Test
    fun `plan refuses duplicate resource uploads for one artifact`() {
        val artifact = preparedImageArtifact(pixelSeed = 33)

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(1, 2, 3),
                packets = listOf(
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                    preparedImageSemantic(artifact, GPUPreparedImageSampling.Linear, 9f),
                ),
                resources = listOf(
                    preparedImageResource(artifact, "packet.one"),
                    preparedImageResource(artifact, "packet.two", GPUPreparedImageSampling.Linear),
                ),
                uniformAllocations = listOf(
                    GPUPreparedImageUniformAllocation("packet.one", 0L, 112L),
                    GPUPreparedImageUniformAllocation("packet.two", 256L, 112L),
                ),
            ),
            "unsupported.prepared_image.artifact_identity",
        )
    }

    @Test
    fun `plan refuses an artifact upload after its first consumer`() {
        val firstArtifact = preparedImageArtifact(pixelSeed = 49)
        val secondArtifact = preparedImageArtifact(pixelSeed = 65)

        assertPreparedImageRefusal(
            preparedImageRenderRunPlan(
                sourceScopeIndices = listOf(5, 1, 2),
                packets = listOf(
                    preparedImageSemantic(firstArtifact, GPUPreparedImageSampling.Nearest, 1f),
                    preparedImageSemantic(secondArtifact, GPUPreparedImageSampling.Nearest, 9f),
                ),
                resources = listOf(
                    preparedImageResource(firstArtifact, "packet.first"),
                    preparedImageResource(secondArtifact, "packet.second"),
                ),
                uniformAllocations = listOf(
                    GPUPreparedImageUniformAllocation("packet.first", 0L, 112L),
                    GPUPreparedImageUniformAllocation("packet.second", 0L, 112L),
                ),
            ),
            "unsupported.prepared_image.upload_order",
        )
    }

    @Test
    fun `successful materialization closes a shared native identity once`() {
        val nativeDevice = RecordingPreparedImageDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(
            nativeDevice.device,
            GPUDeviceGenerationID(23),
        )
        val factory = SharingPreparedImageHandleFactory()
        val artifact = preparedImageArtifact(pixelSeed = 81)
        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                    ),
                    resources = listOf(preparedImageResource(artifact, "packet.shared")),
                    uniformAllocations = listOf(
                        GPUPreparedImageUniformAllocation("packet.shared", 0L, 112L),
                    ),
                ),
                GPUDeviceGenerationID(23),
            ) as GPUPreparedRenderRunMaterialization.Ready

        result.ownedResources.single().close()
        result.ownedResources.single().close()

        assertEquals(1, factory.closeCalls)
        cache.close()
    }
}

internal fun fakeNativeTextureOperand(
    generation: GPUDeviceGenerationID,
): GPUPreparedNativeTextureOperand {
    val texture = GPUTexture::class.java.cast(
        Proxy.newProxyInstance(GPUTexture::class.java.classLoader, arrayOf(GPUTexture::class.java)) {
                proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "close" -> Unit
                "getLabel" -> "prepared-image-test-texture"
                "setLabel" -> Unit
                "toString" -> "PreparedImageTestTexture"
                else -> error("Unexpected fake texture call: ${method.name}")
            }
        },
    )
    return GPUPreparedNativeTextureOperand(
        texture,
        generation,
        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
    )
}

internal fun preparedImageUploadLayoutForTest(): GPUPreparedImageUploadLayout =
    GPUPreparedImageUploadLayout(
        sourceBytesPerRow = 4L,
        logicalBytesPerRow = 4L,
        bytesPerRow = 256L,
        rowsPerImage = 1,
        width = 1,
        height = 1,
        paddedUploadBytes = byteArrayOf(1, 2, 3, 4) + ByteArray(252),
    )

private fun preparedImageArtifact(pixelSeed: Int = 1) =
    (GPUPreparedImageArtifactFactory.prepare(
        GPUPreparedImageSourceInput(
            sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
            sourceId = "task5-materializer",
            width = 2,
            height = 2,
            sourceFormat = GPUPreparedImageSourceFormat.Rgba8,
            alphaType = AlphaType.PREMUL,
            sourceRowBytes = 8,
            profile = GPUPreparedImageProfile.Srgb,
            orientation = GPUPreparedImageOrientation.AppliedIdentity,
            provenance = GPUPreparedImageProvenance.CallerPixels,
            sourceGeneration = 1,
            pixelBytes = ByteArray(16) { (it + pixelSeed).toByte() },
        ),
    ) as GPUPreparedImageArtifactResult.Ready).artifact

internal fun preparedImageResource(
    artifact: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact,
    packetId: String,
    sampling: GPUPreparedImageSampling = GPUPreparedImageSampling.Nearest,
): GPUPreparedImageFrameResourcePlan = buildPreparedImageFrameResourcePlanFromBindings(
    artifact = artifact,
    bindingInputs = listOf(GPUPreparedImageBindingInput(packetId, sampling)),
    bindingLayoutHash = "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
    capabilities = preparedImageCapabilities(),
    frameIdentity = "frame.$packetId",
    uploadTaskId = GPUTaskID("task.upload.$packetId"),
)

internal fun preparedImageRenderRunPlan(
    sourceScopeIndices: List<Int>,
    packets: List<GPUDrawSemanticPayload.SampledImage>,
    resources: List<GPUPreparedImageFrameResourcePlan>,
    uniformAllocations: List<GPUPreparedImageUniformAllocation>,
): GPUPreparedImageRenderRunPlan = GPUPreparedImageRenderRunPlan(
    sourceScopeIndices = sourceScopeIndices,
    packets = packets,
    resources = resources,
    uniformAllocations = uniformAllocations,
    exactScopeKeys = preparedImagePreflightScopeKeys(
        resources,
        uniformAllocations,
        sourceScopeIndices,
    ),
)

private fun preparedImagePreflightScopeKeys(
    resources: List<GPUPreparedImageFrameResourcePlan>,
    allocations: List<GPUPreparedImageUniformAllocation>,
    sourceScopeIndices: List<Int>,
): List<GPUPreparedNativeScopeKey> {
    val uploads = resources.mapIndexed { index, resource ->
        val textureGenerationLabel =
            "GPUFrameTextureRef:${resource.frameTextureRef.value}@${11 + index}"
        GPUPreparedNativeScopeKey(
            sourceStepIndex = sourceScopeIndices[index],
            operationKind = GPUEncoderOperationKind.Upload,
            resourceGenerationLabels = listOf(
                "GPUFrameBufferRef:${resource.stagingRef.value}@${7 + index}",
                textureGenerationLabel,
            ),
            operandKeys = listOf(
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.UploadSource,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey(
                        "prepared-image-upload-data:${resource.stagingRef.value}",
                    ),
                ),
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.UploadDestination,
                    GPUPreparedNativeOperandKind.Texture,
                    gpuPreparedNativeBindingKey(textureGenerationLabel),
                ),
            ),
        )
    }
    val targetGenerationLabel = "GPUFrameTargetRef:target.scene@13"
    val render = GPUPreparedNativeScopeKey(
        sourceStepIndex = sourceScopeIndices.last(),
        operationKind = GPUEncoderOperationKind.Render,
        resourceGenerationLabels = listOf(targetGenerationLabel),
        operandKeys = listOf(
            GPUPreparedNativeOperandKey(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandKind.TextureView,
                gpuPreparedNativeBindingKey(targetGenerationLabel),
            ),
        ) + allocations.flatMap { allocation ->
            listOf(
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    gpuPreparedNativeBindingKey(
                        "preflight.bridge.pipeline.${allocation.packetId}",
                    ),
                ),
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    gpuPreparedNativeBindingKey(
                        "preflight.bridge.bind-group.${allocation.packetId}",
                    ),
                ),
            )
        },
    )
    return uploads + render
}

private fun assertPreparedImageRefusal(
    plan: GPUPreparedImageRenderRunPlan,
    code: String,
) {
    val nativeDevice = RecordingPreparedImageDevice()
    val cache = GPUWgpu4kPreparedImageSessionCache(
        nativeDevice.device,
        GPUDeviceGenerationID(29),
    )
    val result = GPUWgpu4kPreparedImageRenderRunMaterializer(
        cache,
        RecordingPreparedImageHandleFactory(),
    ).materializeAcceptedRun(plan, GPUDeviceGenerationID(29))

    assertEquals(code, assertIs<GPUPreparedRenderRunMaterialization.Refused>(result).code)
    cache.close()
}

private fun preparedImageSemantic(
    artifact: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact,
    sampling: GPUPreparedImageSampling,
    left: Float,
): GPUDrawSemanticPayload.SampledImage =
    GPUPreparedImagePayloadGatherer().gatherSemantic(
        GPUPreparedImagePayloadInput(
            payloadRef = GPUDrawPayloadRef(left.toInt(), "image.draw.texture_upload"),
            artifact = artifact,
            geometry = GPUPreparedImageGeometry(
                GPUPreparedImageGeometryClass.Rect,
                listOf(
                    GPUPreparedImageVertex(left, 1f, 0f, 0f),
                    GPUPreparedImageVertex(left + 4f, 1f, 1f, 0f),
                    GPUPreparedImageVertex(left + 4f, 5f, 1f, 1f),
                    GPUPreparedImageVertex(left, 5f, 0f, 1f),
                ),
                listOf(0, 1, 2, 0, 2, 3),
            ),
            sampling = sampling,
            tintPremultipliedRgba = listOf(1f, 1f, 1f, 1f),
            atlasColorPremultipliedRgba = null,
            atlasSourceBlend = null,
            targetBounds = GPUPixelBounds(0, 0, 16, 16),
            scissorBounds = GPUPixelBounds(0, 0, 16, 16),
            blendPlanIdentity = "SrcOver",
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )

private fun preparedImageCapabilities() = GPUCapabilities(
    implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
    facts = emptyList(),
    snapshotId = "task5-materializer",
    limits = GPULimits(
        maxTextureDimension2D = 8192,
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
        maxBufferSize = 1L shl 30,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
    ),
)

internal class RecordingPreparedImageDevice {
    var handleCreates = 0
    var pipelineCreates = 0
    val closeCounts = linkedMapOf<String, Int>()

    val device: GPUDevice = nativeHandle("device") { methodName ->
        when (methodName) {
            "createBindGroupLayout" -> {
                handleCreates += 1
                nativeHandle<io.ygdrasil.webgpu.GPUBindGroupLayout>("layout")
            }
            "createShaderModule" -> {
                handleCreates += 1
                nativeHandle<GPUShaderModule>("shader")
            }
            "createPipelineLayout" -> {
                handleCreates += 1
                nativeHandle<io.ygdrasil.webgpu.GPUPipelineLayout>("pipeline-layout")
            }
            "createRenderPipeline" -> {
                handleCreates += 1
                pipelineCreates += 1
                nativeHandle<GPURenderPipeline>("pipeline-$pipelineCreates")
            }
            else -> null
        }
    }

    private inline fun <reified T> nativeHandle(
        label: String,
        crossinline other: (String) -> Any? = { null },
    ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
            proxy, method, args ->
        when (method.name) {
            "close" -> closeCounts[label] = closeCounts.getOrDefault(label, 0) + 1
            "toString", "getLabel" -> label
            "setLabel" -> Unit
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.singleOrNull()
            else -> other(method.name)
        }
    } as T
}

internal class RecordingPreparedImageHandleFactory : GPUPreparedImageNativeHandleFactory {
    val closeCounts = linkedMapOf<String, Int>()
    val samplerFilters = mutableListOf<String>()
    val uniformBuffers = mutableListOf<GPUBuffer>()
    val bindGroupUniformBuffers = mutableListOf<GPUBuffer>()
    var textureCreates = 0
    var textureViewCreates = 0
    var samplerCreates = 0
    var uniformBufferCreates = 0
    var bindGroupCreates = 0
    var handleCreates = 0
    private var ordinal = 0

    override fun createTexture(request: GPUPreparedImageFrameResourcePlan): GPUTexture {
        textureCreates += 1
        return handle("texture")
    }

    override fun createTextureView(
        texture: GPUTexture,
        request: GPUPreparedImageFrameResourcePlan,
    ): GPUTextureView {
        textureViewCreates += 1
        return handle("view")
    }

    override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler {
        samplerCreates += 1
        samplerFilters += descriptor.magFilter
        return handle("sampler.${descriptor.magFilter}")
    }

    override fun createUniformBuffer(size: Long): GPUBuffer {
        uniformBufferCreates += 1
        return handle<GPUBuffer>("uniform").also(uniformBuffers::add)
    }

    override fun createBindGroup(
        request: GPUPreparedImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup {
        bindGroupCreates += 1
        bindGroupUniformBuffers += uniformBuffer
        return handle("bind.${request.packetId}")
    }

    private inline fun <reified T> handle(prefix: String): T {
        handleCreates += 1
        val label = "$prefix.${ordinal++}"
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                proxy, method, args ->
            when (method.name) {
                "close" -> closeCounts[label] = closeCounts.getOrDefault(label, 0) + 1
                "toString", "getLabel" -> label
                "setLabel" -> Unit
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.singleOrNull()
                else -> null
            }
        } as T
    }
}

private class SharingPreparedImageHandleFactory : GPUPreparedImageNativeHandleFactory {
    var closeCalls = 0
    private val shared = Proxy.newProxyInstance(
        GPUTexture::class.java.classLoader,
        arrayOf(
            GPUTexture::class.java,
            GPUTextureView::class.java,
            GPUSampler::class.java,
            GPUBuffer::class.java,
            GPUBindGroup::class.java,
        ),
    ) { proxy, method, args ->
        when (method.name) {
            "close" -> closeCalls += 1
            "toString", "getLabel" -> "shared-prepared-image-handle"
            "setLabel" -> Unit
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.singleOrNull()
            else -> null
        }
    }

    override fun createTexture(request: GPUPreparedImageFrameResourcePlan): GPUTexture =
        shared as GPUTexture

    override fun createTextureView(
        texture: GPUTexture,
        request: GPUPreparedImageFrameResourcePlan,
    ): GPUTextureView = shared as GPUTextureView

    override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler =
        shared as GPUSampler

    override fun createUniformBuffer(size: Long): GPUBuffer = shared as GPUBuffer

    override fun createBindGroup(
        request: GPUPreparedImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup = shared as GPUBindGroup
}
