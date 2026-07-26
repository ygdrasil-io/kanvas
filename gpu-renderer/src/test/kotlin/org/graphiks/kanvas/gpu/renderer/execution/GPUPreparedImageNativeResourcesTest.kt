package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
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
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.buildPreparedImageFrameResourcePlanFromBindings

class GPUPreparedImageNativeResourcesTest {
    @Test
    fun `native keys split upload sampler binding and uniform offsets`() {
        val fixture = fixture(
            listOf(
                GPUPreparedImageBindingInput("packet.nearest.a", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.nearest.b", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.linear", GPUPreparedImageSampling.Linear),
            ),
        )
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals(1, seal.uploadKeys.toSet().size)
        assertEquals(2, seal.samplerKeysByPacketId.values.toSet().size)
        assertEquals(2, seal.bindingKeysByPacketId.values.toSet().size)
        assertEquals(
            seal.bindingKeysByPacketId.getValue("packet.nearest.a"),
            seal.bindingKeysByPacketId.getValue("packet.nearest.b"),
        )
        assertNotEquals(
            seal.bindingKeysByPacketId.getValue("packet.nearest.a"),
            seal.bindingKeysByPacketId.getValue("packet.linear"),
        )
        assertTrue(seal.uploadKeys.all { it.deviceGeneration == 7L })
        assertTrue(seal.samplerKeysByPacketId.values.all { it.deviceGeneration == 7L })

        val factory = RecordingFactory()
        val resources = seal.materialize(factory)
        assertEquals(seal.uploadKeys.single(), resources.uploadKey(fixture.artifactKey))
        assertSame(resources.texture(fixture.artifactKey), resources.texture(fixture.artifactKey))
        assertSame(resources.binding("packet.nearest.a"), resources.binding("packet.nearest.b"))
        assertNotSame(resources.binding("packet.nearest.a"), resources.binding("packet.linear"))
        assertEquals(
            listOf(0L, 256L, 512L),
            listOf(
                resources.dynamicUniformOffset("packet.nearest.a"),
                resources.dynamicUniformOffset("packet.nearest.b"),
                resources.dynamicUniformOffset("packet.linear"),
            ),
        )
        assertEquals(1, factory.textureCreates)
        assertEquals(1, factory.textureViewCreates)
        assertEquals(2, factory.samplerCreates)
        assertEquals(1, factory.uniformBufferCreates)
        assertEquals(2, factory.bindGroupCreates)
        resources.close()
    }

    @Test
    fun `same sampler shares binding key while uniforms keep distinct aligned offsets`() {
        val fixture = fixture(
            listOf(
                GPUPreparedImageBindingInput("packet.tint.a", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.tint.b", GPUPreparedImageSampling.Nearest),
            ),
        )
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )

        assertEquals(1, seal.bindingKeysByPacketId.values.toSet().size)
        val factory = RecordingFactory()
        val resources = seal.materialize(factory)
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
        val fixture = fixture(listOf(GPUPreparedImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
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
        val fixture = fixture(listOf(GPUPreparedImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val badUsage = fixture.plan.copy(
            textureDescriptor = fixture.plan.textureDescriptor.copy(usageLabels = setOf("copy_dst")),
        )
        val cases = listOf(
            "unsupported.prepared_image.active_attachment" to fixture.request.copy(
                activeAttachment = fixture.plan.frameTextureRef,
            ),
            "unsupported.prepared_image.texture_usage" to fixture.request.copy(resourcePlan = badUsage),
            "unsupported.prepared_image.device_limit" to fixture.request.copy(
                capabilities = capabilities(maxTextureDimension2D = 1),
            ),
            "unsupported.prepared_image.owner_mismatch" to fixture.request.copy(actualOwner = "foreign-owner"),
            "unsupported.prepared_image.device_generation" to fixture.request.copy(actualDeviceGeneration = 8),
            "unsupported.prepared_image.resource_generation" to fixture.request.copy(actualResourceGeneration = 4),
        )
        val factory = RecordingFactory()

        cases.forEach { (reason, request) ->
            val refused = assertIs<GPUPreparedImageNativePreflightResult.Refused>(
                GPUPreparedImageNativeResourcePreflighter.preflight(request),
            )
            assertEquals(reason, refused.reasonCode)
        }
        assertEquals(0, factory.createCalls)
    }

    @Test
    fun `seal refuses incoherent staging uniform limits and upload layout`() {
        val fixture = fixture(listOf(GPUPreparedImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
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
            "unsupported.prepared_image.device_limit" to planWithPreparation(
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
        }
    }

    @Test
    fun `seal revalidates zero padding after adversarial payload corruption`() {
        val fixture = fixture(listOf(GPUPreparedImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
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
                GPUPreparedImageBindingInput("packet.a", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.b", GPUPreparedImageSampling.Linear),
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
                GPUPreparedImageBindingInput("packet.a", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.b", GPUPreparedImageSampling.Linear),
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
        val fixture = fixture(listOf(GPUPreparedImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
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
                GPUPreparedImageBindingInput("packet.a", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.b", GPUPreparedImageSampling.Linear),
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
            listOf(
                GPUPreparedImageBindingInput("packet.nearest", GPUPreparedImageSampling.Nearest),
                GPUPreparedImageBindingInput("packet.linear", GPUPreparedImageSampling.Linear),
            ),
        )
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )
        val events = mutableListOf<String>()
        val factory = RecordingFactory(events, failOnSecondBindGroup = true)

        assertFailsWith<IllegalStateException> { seal.materialize(factory) }
        assertEquals(
            factory.createdLabels.asReversed(),
            events,
        )
        assertEquals(events.size, events.toSet().size)
    }

    @Test
    fun `close failures aggregate in reverse order and a second close never retries handles`() {
        val fixture = fixture(listOf(GPUPreparedImageBindingInput("packet.image", GPUPreparedImageSampling.Nearest)))
        val seal = assertIs<GPUPreparedImageNativePreflightResult.Sealed>(
            GPUPreparedImageNativeResourcePreflighter.preflight(fixture.request),
        )
        val events = mutableListOf<String>()
        val factory = RecordingFactory(
            closeEvents = events,
            failCloseLabels = setOf("bind-group.nearest", "sampler.nearest"),
        )
        val resources = seal.materialize(factory)

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

    private fun fixture(bindings: List<GPUPreparedImageBindingInput>): Fixture {
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
        val plan = buildPreparedImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = bindings,
            bindingLayoutHash = "layout.image",
            capabilities = caps,
            frameIdentity = "frame.native-resources",
            uploadTaskId = GPUTaskID("task.upload.image"),
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

    private fun capabilities(maxTextureDimension2D: Long = 8192) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "prepared-image-native-resources",
        limits = GPULimits(
            maxTextureDimension2D = maxTextureDimension2D,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private data class Fixture(
        val artifactKey: GPUImageUploadArtifactKey,
        val plan: GPUPreparedImageFrameResourcePlan,
        val request: GPUPreparedImageNativePreflightRequest,
    )

    private class RecordingFactory(
        private val closeEvents: MutableList<String> = mutableListOf(),
        private val failOnSecondBindGroup: Boolean = false,
        private val failCloseLabels: Set<String> = emptySet(),
    ) : GPUPreparedImageNativeHandleFactory {
        var createCalls = 0
        var textureCreates = 0
        var textureViewCreates = 0
        var samplerCreates = 0
        var uniformBufferCreates = 0
        var bindGroupCreates = 0
        val createdLabels = mutableListOf<String>()

        override fun createTexture(request: GPUPreparedImageFrameResourcePlan): GPUTexture {
            textureCreates += 1
            return handle("texture")
        }

        override fun createTextureView(
            texture: GPUTexture,
            request: GPUPreparedImageFrameResourcePlan,
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
            request: GPUPreparedImageBindingRequest,
            uniformBuffer: GPUBuffer,
            textureView: GPUTextureView,
            sampler: GPUSampler,
        ): GPUBindGroup {
            bindGroupCreates += 1
            if (failOnSecondBindGroup && bindGroupCreates == 2) error("bind-group failure")
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
