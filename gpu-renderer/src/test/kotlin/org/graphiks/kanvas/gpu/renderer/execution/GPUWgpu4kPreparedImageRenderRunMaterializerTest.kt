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
    fun `accepted run uploads once and keeps sampler and uniform axes out of the pipeline cache`() {
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
                GPUPreparedImageBindingInput("packet.nearest", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.linear", GPUPreparedImageSampling.Linear),
            ),
            bindingLayoutHash = PREPARED_IMAGE_BINDING_LAYOUT_HASH,
            capabilities = preparedImageCapabilities(),
            frameIdentity = "frame.task5",
            uploadTaskId = GPUTaskID("task.upload.image"),
        )
        val allocations = listOf(
            GPUPreparedImageUniformAllocation("packet.nearest", 0L, 112L),
            GPUPreparedImageUniformAllocation("packet.linear", 256L, 112L),
        )
        val result = GPUWgpu4kPreparedImageRenderRunMaterializer(cache, factory)
            .materializeAcceptedRun(
                preparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2),
                    packets = listOf(
                        preparedImageSemantic(artifact, GPUPreparedImageSampling.Nearest, 1f),
                        preparedImageSemantic(artifact, GPUPreparedImageSampling.Linear, 9f),
                    ),
                    resources = listOf(resource),
                    uniformAllocations = allocations,
                ),
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
        assertTrue(upload.sourceStepIndex < renders.single().sourceStepIndex)
        assertEquals(listOf(0L, 256L), drawEntries.map { it.dynamicUniformOffset })
        assertNotEquals(
            drawEntries[0].uniformBytes().toList(),
            drawEntries[1].uniformBytes().toList(),
        )
        assertSame(drawEntries[0].pipeline.pipeline, drawEntries[1].pipeline.pipeline)
        assertEquals(1, nativeDevice.pipelineCreates)
        assertEquals(listOf("nearest", "linear"), factory.samplerFilters)

        result.ownedResources.single().close()
        result.ownedResources.single().close()
        assertTrue(factory.closeCounts.values.all { it == 1 })

        cache.invalidateForDeviceLoss()
        assertTrue(nativeDevice.closeCounts.values.all { it == 1 })
        cache.close()
        assertTrue(nativeDevice.closeCounts.values.all { it == 1 })
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
            bindingLayoutHash = PREPARED_IMAGE_BINDING_LAYOUT_HASH,
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

private fun preparedImageResource(
    artifact: org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact,
    packetId: String,
    sampling: GPUPreparedImageSampling = GPUPreparedImageSampling.Nearest,
): GPUPreparedImageFrameResourcePlan = buildPreparedImageFrameResourcePlanFromBindings(
    artifact = artifact,
    bindingInputs = listOf(GPUPreparedImageBindingInput(packetId, sampling)),
    bindingLayoutHash = PREPARED_IMAGE_BINDING_LAYOUT_HASH,
    capabilities = preparedImageCapabilities(),
    frameIdentity = "frame.$packetId",
    uploadTaskId = GPUTaskID("task.upload.$packetId"),
)

private fun preparedImageRenderRunPlan(
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
    ).materializeAcceptedRun(plan)

    assertEquals(code, assertIs<GPUPreparedRenderRunMaterialization.Refused>(result).code)
    cache.close()
}

private fun preparedImageSemantic(
    artifact: org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact,
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
    var pipelineCreates = 0
    val closeCounts = linkedMapOf<String, Int>()

    val device: GPUDevice = nativeHandle("device") { methodName ->
        when (methodName) {
            "createBindGroupLayout" -> nativeHandle<io.ygdrasil.webgpu.GPUBindGroupLayout>("layout")
            "createShaderModule" -> nativeHandle<GPUShaderModule>("shader")
            "createPipelineLayout" -> nativeHandle<io.ygdrasil.webgpu.GPUPipelineLayout>("pipeline-layout")
            "createRenderPipeline" -> {
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
    var handleCreates = 0
    private var ordinal = 0

    override fun createTexture(request: GPUPreparedImageFrameResourcePlan): GPUTexture =
        handle("texture")

    override fun createTextureView(
        texture: GPUTexture,
        request: GPUPreparedImageFrameResourcePlan,
    ): GPUTextureView = handle("view")

    override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler {
        samplerFilters += descriptor.magFilter
        return handle("sampler.${descriptor.magFilter}")
    }

    override fun createUniformBuffer(size: Long): GPUBuffer =
        handle<GPUBuffer>("uniform").also(uniformBuffers::add)

    override fun createBindGroup(
        request: GPUPreparedImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup {
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
