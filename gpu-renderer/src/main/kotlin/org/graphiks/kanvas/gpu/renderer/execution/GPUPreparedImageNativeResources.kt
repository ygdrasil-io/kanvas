package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureViewDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.preparedImageDescriptorHash

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

internal data class GPUPreparedImageNativeBindingKeys(
    val uploadKey: GPUPreparedImageUploadKey,
    val samplerKeysByPacketId: Map<String, GPUPreparedImageSamplerKey>,
    val bindingKeysByPacketId: Map<String, GPUPreparedImageBindingKey>,
)

internal interface GPUPreparedImageNativeHandleFactory {
    fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture
    fun createTextureView(texture: GPUTexture, request: GPUImageFrameResourcePlan): GPUTextureView
    fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler
    fun createUniformBuffer(size: Long): GPUBuffer
    fun createBindGroup(
        bindGroupLayout: GPUBindGroupLayout,
        request: GPUImageBindingRequest,
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
    val resourcePlan: GPUImageFrameResourcePlan,
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
    data class Refused(
        val reasonCode: String,
        val facts: Map<String, String> = emptyMap(),
    ) : GPUPreparedImageNativePreflightResult {
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
        fun materialize(
            factory: GPUPreparedImageNativeHandleFactory,
            bindGroupLayout: GPUBindGroupLayout,
        ): GPUPreparedImageNativeResourceSet =
            materializePreparedImageNativeResources(this, factory, bindGroupLayout)
    }
}

internal object GPUPreparedImageNativeResourcePreflighter {
    fun preflight(
        request: GPUPreparedImageNativePreflightRequest,
        shaderSource: String = GPU_PREPARED_IMAGE_WGSL,
    ): GPUPreparedImageNativePreflightResult {
        val shader = when (val validation = validatePreparedImageShader(shaderSource)) {
            is GPUPreparedImageShaderValidationResult.Ready -> validation
            is GPUPreparedImageShaderValidationResult.Refused ->
                return GPUPreparedImageNativePreflightResult.Refused(
                    reasonCode = validation.code,
                    facts = validation.facts,
                )
        }
        refusalReason(request, shader.bindingLayout.identity)?.let {
            return GPUPreparedImageNativePreflightResult.Refused(
                reasonCode = it,
                facts = mapOf("boundary" to "preflight"),
            )
        }

        val keys = request.resourcePlan.preparedImageNativeBindingKeys(
            deviceGeneration = request.actualDeviceGeneration,
        )
        return GPUPreparedImageNativePreflightResult.Sealed(
            request = request,
            uploadKeys = listOf(keys.uploadKey),
            samplerKeysByPacketId = keys.samplerKeysByPacketId,
            bindingKeysByPacketId = keys.bindingKeysByPacketId,
        )
    }

    private fun refusalReason(
        request: GPUPreparedImageNativePreflightRequest,
        bindingLayoutIdentity: String,
    ): String? {
        val plan = request.resourcePlan
        if (plan.bindingRequests.any { binding ->
                binding.bindingLayoutHash != bindingLayoutIdentity
            }
        ) {
            return GPUPreparedImageRefusalCodes.NATIVE_BINDING
        }
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
            return GPUPreparedImageRefusalCodes.NATIVE_GENERATION
        }
        if (request.expectedResourceGeneration != request.actualResourceGeneration) {
            return GPUPreparedImageRefusalCodes.NATIVE_GENERATION
        }
        if (plan.bindingRequests.isEmpty()) {
            return GPUPreparedImageRefusalCodes.NATIVE_BINDING
        }
        if (plan.bindingRequests.any { it.artifactKey != request.artifactKey } ||
            plan.bindingRequests.map { it.packetId }.distinct().size != plan.bindingRequests.size
        ) {
            return "unsupported.prepared_image.artifact_identity"
        }
        val expectedUniformBufferSize =
            plan.expectedUniformBufferSizeOrNull()
                ?: return "unsupported.prepared_image.uniform_allocation"
        if (plan.bindingRequests.any { binding ->
                binding.uniformAllocation.offset < 0L ||
                    binding.uniformAllocation.size <= 0L
            }
        ) {
            return "unsupported.prepared_image.uniform_allocation"
        }
        val expectedTextureByteSize =
            plan.expectedTextureByteSizeOrNull()
                ?: return GPUPreparedImageRefusalCodes.TEXTURE_LIMIT
        val limits = request.capabilities.limits ?: return GPUPreparedImageRefusalCodes.TEXTURE_LIMIT
        if (plan.textureDescriptor.width.toLong() > limits.maxTextureDimension2D ||
            plan.textureDescriptor.height.toLong() > limits.maxTextureDimension2D
        ) {
            return GPUPreparedImageRefusalCodes.TEXTURE_LIMIT
        }
        if (limits.maxBufferSize?.let { limit ->
                plan.uploadTaskLayout.byteSize > limit ||
                    expectedUniformBufferSize > limit ||
                    plan.preparationRequests.any { preparation -> preparation.byteSize > limit }
            } == true
        ) {
            return GPUPreparedImageRefusalCodes.UPLOAD_BUDGET_EXCEEDED
        }
        if (limits.maxDynamicUniformBuffersPerPipelineLayout?.let { it < 1L } == true) {
            return GPUPreparedImageRefusalCodes.NATIVE_BINDING
        }
        if (plan.uploadTaskLayout.bytesPerRow % limits.copyBytesPerRowAlignment != 0L) {
            return GPUPreparedImageRefusalCodes.PIXEL_ROW_STRIDE
        }
        val commonView = plan.bindingRequests.first().view
        if (plan.bindingRequests.any { binding -> binding.view != commonView } ||
            commonView.textureDescriptorHash != plan.textureDescriptor.preparedImageDescriptorHash() ||
            commonView.viewDimension != "2d" ||
            commonView.mipRange != 0..0 ||
            commonView.arrayLayerRange != 0..0
        ) {
            return "unsupported.prepared_image.view_identity"
        }
        if (!plan.hasExactUploadLayout()) {
            return "unsupported.prepared_image.upload_layout"
        }
        if (plan.textureRef.value != plan.frameTextureRef.value ||
            plan.bindingRequests.any {
                it.texture != plan.textureDescriptor ||
                    it.uniformAllocation.packetId != it.packetId ||
                    it.uniformAllocation.offset % limits.minUniformBufferOffsetAlignment != 0L
            }
        ) {
            return if (plan.bindingRequests.any {
                    it.uniformAllocation.offset % limits.minUniformBufferOffsetAlignment != 0L
                }
            ) {
                "unsupported.prepared_image.uniform_allocation"
            } else {
                "unsupported.prepared_image.plan_identity"
            }
        }
        if (!plan.hasExactStagingPreparation()) {
            return "unsupported.prepared_image.staging_preparation"
        }
        val texturePreparation = plan.preparationRequests.singleOrNull {
            it.resource == plan.frameTextureRef
        }
        if (texturePreparation == null ||
            texturePreparation.role != GPUFrameResourceRole.StorageData ||
            texturePreparation.usages !=
                setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.TextureBinding,
                ) ||
            texturePreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            texturePreparation.byteSize != expectedTextureByteSize ||
            (texturePreparation.descriptor as? GPUFrameTextureDescriptor)?.matches(plan) != true
        ) {
            return "unsupported.prepared_image.texture_usage"
        }
        if (!plan.hasExactUniformPreparation(
                requiredAlignment = limits.minUniformBufferOffsetAlignment,
                expectedSize = expectedUniformBufferSize,
            )
        ) {
            return "unsupported.prepared_image.uniform_preparation"
        }
        if (plan.preparationRequests.size != 3 ||
            plan.preparationRequests.map { it.resource }.toSet().size != 3
        ) {
            return "unsupported.prepared_image.plan_identity"
        }
        return null
    }
}

internal fun GPUImageFrameResourcePlan.preparedImageNativeBindingKeys(
    deviceGeneration: Long,
): GPUPreparedImageNativeBindingKeys {
    require(deviceGeneration >= 0L)
    val uploadKey = GPUPreparedImageUploadKey(
        artifactKey = artifactKey,
        deviceGeneration = deviceGeneration,
        textureDescriptorHash = textureDescriptor.preparedImageDescriptorHash(),
        viewDescriptorHash = bindingRequests.first().view.preparedImageViewHash(),
    )
    val samplerKeys = bindingRequests.associate { binding ->
        binding.packetId to GPUPreparedImageSamplerKey(
            deviceGeneration = deviceGeneration,
            descriptorHash = binding.sampler.preparedImageSamplerHash(),
        )
    }
    val bindingKeys = bindingRequests.associate { binding ->
        binding.packetId to GPUPreparedImageBindingKey(
            layoutHash = binding.bindingLayoutHash,
            uploadKey = uploadKey,
            samplerKey = samplerKeys.getValue(binding.packetId),
        )
    }
    return GPUPreparedImageNativeBindingKeys(
        uploadKey = uploadKey,
        samplerKeysByPacketId = samplerKeys.toMap(),
        bindingKeysByPacketId = bindingKeys.toMap(),
    )
}

private fun materializePreparedImageNativeResources(
    seal: GPUPreparedImageNativePreflightResult.Sealed,
    factory: GPUPreparedImageNativeHandleFactory,
    bindGroupLayout: GPUBindGroupLayout,
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
        val uniformSize = checkNotNull(plan.expectedUniformBufferSizeOrNull()) {
            "Sealed prepared-image uniform ranges must remain exact"
        }
        val uniformBuffer = factory.createUniformBuffer(uniformSize).also(created::add)
        val bindGroupsByKey = linkedMapOf<GPUPreparedImageBindingKey, GPUBindGroup>()
        plan.bindingRequests.forEach { binding ->
            val bindingKey = seal.bindingKeysByPacketId.getValue(binding.packetId)
            bindGroupsByKey.getOrPut(bindingKey) {
                val samplerKey = seal.samplerKeysByPacketId.getValue(binding.packetId)
                factory.createBindGroup(
                    bindGroupLayout = bindGroupLayout,
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
        closed = true
        val handles = pending.toList()
        pending.clear()
        closePreparedImageHandles(handles)
    }
}

private fun GPUImageFrameResourcePlan.hasExactUploadLayout(): Boolean {
    val expectedLogicalBytesPerRow =
        exactMultiplyOrNull(textureDescriptor.width.toLong(), 4L) ?: return false
    val expectedUploadBytes =
        exactMultiplyOrNull(uploadLayout.bytesPerRow, textureDescriptor.height.toLong()) ?: return false
    val expectedLogicalBytes =
        exactMultiplyOrNull(expectedLogicalBytesPerRow, textureDescriptor.height.toLong()) ?: return false
    if (uploadLayout.logicalBytesPerRow != expectedLogicalBytesPerRow ||
        uploadLayout.bytesPerRow < uploadLayout.logicalBytesPerRow ||
        uploadLayout.rowsPerImage != textureDescriptor.height ||
        uploadLayout.width != textureDescriptor.width ||
        uploadLayout.height != textureDescriptor.height ||
        uploadLayout.bytesForUpload().size.toLong() != expectedUploadBytes ||
        uploadLayout.logicalBytesForHash().size.toLong() != expectedLogicalBytes
    ) {
        return false
    }
    val bytes = uploadLayout.bytesForUpload()
    repeat(uploadLayout.height) { row ->
        val rowStart = exactMultiplyOrNull(row.toLong(), uploadLayout.bytesPerRow) ?: return false
        val paddingStart =
            exactAddOrNull(rowStart, uploadLayout.logicalBytesPerRow)?.toInt() ?: return false
        val rowEnd =
            exactMultiplyOrNull(row.toLong() + 1L, uploadLayout.bytesPerRow)?.toInt() ?: return false
        if ((paddingStart until rowEnd).any { index -> bytes[index] != 0.toByte() }) return false
    }
    return uploadTaskLayout.sourceOffsetBytes == 0L &&
        uploadTaskLayout.bytesPerRow == uploadLayout.bytesPerRow &&
        uploadTaskLayout.rowsPerImage == uploadLayout.rowsPerImage &&
        uploadTaskLayout.byteSize == expectedUploadBytes
}

private fun GPUImageFrameResourcePlan.hasExactStagingPreparation(): Boolean {
    val staging = preparationRequests.singleOrNull { it.resource == stagingRef } ?: return false
    val descriptor = staging.descriptor as? GPUFrameBufferDescriptor ?: return false
    return staging.role == GPUFrameResourceRole.UploadStaging &&
        staging.usages == setOf(GPUFrameResourceUsage.CopySource) &&
        staging.lifetime == GPUFrameResourceLifetime.FrameLocal &&
        staging.byteSize == uploadTaskLayout.byteSize &&
        descriptor.byteSize == uploadTaskLayout.byteSize &&
        descriptor.alignmentBytes == 4L
}

private fun GPUImageFrameResourcePlan.hasExactUniformPreparation(
    requiredAlignment: Long,
    expectedSize: Long,
): Boolean {
    val uniform = preparationRequests.singleOrNull { it.resource == uniformRef } ?: return false
    val descriptor = uniform.descriptor as? GPUFrameBufferDescriptor ?: return false
    return uniform.role == GPUFrameResourceRole.UniformData &&
        uniform.usages == setOf(
            GPUFrameResourceUsage.CopyDestination,
            GPUFrameResourceUsage.Uniform,
        ) &&
        uniform.lifetime == GPUFrameResourceLifetime.FrameLocal &&
        uniform.byteSize == expectedSize &&
        descriptor.byteSize == expectedSize &&
        descriptor.alignmentBytes == requiredAlignment
}

private fun GPUFrameTextureDescriptor.matches(
    plan: GPUImageFrameResourcePlan,
): Boolean =
    logicalBounds.left == 0 &&
        logicalBounds.top == 0 &&
        logicalBounds.right == plan.textureDescriptor.width &&
        logicalBounds.bottom == plan.textureDescriptor.height &&
        format.value.equals(plan.textureDescriptor.format, ignoreCase = true) &&
        sampleCount == plan.textureDescriptor.sampleCount

private fun GPUImageFrameResourcePlan.expectedUniformBufferSizeOrNull(): Long? {
    var maximum = 0L
    bindingRequests.forEach { binding ->
        val end = exactAddOrNull(
            binding.uniformAllocation.offset,
            binding.uniformAllocation.size,
        ) ?: return null
        maximum = maxOf(maximum, end)
    }
    return maximum
}

private fun GPUImageFrameResourcePlan.expectedTextureByteSizeOrNull(): Long? {
    val pixels =
        exactMultiplyOrNull(textureDescriptor.width.toLong(), textureDescriptor.height.toLong())
            ?: return null
    return exactMultiplyOrNull(pixels, 4L)
}

private fun exactAddOrNull(left: Long, right: Long): Long? =
    try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

private fun exactMultiplyOrNull(left: Long, right: Long): Long? =
    try {
        Math.multiplyExact(left, right)
    } catch (_: ArithmeticException) {
        null
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
