package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.images.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureViewDescriptor

data class GPUPreparedImageUploadKey(
    val artifactKey: GPUImageUploadArtifactKey,
    val deviceGeneration: Long,
    val textureDescriptorHash: String,
    val viewDescriptorHash: String,
)

data class GPUPreparedImageSamplerKey(
    val deviceGeneration: Long,
    val descriptorHash: String,
)

data class GPUPreparedImageBindingKey(
    val layoutHash: String,
    val uploadKey: GPUPreparedImageUploadKey,
    val samplerKey: GPUPreparedImageSamplerKey,
)

internal interface GPUPreparedImageNativeHandleFactory {
    fun createTexture(request: GPUPreparedImageFrameResourcePlan): GPUTexture
    fun createTextureView(texture: GPUTexture, request: GPUPreparedImageFrameResourcePlan): GPUTextureView
    fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler
    fun createUniformBuffer(size: Long): GPUBuffer
    fun createBindGroup(
        request: GPUPreparedImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup
}

internal interface GPUPreparedImageNativeResourceSet : AutoCloseable {
    fun uploadKey(artifactKey: GPUImageUploadArtifactKey): GPUPreparedImageUploadKey
    fun texture(artifactKey: GPUImageUploadArtifactKey): GPUPreparedNativeTextureOperand
    fun binding(packetId: String): GPUPreparedNativeBindGroupOperand
    fun dynamicUniformOffset(packetId: String): Long
}

internal data class GPUPreparedImageNativePreflightRequest(
    val resourcePlan: GPUPreparedImageFrameResourcePlan,
    val artifactKey: GPUImageUploadArtifactKey,
    val capabilities: GPUCapabilities,
    val expectedDeviceGeneration: Long,
    val actualDeviceGeneration: Long,
    val expectedResourceGeneration: Long,
    val actualResourceGeneration: Long,
    val expectedOwner: String,
    val actualOwner: String,
    val activeAttachment: GPUFrameTextureRef? = null,
) {
    init {
        require(expectedDeviceGeneration >= 0L && actualDeviceGeneration >= 0L)
        require(expectedResourceGeneration >= 0L && actualResourceGeneration >= 0L)
        require(expectedOwner.isNotBlank() && actualOwner.isNotBlank())
    }
}

internal sealed interface GPUPreparedImageNativePreflightResult {
    data class Refused(val reasonCode: String) : GPUPreparedImageNativePreflightResult {
        init {
            require(reasonCode.isNotBlank())
        }
    }

    class Sealed internal constructor(
        internal val request: GPUPreparedImageNativePreflightRequest,
        val uploadKeys: List<GPUPreparedImageUploadKey>,
        val samplerKeysByPacketId: Map<String, GPUPreparedImageSamplerKey>,
        val bindingKeysByPacketId: Map<String, GPUPreparedImageBindingKey>,
    ) : GPUPreparedImageNativePreflightResult {
        fun materialize(factory: GPUPreparedImageNativeHandleFactory): GPUPreparedImageNativeResourceSet =
            materializePreparedImageNativeResources(this, factory)
    }
}

internal object GPUPreparedImageNativeResourcePreflighter {
    fun preflight(request: GPUPreparedImageNativePreflightRequest): GPUPreparedImageNativePreflightResult {
        refusalReason(request)?.let { return GPUPreparedImageNativePreflightResult.Refused(it) }

        val plan = request.resourcePlan
        val uploadKey = GPUPreparedImageUploadKey(
            artifactKey = request.artifactKey,
            deviceGeneration = request.actualDeviceGeneration,
            textureDescriptorHash = plan.textureDescriptor.preparedImageDescriptorHash(),
            viewDescriptorHash = plan.bindingRequests.first().view.preparedImageViewHash(),
        )
        val samplerKeys = plan.bindingRequests.associate { binding ->
            binding.packetId to GPUPreparedImageSamplerKey(
                deviceGeneration = request.actualDeviceGeneration,
                descriptorHash = binding.sampler.preparedImageSamplerHash(),
            )
        }
        val bindingKeys = plan.bindingRequests.associate { binding ->
            binding.packetId to GPUPreparedImageBindingKey(
                layoutHash = binding.bindingLayoutHash,
                uploadKey = uploadKey,
                samplerKey = samplerKeys.getValue(binding.packetId),
            )
        }
        return GPUPreparedImageNativePreflightResult.Sealed(
            request = request,
            uploadKeys = listOf(uploadKey),
            samplerKeysByPacketId = samplerKeys.toMap(),
            bindingKeysByPacketId = bindingKeys.toMap(),
        )
    }

    private fun refusalReason(request: GPUPreparedImageNativePreflightRequest): String? {
        val plan = request.resourcePlan
        if (request.activeAttachment == plan.frameTextureRef) {
            return "unsupported.prepared_image.active_attachment"
        }
        if (!plan.textureDescriptor.usageLabels.containsAll(setOf("copy_dst", "texture_binding"))) {
            return "unsupported.prepared_image.texture_usage"
        }
        if (request.expectedOwner != request.actualOwner) {
            return "unsupported.prepared_image.owner_mismatch"
        }
        if (request.expectedDeviceGeneration != request.actualDeviceGeneration) {
            return "unsupported.prepared_image.device_generation"
        }
        if (request.expectedResourceGeneration != request.actualResourceGeneration) {
            return "unsupported.prepared_image.resource_generation"
        }
        val limits = request.capabilities.limits ?: return "unsupported.prepared_image.device_limit"
        if (plan.textureDescriptor.width.toLong() > limits.maxTextureDimension2D ||
            plan.textureDescriptor.height.toLong() > limits.maxTextureDimension2D ||
            limits.maxBufferSize?.let { plan.uploadTaskLayout.byteSize > it } == true ||
            plan.uploadTaskLayout.bytesPerRow % limits.copyBytesPerRowAlignment != 0L
        ) {
            return "unsupported.prepared_image.device_limit"
        }
        if (plan.bindingRequests.isEmpty() ||
            plan.bindingRequests.any { it.artifactKey != request.artifactKey } ||
            plan.bindingRequests.map { it.packetId }.distinct().size != plan.bindingRequests.size
        ) {
            return "unsupported.prepared_image.artifact_identity"
        }
        if (plan.textureRef.value != plan.frameTextureRef.value ||
            plan.bindingRequests.any {
                it.texture != plan.textureDescriptor ||
                    it.uniformAllocation.packetId != it.packetId ||
                    it.uniformAllocation.offset % limits.minUniformBufferOffsetAlignment != 0L
            }
        ) {
            return "unsupported.prepared_image.plan_identity"
        }
        val texturePreparation = plan.preparationRequests.singleOrNull {
            it.resource == plan.frameTextureRef
        }
        if (texturePreparation == null ||
            texturePreparation.role != GPUFrameResourceRole.StorageData ||
            !texturePreparation.usages.containsAll(
                setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.TextureBinding,
                ),
            )
        ) {
            return "unsupported.prepared_image.texture_usage"
        }
        return null
    }
}

private fun materializePreparedImageNativeResources(
    seal: GPUPreparedImageNativePreflightResult.Sealed,
    factory: GPUPreparedImageNativeHandleFactory,
): GPUPreparedImageNativeResourceSet {
    val plan = seal.request.resourcePlan
    val generation = GPUDeviceGenerationID(seal.request.actualDeviceGeneration)
    val created = mutableListOf<AutoCloseable>()
    try {
        val texture = factory.createTexture(plan).also(created::add)
        val textureView = factory.createTextureView(texture, plan).also(created::add)
        val samplersByKey = linkedMapOf<GPUPreparedImageSamplerKey, GPUSampler>()
        plan.bindingRequests.forEach { binding ->
            val key = seal.samplerKeysByPacketId.getValue(binding.packetId)
            samplersByKey.getOrPut(key) {
                factory.createSampler(binding.sampler).also(created::add)
            }
        }
        val uniformSize = plan.bindingRequests.maxOf {
            Math.addExact(it.uniformAllocation.offset, it.uniformAllocation.size)
        }
        val uniformBuffer = factory.createUniformBuffer(uniformSize).also(created::add)
        val bindGroupsByKey = linkedMapOf<GPUPreparedImageBindingKey, GPUBindGroup>()
        plan.bindingRequests.forEach { binding ->
            val bindingKey = seal.bindingKeysByPacketId.getValue(binding.packetId)
            bindGroupsByKey.getOrPut(bindingKey) {
                val samplerKey = seal.samplerKeysByPacketId.getValue(binding.packetId)
                factory.createBindGroup(
                    request = binding,
                    uniformBuffer = uniformBuffer,
                    textureView = textureView,
                    sampler = samplersByKey.getValue(samplerKey),
                ).also(created::add)
            }
        }
        val textureOperand = GPUPreparedNativeTextureOperand(
            texture = texture,
            deviceGeneration = generation,
            ownership = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        )
        val bindGroupOperandsByKey = bindGroupsByKey.mapValues { (_, bindGroup) ->
            GPUPreparedNativeBindGroupOperand(
                bindGroup = bindGroup,
                deviceGeneration = generation,
                ownership = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            )
        }
        val bindings = plan.bindingRequests.associate { binding ->
            binding.packetId to bindGroupOperandsByKey.getValue(
                seal.bindingKeysByPacketId.getValue(binding.packetId),
            )
        }
        return GPUPreparedImageNativeResourceSetImpl(
            uploadKey = seal.uploadKeys.single(),
            texture = textureOperand,
            bindings = bindings,
            offsets = plan.bindingRequests.associate {
                it.packetId to it.uniformAllocation.offset
            },
            handles = created,
        )
    } catch (failure: Throwable) {
        closePreparedImageHandles(created, failure)
        throw failure
    }
}

private class GPUPreparedImageNativeResourceSetImpl(
    private val uploadKey: GPUPreparedImageUploadKey,
    private val texture: GPUPreparedNativeTextureOperand,
    private val bindings: Map<String, GPUPreparedNativeBindGroupOperand>,
    private val offsets: Map<String, Long>,
    handles: List<AutoCloseable>,
) : GPUPreparedImageNativeResourceSet {
    private val pending = handles.toMutableList()
    private var closed = false

    override fun uploadKey(artifactKey: GPUImageUploadArtifactKey): GPUPreparedImageUploadKey {
        check(!closed) { "Prepared-image native resources are closed" }
        require(artifactKey == uploadKey.artifactKey) { "Unknown prepared-image upload artifact" }
        return uploadKey
    }

    override fun texture(artifactKey: GPUImageUploadArtifactKey): GPUPreparedNativeTextureOperand {
        uploadKey(artifactKey)
        return texture
    }

    override fun binding(packetId: String): GPUPreparedNativeBindGroupOperand {
        check(!closed) { "Prepared-image native resources are closed" }
        return requireNotNull(bindings[packetId]) { "Unknown prepared-image packet $packetId" }
    }

    override fun dynamicUniformOffset(packetId: String): Long {
        check(!closed) { "Prepared-image native resources are closed" }
        return requireNotNull(offsets[packetId]) { "Unknown prepared-image packet $packetId" }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closePreparedImageHandles(pending)
        pending.clear()
        closed = true
    }
}

private fun closePreparedImageHandles(
    handles: List<AutoCloseable>,
    primaryFailure: Throwable? = null,
) {
    var closeFailure: Throwable? = null
    handles.asReversed().forEach { handle ->
        try {
            handle.close()
        } catch (failure: Throwable) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(failure)
            } else {
                val previous = closeFailure
                if (previous == null) {
                    closeFailure = failure
                } else {
                    previous.addSuppressed(failure)
                }
            }
        }
    }
    if (primaryFailure == null) closeFailure?.let { throw it }
}

private fun GPUTextureDescriptor.preparedImageDescriptorHash(): String = preparedImageHash(
    "texture",
    width.toString(),
    height.toString(),
    format,
    sampleCount.toString(),
    usageLabels.sorted().joinToString("+"),
)

private fun GPUTextureViewDescriptor.preparedImageViewHash(): String = preparedImageHash(
    "view",
    textureDescriptorHash,
    viewDimension,
    mipRange.toString(),
    arrayLayerRange.toString(),
)

private fun GPUSamplerDescriptor.preparedImageSamplerHash(): String = preparedImageHash(
    "sampler",
    addressModeU,
    addressModeV,
    magFilter,
    minFilter,
    mipmapFilter,
    lodMinClamp,
    lodMaxClamp,
    compareMode,
    maxAnisotropy.toString(),
    capabilityRequirements.sorted().joinToString("+"),
)

private fun preparedImageHash(vararg tokens: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(tokens.joinToString("\u0000").encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
