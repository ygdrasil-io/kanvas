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
    fun `texture upload exposes data and texture keys while retaining only the native texture operand`() {
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
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
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
        assertEquals(4, upload.sourceStepIndex)
    }

    @Test
    fun `materialization result is operand-only and has no draft inheritance`() {
        val readyType = GPUPreparedRenderRunMaterialization.Ready::class
        assertEquals(
            setOf("ownedResources", "scopeOperands"),
            readyType.java.declaredMethods.map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
                .intersect(setOf("ownedResources", "scopeOperands", "draft")),
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
                GPUPreparedImageRenderRunPlan(
                    sourceScopeIndices = listOf(1, 2, 3),
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
        assertTrue(upload.sourceStepIndex < renders.first().sourceStepIndex)
        assertEquals(listOf(0L, 256L), renders.map { it.dynamicUniformOffset })
        assertNotEquals(renders[0].uniformBytes().toList(), renders[1].uniformBytes().toList())
        assertSame(renders[0].pipeline.pipeline, renders[1].pipeline.pipeline)
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

private fun preparedImageArtifact() =
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
            pixelBytes = ByteArray(16) { (it + 1).toByte() },
        ),
    ) as GPUPreparedImageArtifactResult.Ready).artifact

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

private class RecordingPreparedImageDevice {
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

private class RecordingPreparedImageHandleFactory : GPUPreparedImageNativeHandleFactory {
    val closeCounts = linkedMapOf<String, Int>()
    val samplerFilters = mutableListOf<String>()
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

    override fun createUniformBuffer(size: Long): GPUBuffer = handle("uniform")

    override fun createBindGroup(
        request: GPUPreparedImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup = handle("bind.${request.packetId}")

    private inline fun <reified T> handle(prefix: String): T {
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
