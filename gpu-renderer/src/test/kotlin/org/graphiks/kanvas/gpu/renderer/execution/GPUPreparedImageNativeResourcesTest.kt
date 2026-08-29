package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageRouteCapability
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.buildImageFrameResourcePlanFromBindings

class GPUPreparedImageNativeResourcesTest {
    @Test
    fun `bounded nearest 1 to 1 resource plan reaches preflight without handles`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput(
                    packetId = "packet.bounded.nearest",
                    sampling = GPUPreparedImageSampling.Nearest,
                    routeCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
                    boundedGeometry = boundedGeometry(),
                ),
            ),
        )

        val sealed = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals(
            GPUPreparedImageRouteCapability.BoundedNearest1To1,
            sealed.request.resourcePlan.bindingRequests.single().routeCapability,
        )
        assertEquals("nearest", sealed.request.resourcePlan.bindingRequests.single().sampler.magFilter)
    }

    @Test
    fun `bounded route rejects linear or forged corner geometry before materialization`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput(
                    packetId = "packet.bounded.refusal",
                    sampling = GPUPreparedImageSampling.Nearest,
                    routeCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
                    boundedGeometry = boundedGeometry(),
                ),
            ),
        )
        val binding = fixture.plan.bindingRequests.single()
        val malformedCorners = binding.copy(
            boundedGeometry = GPUPreparedImageGeometry(
                GPUPreparedImageGeometryClass.Rect,
                listOf(
                    GPUPreparedImageVertex(4f, 6f, 0f, 0f),
                    GPUPreparedImageVertex(6f, 7f, 1f, 0f),
                    GPUPreparedImageVertex(6f, 8f, 1f, 1f),
                    GPUPreparedImageVertex(3f, 8f, 0f, 1f),
                ),
                listOf(0, 1, 2, 0, 2, 3),
            ),
        )
        val linear = binding.copy(sampler = binding.sampler.copy(magFilter = "linear", minFilter = "linear"))

        listOf(malformedCorners, linear).forEach { forged ->
            val factory = RecordingFactory()
            val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
                GPUPreparedImageNativeResourcePreflighter.preflight(
                    fixture.request.copy(
                        resourcePlan = fixture.plan.copy(bindingRequests = listOf(forged)),
                    ),
                ),
            )

            assertEquals(GPUPreparedImageRefusalCodes.RECT_GEOMETRY, refused.reasonCode)
            assertEquals("preflight", refused.facts["boundary"])
            assertEquals(0, factory.createCalls)
        }
    }

    @Test
    fun `bounded route capability incoherence refuses before native handles`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput(
                    packetId = "packet.bounded.incoherent",
                    sampling = GPUPreparedImageSampling.Nearest,
                    routeCapability = GPUPreparedImageRouteCapability.BoundedNearest1To1,
                    boundedGeometry = boundedGeometry(),
                ),
            ),
        )
        val incoherent = fixture.plan.bindingRequests.single().copy(
            sampler = fixture.plan.bindingRequests.single().sampler.copy(
                preparedImageRouteCapability = GPUPreparedImageRouteCapability.GenericNative,
            ),
        )

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
            GPUPreparedImageNativeResourcePreflighter.preflight(
                fixture.request.copy(resourcePlan = fixture.plan.copy(bindingRequests = listOf(incoherent))),
            ),
        )

        assertEquals(GPUPreparedImageRefusalCodes.RECT_GEOMETRY, refused.reasonCode)
        assertEquals("preflight", refused.facts["boundary"])
    }

    @Test
    fun `generic native linear sampler plan reaches native preflight`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.linear", GPUPreparedImageSampling.Linear)))

        val sealed = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals("linear", sealed.request.resourcePlan.bindingRequests.single().sampler.magFilter)
        assertEquals("linear", sealed.request.resourcePlan.bindingRequests.single().sampler.minFilter)
    }

    @Test
    fun `nearest sampler shares binding while uniforms keep distinct offsets`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput("packet.nearest.a", GPUPreparedImageSampling.Nearest),
                GPUImageBindingInput("packet.nearest.b", GPUPreparedImageSampling.Nearest),
                GPUImageBindingInput("packet.nearest.c", GPUPreparedImageSampling.Nearest),
            ),
        )
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals(1, seal.uploadKeys.toSet().size)
        assertEquals(1, seal.samplerKeysByPacketId.values.toSet().size)
        assertEquals(1, seal.bindingKeysByPacketId.values.toSet().size)
        assertEquals(
            seal.bindingKeysByPacketId.getValue("packet.nearest.a"),
            seal.bindingKeysByPacketId.getValue("packet.nearest.b"),
        )
        assertEquals(
            seal.bindingKeysByPacketId.getValue("packet.nearest.a"),
            seal.bindingKeysByPacketId.getValue("packet.nearest.c"),
        )
        assertTrue(seal.uploadKeys.all { it.deviceGeneration == 7L })
        assertTrue(seal.samplerKeysByPacketId.values.all { it.deviceGeneration == 7L })

        val factory = RecordingFactory()
        val resources = seal.materialize(factory, factory.bindGroupLayout)
        assertEquals(seal.uploadKeys.single(), resources.uploadKey(fixture.artifactKey))
        assertSame(resources.texture(fixture.artifactKey), resources.texture(fixture.artifactKey))
        assertSame(resources.binding("packet.nearest.a"), resources.binding("packet.nearest.b"))
        assertSame(resources.binding("packet.nearest.a"), resources.binding("packet.nearest.c"))
        assertEquals(
            listOf(0L, 256L, 512L),
            listOf(
                resources.dynamicUniformOffset("packet.nearest.a"),
                resources.dynamicUniformOffset("packet.nearest.b"),
                resources.dynamicUniformOffset("packet.nearest.c"),
            ),
        )
        assertEquals(1, factory.textureCreates)
        assertEquals(1, factory.textureViewCreates)
        assertEquals(1, factory.samplerCreates)
        assertEquals(1, factory.uniformBufferCreates)
        assertEquals(1, factory.bindGroupCreates)
        resources.close()
    }

    @Test
    fun `same sampler shares binding key while uniforms keep distinct aligned offsets`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput("packet.tint.a", GPUPreparedImageSampling.Nearest),
                GPUImageBindingInput("packet.tint.b", GPUPreparedImageSampling.Nearest),
            ),
        )
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals(1, seal.bindingKeysByPacketId.values.toSet().size)
        val factory = RecordingFactory()
        val resources = seal.materialize(factory, factory.bindGroupLayout)
        assertSame(resources.binding("packet.tint.a"), resources.binding("packet.tint.b"))
        assertEquals(listOf(0L, 256L), listOf(
            resources.dynamicUniformOffset("packet.tint.a"),
            resources.dynamicUniformOffset("packet.tint.b"),
        ))
        assertEquals(1, factory.bindGroupCreates)
        resources.close()
    }

    @Test
    fun `device generation changes upload sampler and binding keys`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val first = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )
        val next = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(
                fixture.request.copy(expectedDeviceGeneration = 8, actualDeviceGeneration = 8),
            ),
        )

        assertNotEquals(first.uploadKeys.single(), next.uploadKeys.single())
        assertNotEquals(
            first.samplerKeysByPacketId.getValue("packet.image"),
            next.samplerKeysByPacketId.getValue("packet.image"),
        )
        assertNotEquals(
            first.bindingKeysByPacketId.getValue("packet.image"),
            next.bindingKeysByPacketId.getValue("packet.image"),
        )
    }

    @Test
    fun `all attachment usage limit owner and generation mismatches refuse before factory`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val badUsage = fixture.plan.copy(
            textureDescriptor = fixture.plan.textureDescriptor.copy(usageLabels = setOf("copy_dst")),
        )
        val cases = listOf(
            "unsupported.prepared_image.active_attachment" to fixture.request.copy(
                activeAttachment = fixture.plan.frameTextureRef,
            ),
            "unsupported.prepared_image.texture_usage" to fixture.request.copy(resourcePlan = badUsage),
            GPUPreparedImageRefusalCodes.TEXTURE_LIMIT to fixture.request.copy(
                capabilities = capabilities(maxTextureDimension2D = 1),
            ),
            "unsupported.prepared_image.owner_mismatch" to fixture.request.copy(actualOwner = "foreign-owner"),
            GPUPreparedImageRefusalCodes.NATIVE_GENERATION to
                fixture.request.copy(actualDeviceGeneration = 8),
            GPUPreparedImageRefusalCodes.NATIVE_GENERATION to
                fixture.request.copy(actualResourceGeneration = 4),
        )
        cases.forEach { (reason, request) ->
            val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
                GPUPreparedImageNativeResourcePreflighter.preflight(request),
            )
            assertEquals(reason, refused.reasonCode)
            if (reason in setOf(
                    GPUPreparedImageRefusalCodes.NATIVE_GENERATION,
                    GPUPreparedImageRefusalCodes.TEXTURE_LIMIT,
                )
            ) {
                assertEquals("preflight", refused.facts["boundary"])
            }
        }
    }

    @Test
    fun `foreign binding layout refuses before native handles`() {
        val fixture = fixture(
            bindings = listOf(
                GPUImageBindingInput(
                    "packet.foreign-layout",
                    GPUPreparedImageSampling.Nearest,
                ),
            ),
            bindingLayoutHash = "layout.image",
        )
        val factory = RecordingFactory()
        val result = GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request)
        if (result is GPUPreparedImageNativePreflightResult.Sealed) {
            result.materialize(factory, factory.bindGroupLayout).close()
        }

        assertEquals(0, factory.createCalls)
        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.reasonCode)
        assertEquals("preflight", refused.facts["boundary"])
    }

    @Test
    fun `missing binding preserves canonical refusal through preflight`() {
        val fixture = fixture(
            listOf(GPUImageBindingInput("packet.missing", GPUPreparedImageSampling.Nearest)),
        )

        val result = GPUPreparedImageNativeResourcePreflighter.preflight(
            fixture.request.copy(
                resourcePlan = fixture.plan.copy(bindingRequests = emptyList()),
            ),
        )

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.reasonCode)
        assertEquals("preflight", refused.facts["boundary"])
        assertTrue(!refused.reasonCode.startsWith("unsupported.surface.prepared.image-source."))
    }

    @Test
    fun `incompatible copy row alignment refuses before native handles`() {
        val fixture = fixture(
            listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)),
        )
        val factory = RecordingFactory()

        val result = GPUPreparedImageNativeResourcePreflighter.preflight(
            fixture.request.copy(
                capabilities = capabilities(copyBytesPerRowAlignment = 512),
            ),
        )
        if (result is GPUPreparedImageNativePreflightResult.Sealed) {
            result.materialize(factory, factory.bindGroupLayout).close()
        }

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.PIXEL_ROW_STRIDE, refused.reasonCode)
        assertEquals("preflight", refused.facts["boundary"])
        assertEquals(0, factory.createCalls)
    }

    @Test
    fun `seal refuses incoherent staging uniform limits and upload layout`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val staging = fixture.plan.preparationRequests.single { it.resource == fixture.plan.stagingRef }
        val uniform = fixture.plan.preparationRequests.single { it.resource == fixture.plan.uniformRef }
        fun planWithPreparation(replacement: GPUResourcePreparationRequest) = fixture.plan.copy(
            preparationRequests = fixture.plan.preparationRequests.map { request ->
                if (request.resource == replacement.resource) replacement else request
            },
        )
        val oversizedUniformBytes = (1L shl 30) + 1L
        val cases = listOf(
            "unsupported.prepared_image.staging_preparation" to planWithPreparation(
                staging.rebuilt(usages = setOf(GPUFrameResourceUsage.CopyDestination)),
            ),
            "unsupported.prepared_image.staging_preparation" to planWithPreparation(
                staging.rebuilt(
                    descriptor = GPUFrameBufferDescriptor(
                        byteSize = staging.byteSize - 1L,
                        alignmentBytes = 4,
                    ),
                    byteSize = staging.byteSize - 1L,
                ),
            ),
            "unsupported.prepared_image.uniform_preparation" to planWithPreparation(
                uniform.rebuilt(usages = setOf(GPUFrameResourceUsage.CopyDestination)),
            ),
            GPUPreparedImageRefusalCodes.UPLOAD_BUDGET_EXCEEDED to planWithPreparation(
                uniform.rebuilt(
                    descriptor = GPUFrameBufferDescriptor(
                        byteSize = oversizedUniformBytes,
                        alignmentBytes = 256,
                    ),
                    byteSize = oversizedUniformBytes,
                ),
            ),
            "unsupported.prepared_image.upload_layout" to fixture.plan.copy(
                uploadTaskLayout = fixture.plan.uploadTaskLayout.copy(
                    bytesPerRow = fixture.plan.uploadTaskLayout.bytesPerRow + 256L,
                ),
            ),
            "unsupported.prepared_image.upload_layout" to fixture.plan.copy(
                uploadTaskLayout = GPUUploadLayout(
                    sourceOffsetBytes = 1,
                    bytesPerRow = fixture.plan.uploadTaskLayout.bytesPerRow,
                    rowsPerImage = fixture.plan.uploadTaskLayout.rowsPerImage,
                    byteSize = fixture.plan.uploadTaskLayout.byteSize,
                ),
            ),
        )

        cases.forEach { (reason, plan) ->
            val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
                GPUPreparedImageNativeResourcePreflighter.preflight(
                    fixture.request.copy(resourcePlan = plan),
                ),
            )
            assertEquals(reason, refused.reasonCode)
            if (reason in GPUPreparedImageRefusalCodes.ALL) {
                assertEquals("preflight", refused.facts["boundary"])
            }
        }
    }

    @Test
    fun `seal revalidates zero padding after adversarial payload corruption`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val uploadBytesField = fixture.plan.uploadLayout.javaClass.getDeclaredField("uploadBytes")
        uploadBytesField.isAccessible = true
        val privateUploadBytes = uploadBytesField.get(fixture.plan.uploadLayout) as ByteArray
        privateUploadBytes[fixture.plan.uploadLayout.logicalBytesPerRow.toInt()] = 1

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals("unsupported.prepared_image.upload_layout", refused.reasonCode)
    }

    @Test
    fun `seal refuses bindings whose texture views disagree`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput("packet.a", GPUPreparedImageSampling.Nearest),
                GPUImageBindingInput("packet.b", GPUPreparedImageSampling.Nearest),
            ),
        )
        val changedBindings = fixture.plan.bindingRequests.mapIndexed { index, binding ->
            if (index == 0) binding else binding.copy(
                view = binding.view.copy(mipRange = 1..1),
            )
        }

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
            GPUPreparedImageNativeResourcePreflighter.preflight(
                fixture.request.copy(
                    resourcePlan = fixture.plan.copy(bindingRequests = changedBindings),
                ),
            ),
        )

        assertEquals("unsupported.prepared_image.view_identity", refused.reasonCode)
    }

    @Test
    fun `seal refuses a negative aligned dynamic uniform offset`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput("packet.a", GPUPreparedImageSampling.Nearest),
                GPUImageBindingInput("packet.b", GPUPreparedImageSampling.Nearest),
            ),
        )
        val changedBindings = fixture.plan.bindingRequests.mapIndexed { index, binding ->
            if (index != 0) binding else binding.copy(
                uniformAllocation = binding.uniformAllocation.copy(offset = -256L),
            )
        }

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
            GPUPreparedImageNativeResourcePreflighter.preflight(
                fixture.request.copy(
                    resourcePlan = fixture.plan.copy(bindingRequests = changedBindings),
                ),
            ),
        )

        assertEquals("unsupported.prepared_image.uniform_allocation", refused.reasonCode)
    }

    @Test
    fun `seal converts dynamic uniform range overflow into stable refusal`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val overflowBinding = fixture.plan.bindingRequests.single().let { binding ->
            binding.copy(
                uniformAllocation = binding.uniformAllocation.copy(
                    offset = Long.MAX_VALUE,
                    size = 1L,
                ),
            )
        }

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
            GPUPreparedImageNativeResourcePreflighter.preflight(
                fixture.request.copy(
                    resourcePlan = fixture.plan.copy(bindingRequests = listOf(overflowBinding)),
                ),
            ),
        )

        assertEquals("unsupported.prepared_image.uniform_allocation", refused.reasonCode)
    }

    @Test
    fun `seal refuses a common view whose texture descriptor hash is foreign`() {
        val fixture = fixture(
            listOf(
                GPUImageBindingInput("packet.a", GPUPreparedImageSampling.Nearest),
                GPUImageBindingInput("packet.b", GPUPreparedImageSampling.Nearest),
            ),
        )
        val changedBindings = fixture.plan.bindingRequests.map { binding ->
            binding.copy(
                view = binding.view.copy(textureDescriptorHash = "foreign-texture-descriptor"),
            )
        }

        val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
            GPUPreparedImageNativeResourcePreflighter.preflight(
                fixture.request.copy(
                    resourcePlan = fixture.plan.copy(bindingRequests = changedBindings),
                ),
            ),
        )

        assertEquals("unsupported.prepared_image.view_identity", refused.reasonCode)
    }

    @Test
    fun `partial factory failure closes every created handle once in reverse order`() {
        val fixture = fixture(
            listOf(GPUImageBindingInput("packet.nearest", GPUPreparedImageSampling.Nearest)),
        )
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )
        val events = mutableListOf<String>()
        val factory = RecordingFactory(events, failOnFirstBindGroup = true)

        assertFailsWith<IllegalStateException> {
            seal.materialize(factory, factory.bindGroupLayout)
        }
        assertEquals(
            factory.createdLabels.asReversed(),
            events,
        )
        assertEquals(events.size, events.toSet().size)
    }

    @Test
    fun `close failures aggregate in reverse order and a second close never retries handles`() {
        val fixture = fixture(listOf(GPUImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )
        val events = mutableListOf<String>()
        val factory = RecordingFactory(
            closeEvents = events,
            failCloseLabels = setOf("bind-group.nearest", "sampler.nearest"),
        )
        val resources = seal.materialize(factory, factory.bindGroupLayout)

        val failure = assertFailsWith<IllegalStateException> { resources.close() }
        val firstCloseEvents = factory.createdLabels.asReversed()
        assertEquals(firstCloseEvents, events)
        assertEquals("close failure bind-group.nearest", failure.message)
        assertEquals(
            listOf("close failure sampler.nearest"),
            failure.suppressed.map { suppressed -> suppressed.message },
        )

        resources.close()
        assertEquals(firstCloseEvents, events)
        assertFailsWith<IllegalStateException> { resources.texture(fixture.artifactKey) }
    }

    private fun fixture(
        bindings: List<GPUImageBindingInput>,
        bindingLayoutHash: String =
            "prepared-image.group0.dynamic-uniform-texture-sampler.v1",
    ): Fixture {
        val artifact = (GPUPreparedImageArtifactFactory.prepare(
            GPUPreparedImageSourceInput(
                sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                sourceId = "native-resources",
                width = 2,
                height = 2,
                sourceFormat = GPUPreparedImageSourceFormat.Rgba8,
                alphaType = AlphaType.PREMUL,
                sourceRowBytes = 8,
                profile = GPUPreparedImageProfile.Srgb,
                orientation = GPUPreparedImageOrientation.AppliedIdentity,
                provenance = GPUPreparedImageProvenance.CallerPixels,
                sourceGeneration = 3,
                pixelBytes = ByteArray(16) { it.toByte() },
            ),
        ) as GPUPreparedImageArtifactResult.Ready).artifact
        val caps = capabilities()
        val plan = buildImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = bindings,
            bindingLayoutHash = bindingLayoutHash,
            capabilities = caps,
            frameIdentity = "frame.native-resources",
        )
        return Fixture(
            artifact.key,
            plan,
            GPUPreparedImageNativePreflightRequest(
                resourcePlan = plan,
                artifactKey = artifact.key,
                capabilities = caps,
                expectedDeviceGeneration = 7,
                actualDeviceGeneration = 7,
                expectedResourceGeneration = 3,
                actualResourceGeneration = 3,
                expectedOwner = "prepared-image-frame",
                actualOwner = "prepared-image-frame",
            ),
        )
    }

    private fun capabilities(
        maxTextureDimension2D: Long = 8192,
        copyBytesPerRowAlignment: Long = 256,
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "prepared-image-native-resources",
        limits = GPULimits(
            maxTextureDimension2D = maxTextureDimension2D,
            copyBytesPerRowAlignment = copyBytesPerRowAlignment,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private fun boundedGeometry(): GPUPreparedImageGeometry = GPUPreparedImageGeometry(
        GPUPreparedImageGeometryClass.Rect,
        listOf(
            GPUPreparedImageVertex(4f, 6f, 0f, 0f),
            GPUPreparedImageVertex(6f, 6f, 1f, 0f),
            GPUPreparedImageVertex(6f, 8f, 1f, 1f),
            GPUPreparedImageVertex(4f, 8f, 0f, 1f),
        ),
        listOf(0, 1, 2, 0, 2, 3),
    )

    private data class Fixture(
        val artifactKey: GPUImageUploadArtifactKey,
        val plan: GPUImageFrameResourcePlan,
        val request: GPUPreparedImageNativePreflightRequest,
    )

    private class RecordingFactory(
        private val closeEvents: MutableList<String> = mutableListOf(),
        private val failOnSecondBindGroup: Boolean = false,
        private val failOnFirstBindGroup: Boolean = false,
        private val failCloseLabels: Set<String> = emptySet(),
    ) : GPUPreparedImageNativeHandleFactory {
        val bindGroupLayout: GPUBindGroupLayout = Proxy.newProxyInstance(
            GPUBindGroupLayout::class.java.classLoader,
            arrayOf(GPUBindGroupLayout::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "close", "setLabel" -> Unit
                "getLabel", "toString" -> "prepared-image-test-layout"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.singleOrNull()
                else -> null
            }
        } as GPUBindGroupLayout
        var createCalls = 0
        var textureCreates = 0
        var textureViewCreates = 0
        var samplerCreates = 0
        var uniformBufferCreates = 0
        var bindGroupCreates = 0
        val createdLabels = mutableListOf<String>()

        override fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture {
            textureCreates += 1
            return handle("texture")
        }

        override fun createTextureView(
            texture: GPUTexture,
            request: GPUImageFrameResourcePlan,
        ): GPUTextureView {
            textureViewCreates += 1
            return handle("texture-view")
        }

        override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler {
            samplerCreates += 1
            return handle("sampler.${descriptor.magFilter}")
        }

        override fun createUniformBuffer(size: Long): GPUBuffer {
            uniformBufferCreates += 1
            return handle("uniform-buffer")
        }

        override fun createBindGroup(
            bindGroupLayout: GPUBindGroupLayout,
            request: GPUImageBindingRequest,
            uniformBuffer: GPUBuffer,
            textureView: GPUTextureView,
            sampler: GPUSampler,
        ): GPUBindGroup {
            bindGroupCreates += 1
            if ((failOnFirstBindGroup && bindGroupCreates == 1) ||
                (failOnSecondBindGroup && bindGroupCreates == 2)
            ) error("bind-group failure")
            return handle("bind-group.${request.sampler.magFilter}")
        }

        private inline fun <reified T> handle(label: String): T {
            createCalls += 1
            createdLabels += label
            return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
                when (method.name) {
                    "close" -> {
                        closeEvents += label
                        if (label in failCloseLabels) error("close failure $label")
                    }
                    "toString" -> label
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.singleOrNull()
                    else -> null
                }
            } as T
        }
    }
}

private fun GPUResourcePreparationRequest.rebuilt(
    descriptor: GPUFrameResourceDescriptor = this.descriptor,
    role: GPUFrameResourceRole = this.role,
    usages: Set<GPUFrameResourceUsage> = this.usages,
    byteSize: Long = this.byteSize,
): GPUResourcePreparationRequest = GPUResourcePreparationRequest(
    resource = resource,
    descriptor = descriptor,
    role = role,
    usages = usages,
    lifetime = lifetime,
    byteSize = byteSize,
    diagnosticLabel = diagnosticLabel,
)
