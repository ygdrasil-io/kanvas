package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureAspect
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.buildImageFrameResourcePlanFromBindings

class GPUWgpu4kPreparedImageNativeHandleFactoryTest {
    @Test
    fun `failed frame local sampler creation does not increment prepared image counters`() {
        val native = CapturingPreparedImageNativeDevice(failCreateFor = "createSampler")
        val counters = GPUPreparedImageNativeCounterRecorder()
        val factory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device, counters)
        val binding = preparedImageResourcePlan().bindingRequests.single()

        assertFailsWith<IllegalStateException> {
            factory.createSampler(binding.sampler)
        }

        assertEquals(0L, counters.snapshot().frameSamplerCreations)
    }

    @Test
    fun `creates the exact prepared color image native descriptors and cached layout binding`() {
        val native = CapturingPreparedImageNativeDevice()
        val factory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device)
        val request = preparedImageResourcePlan()
        val binding = request.bindingRequests.single()
        val layout = native.bindGroupLayout("cached-layout")

        val texture = factory.createTexture(request)
        val view = factory.createTextureView(texture, request)
        val sampler = factory.createSampler(binding.sampler)
        val uniformBuffer = factory.createUniformBuffer(368L)
        factory.createBindGroup(layout, binding, uniformBuffer, view, sampler)

        val textureDescriptor = native.textureDescriptors.single()
        assertEquals(3u, textureDescriptor.size.width)
        assertEquals(2u, textureDescriptor.size.height)
        assertEquals(1u, textureDescriptor.size.depthOrArrayLayers)
        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, textureDescriptor.format)
        assertEquals(
            GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
            textureDescriptor.usage,
        )
        assertEquals(1u, textureDescriptor.mipLevelCount)
        assertEquals(1u, textureDescriptor.sampleCount)

        val viewDescriptor = native.textureViewDescriptors.single()
        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, viewDescriptor.format)
        assertEquals(GPUTextureViewDimension.TwoD, viewDescriptor.dimension)
        assertEquals(GPUTextureUsage.TextureBinding, viewDescriptor.usage)
        assertEquals(GPUTextureAspect.All, viewDescriptor.aspect)
        assertEquals(0u, viewDescriptor.baseMipLevel)
        assertEquals(1u, viewDescriptor.mipLevelCount)
        assertEquals(0u, viewDescriptor.baseArrayLayer)
        assertEquals(1u, viewDescriptor.arrayLayerCount)

        val samplerDescriptor = native.samplerDescriptors.single()
        assertEquals(GPUAddressMode.ClampToEdge, samplerDescriptor.addressModeU)
        assertEquals(GPUAddressMode.ClampToEdge, samplerDescriptor.addressModeV)
        assertEquals(GPUAddressMode.ClampToEdge, samplerDescriptor.addressModeW)
        assertEquals(GPUFilterMode.Nearest, samplerDescriptor.magFilter)
        assertEquals(GPUFilterMode.Nearest, samplerDescriptor.minFilter)
        assertEquals(GPUMipmapFilterMode.Nearest, samplerDescriptor.mipmapFilter)
        assertEquals(0f, samplerDescriptor.lodMinClamp)
        assertEquals(0f, samplerDescriptor.lodMaxClamp)
        assertNull(samplerDescriptor.compare)
        assertEquals(1u.toUShort(), samplerDescriptor.maxAnisotropy)

        val bufferDescriptor = native.bufferDescriptors.single()
        assertEquals(368uL, bufferDescriptor.size)
        assertEquals(
            GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            bufferDescriptor.usage,
        )
        assertEquals(false, bufferDescriptor.mappedAtCreation)

        val bindGroupDescriptor = native.bindGroupDescriptors.single()
        assertSame(layout, bindGroupDescriptor.layout)
        assertEquals(listOf(0u, 1u, 2u), bindGroupDescriptor.entries.map { it.binding })
        val uniformBinding = bindGroupDescriptor.entries[0].resource as BufferBinding
        assertSame(uniformBuffer, uniformBinding.buffer)
        assertEquals(0uL, uniformBinding.offset)
        assertEquals(112uL, uniformBinding.size)
        assertSame(view, bindGroupDescriptor.entries[1].resource)
        assertSame(sampler, bindGroupDescriptor.entries[2].resource)
    }

    @Test
    fun `creates the exact linear sampler descriptor`() {
        val native = CapturingPreparedImageNativeDevice()
        val factory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device)
        val nearest = preparedImageResourcePlan().bindingRequests.single().sampler

        factory.createSampler(
            nearest.copy(
                magFilter = "linear",
                minFilter = "linear",
            ),
        )

        val descriptor = native.samplerDescriptors.single()
        assertEquals(GPUAddressMode.ClampToEdge, descriptor.addressModeU)
        assertEquals(GPUAddressMode.ClampToEdge, descriptor.addressModeV)
        assertEquals(GPUAddressMode.ClampToEdge, descriptor.addressModeW)
        assertEquals(GPUFilterMode.Linear, descriptor.magFilter)
        assertEquals(GPUFilterMode.Linear, descriptor.minFilter)
        assertEquals(GPUMipmapFilterMode.Nearest, descriptor.mipmapFilter)
        assertEquals(0f, descriptor.lodMinClamp)
        assertEquals(0f, descriptor.lodMaxClamp)
        assertNull(descriptor.compare)
        assertEquals(1u.toUShort(), descriptor.maxAnisotropy)
    }

    @Test
    fun `creates A8 coverage as physical RGBA8Unorm texture and view while color stays sRGB`() {
        val native = CapturingPreparedImageNativeDevice()
        val factory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device)
        val color = preparedImageResourcePlan()
        val coverageArtifact = assertIs<GPUPreparedImageArtifactResult.Ready>(
            GPUPreparedImageArtifactFactory.prepare(
                GPUPreparedImageSourceInput(
                    sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                    sourceId = "factory-test-a8",
                    width = 3,
                    height = 1,
                    sourceFormat = GPUPreparedImageSourceFormat.A8,
                    alphaType = AlphaType.PREMUL,
                    sourceRowBytes = 3,
                    profile = GPUPreparedImageProfile.Srgb,
                    orientation = GPUPreparedImageOrientation.AppliedIdentity,
                    provenance = GPUPreparedImageProvenance.CallerPixels,
                    sourceGeneration = 5,
                    pixelBytes = byteArrayOf(0, 128.toByte(), 255.toByte()),
                ),
            ),
        ).artifact
        val coverage = buildImageFrameResourcePlanFromBindings(
            artifact = coverageArtifact,
            bindingInputs = listOf(
                GPUImageBindingInput("packet.a8", GPUPreparedImageSampling.Linear),
            ),
            bindingLayoutHash = GPUPreparedImageBindingLayoutTopology.IDENTITY,
            capabilities = preparedSurfacePreflightFixture(
                PreparedSurfaceFixtureShape.ImageOnly,
            ).capabilities,
            frameIdentity = "factory-test-a8",
        )

        val colorTexture = factory.createTexture(color)
        factory.createTextureView(colorTexture, color)
        val coverageTexture = factory.createTexture(coverage)
        factory.createTextureView(coverageTexture, coverage)
        factory.createSampler(coverage.bindingRequests.single().sampler)

        assertEquals(
            listOf(
                GPUTextureFormat.RGBA8UnormSrgb,
                GPUTextureFormat.RGBA8Unorm,
            ),
            native.textureDescriptors.map(TextureDescriptor::format),
        )
        assertEquals(
            listOf(
                GPUTextureFormat.RGBA8UnormSrgb,
                GPUTextureFormat.RGBA8Unorm,
            ),
            native.textureViewDescriptors.map(TextureViewDescriptor::format),
        )
        assertTrue(coverageArtifact.alphaOnly)
        assertEquals(
            GPUColorInterpretation.LinearPremul.value,
            coverageArtifact.colorUploadInterpretation,
        )
        assertEquals("RGBA8Unorm", coverage.textureDescriptor.format)
        assertEquals("linear", coverage.bindingRequests.single().sampler.magFilter)
        assertEquals("linear", coverage.bindingRequests.single().sampler.minFilter)
        val coverageSampler = native.samplerDescriptors.single()
        assertEquals(GPUFilterMode.Linear, coverageSampler.magFilter)
        assertEquals(GPUFilterMode.Linear, coverageSampler.minFilter)
        assertEquals(GPUMipmapFilterMode.Nearest, coverageSampler.mipmapFilter)
    }

    @Test
    fun `accepts both prepared SDR texture formats and rejects every other format`() {
        val native = CapturingPreparedImageNativeDevice()
        val factory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device)
        val colorRequest = preparedImageResourcePlan()

        factory.createTexture(colorRequest)
        factory.createTexture(
            colorRequest.copy(
                textureDescriptor = colorRequest.textureDescriptor.copy(format = "RGBA8Unorm"),
            ),
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            factory.createTexture(
                colorRequest.copy(
                    textureDescriptor = colorRequest.textureDescriptor.copy(format = "BGRA8Unorm"),
                ),
            )
        }

        assertEquals(
            listOf(
                GPUTextureFormat.RGBA8UnormSrgb,
                GPUTextureFormat.RGBA8Unorm,
            ),
            native.textureDescriptors.map(TextureDescriptor::format),
        )
        assertEquals(
            "Unsupported prepared-image texture format BGRA8Unorm",
            failure.message,
        )
    }

    private fun preparedImageResourcePlan() =
        preparedSurfacePreflightFixture(PreparedSurfaceFixtureShape.ImageOnly)
            .framePlan
            .steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .mapNotNull(GPUFrameStep.UploadResourceStep::imageResourcePlan)
            .single()
}

private class CapturingPreparedImageNativeDevice(
    private val failCreateFor: String? = null,
) {
    val textureDescriptors = mutableListOf<TextureDescriptor>()
    val textureViewDescriptors = mutableListOf<TextureViewDescriptor>()
    val samplerDescriptors = mutableListOf<SamplerDescriptor>()
    val bufferDescriptors = mutableListOf<BufferDescriptor>()
    val bindGroupDescriptors = mutableListOf<BindGroupDescriptor>()

    val device = proxy<io.ygdrasil.webgpu.GPUDevice>("device") { method, args ->
        if (method == failCreateFor) error("$method creation failed")
        when (method) {
            "createTexture" -> {
                textureDescriptors += args.single() as TextureDescriptor
                texture()
            }
            "createSampler" -> {
                samplerDescriptors += args.single() as SamplerDescriptor
                handle<GPUSampler>("sampler")
            }
            "createBuffer" -> {
                bufferDescriptors += args.single() as BufferDescriptor
                handle<GPUBuffer>("buffer")
            }
            "createBindGroup" -> {
                bindGroupDescriptors += args.single() as BindGroupDescriptor
                handle<GPUBindGroup>("bind-group")
            }
            else -> error("Unexpected device call: $method")
        }
    }

    fun bindGroupLayout(label: String): GPUBindGroupLayout =
        handle(GPUBindGroupLayout::class.java, label)

    private fun <T> handle(type: Class<T>, label: String): T = proxy(type, label) { method, _ ->
        when (method) {
            "close", "setLabel" -> Unit
            "getLabel", "toString" -> label
            else -> null
        }
    }

    private inline fun <reified T> handle(label: String): T = handle(T::class.java, label)

    private fun texture(): GPUTexture = proxy("texture") { method, args ->
        when (method) {
            "createView" -> {
                textureViewDescriptors += args.single() as TextureViewDescriptor
                handle<GPUTextureView>("view")
            }
            "close", "setLabel" -> Unit
            "getLabel", "toString" -> "texture"
            else -> null
        }
    }

    private inline fun <reified T> proxy(
        label: String,
        noinline call: (String, Array<out Any?>) -> Any?,
    ): T = proxy(T::class.java, label, call)

    private fun <T> proxy(
        type: Class<T>,
        label: String,
        call: (String, Array<out Any?>) -> Any?,
    ): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            val args = arguments ?: emptyArray()
            when (method.name) {
                "equals" -> proxy === args.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> label
                else -> call(method.name, args)
            }
        },
    )
}
