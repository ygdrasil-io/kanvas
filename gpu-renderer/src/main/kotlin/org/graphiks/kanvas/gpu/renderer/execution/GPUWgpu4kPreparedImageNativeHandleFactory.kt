package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES

/**
 * Frame-local prepared-image handle factory. It does not retain textures, views, samplers,
 * buffers, or bind groups; their owner remains the materializing frame.
 */
internal class GPUWgpu4kPreparedImageNativeHandleFactory(
    private val device: GPUDevice,
    private val counterRecorder: GPUPreparedImageNativeCounterRecorder =
        GPUPreparedImageNativeCounterRecorder(),
) : GPUPreparedImageNativeHandleFactory {
    override fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture {
        val descriptor = request.textureDescriptor
        require(descriptor.sampleCount == 1) {
            "Prepared-image textures require sampleCount=1"
        }
        return device.createTexture(
            TextureDescriptor(
                size = Extent3D(
                    descriptor.width.toUInt(),
                    descriptor.height.toUInt(),
                    1u,
                ),
                format = descriptor.preparedImageNativeFormat(),
                usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                mipLevelCount = 1u,
                sampleCount = 1u,
                label = "Kanvas.frame.preparedImage.texture",
            ),
        ).also { counterRecorder.recordFrameTextureCreation() }
    }

    override fun createTextureView(
        texture: GPUTexture,
        request: GPUImageFrameResourcePlan,
    ): GPUTextureView {
        val views = request.bindingRequests.map(GPUImageBindingRequest::view).distinct()
        require(views.size == 1) {
            "Prepared-image texture bindings require one exact view descriptor"
        }
        val view = views.single()
        require(
            view.viewDimension == "2d" &&
                view.mipRange == 0..0 &&
                view.arrayLayerRange == 0..0
        ) {
            "Prepared-image texture view must be 2D mip/layer 0"
        }
        return texture.createView(
            TextureViewDescriptor(
                format = request.textureDescriptor.preparedImageNativeFormat(),
                dimension = GPUTextureViewDimension.TwoD,
                usage = GPUTextureUsage.TextureBinding,
                aspect = GPUTextureAspect.All,
                baseMipLevel = 0u,
                mipLevelCount = 1u,
                baseArrayLayer = 0u,
                arrayLayerCount = 1u,
                label = "Kanvas.frame.preparedImage.textureView",
            ),
        ).also { counterRecorder.recordFrameTextureViewCreation() }
    }

    override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler {
        require(
            descriptor.addressModeU == "clamp-to-edge" &&
                descriptor.addressModeV == "clamp-to-edge" &&
                descriptor.magFilter == "nearest" &&
                descriptor.minFilter == "nearest" &&
                descriptor.mipmapFilter == "none" &&
                descriptor.lodMinClamp == "0" &&
                descriptor.lodMaxClamp == "0" &&
                descriptor.compareMode == "none" &&
                descriptor.maxAnisotropy == 1 &&
                descriptor.capabilityRequirements.isEmpty()
        ) {
            "Unsupported prepared-image sampler descriptor"
        }
        return device.createSampler(
            SamplerDescriptor(
                addressModeU = GPUAddressMode.ClampToEdge,
                addressModeV = GPUAddressMode.ClampToEdge,
                addressModeW = GPUAddressMode.ClampToEdge,
                magFilter = descriptor.magFilter.preparedImageNativeFilter("mag"),
                minFilter = descriptor.minFilter.preparedImageNativeFilter("min"),
                mipmapFilter = GPUMipmapFilterMode.Nearest,
                lodMinClamp = 0f,
                lodMaxClamp = 0f,
                compare = null,
                maxAnisotropy = 1u.toUShort(),
                label = "Kanvas.frame.preparedImage.sampler",
            ),
        ).also { counterRecorder.recordFrameSamplerCreation() }
    }

    override fun createUniformBuffer(size: Long): GPUBuffer {
        require(
            size >= GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES &&
                size % 4L == 0L
        ) {
            "Prepared-image uniform buffer size must contain one aligned 112-byte allocation"
        }
        return device.createBuffer(
            BufferDescriptor(
                size = size.toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                mappedAtCreation = false,
                label = "Kanvas.frame.preparedImage.uniforms",
            ),
        ).also { counterRecorder.recordFrameUniformBufferCreation() }
    }

    override fun createBindGroup(
        bindGroupLayout: GPUBindGroupLayout,
        request: GPUImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup {
        require(
            request.bindingLayoutHash == GPUPreparedImageBindingLayoutTopology.IDENTITY &&
                request.uniformAllocation.size ==
                GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES
        ) {
            "Prepared-image bind group requires the canonical layout and 112-byte uniform range"
        }
        return device.createBindGroup(
            BindGroupDescriptor(
                label = "Kanvas.frame.preparedImage.bindGroup",
                layout = bindGroupLayout,
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = BufferBinding(
                            buffer = uniformBuffer,
                            offset = 0uL,
                            size = GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES.toULong(),
                        ),
                    ),
                    BindGroupEntry(binding = 1u, resource = textureView),
                    BindGroupEntry(binding = 2u, resource = sampler),
                ),
            ),
        ).also { counterRecorder.recordFrameBindGroupCreation() }
    }
}

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor
    .preparedImageNativeFormat(): GPUTextureFormat =
    when (format) {
        "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
        "RGBA8Unorm" -> GPUTextureFormat.RGBA8Unorm
        else -> throw IllegalArgumentException(
            "Unsupported prepared-image texture format $format",
        )
    }

private fun String.preparedImageNativeFilter(axis: String): GPUFilterMode =
    when (this) {
        "nearest" -> GPUFilterMode.Nearest
        else -> throw IllegalArgumentException(
            "Unsupported prepared-image $axis filter $this",
        )
    }
