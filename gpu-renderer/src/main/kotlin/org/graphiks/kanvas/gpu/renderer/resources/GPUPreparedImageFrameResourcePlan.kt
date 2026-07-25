package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID

class GPUPreparedImageUploadLayout internal constructor(
    val logicalBytesPerRow: Long,
    val sourceBytesPerRow: Long = logicalBytesPerRow,
    val bytesPerRow: Long,
    val rowsPerImage: Int,
    val width: Int,
    val height: Int,
    paddedUploadBytes: ByteArray,
) {
    private val uploadBytes = paddedUploadBytes.copyOf()

    fun bytesForUpload(): ByteArray = uploadBytes.copyOf()

    internal fun snapshot(): GPUPreparedImageUploadLayout = GPUPreparedImageUploadLayout(
        sourceBytesPerRow = sourceBytesPerRow,
        logicalBytesPerRow = logicalBytesPerRow,
        bytesPerRow = bytesPerRow,
        rowsPerImage = rowsPerImage,
        width = width,
        height = height,
        paddedUploadBytes = uploadBytes,
    )

    override fun equals(other: Any?): Boolean =
        other is GPUPreparedImageUploadLayout &&
            sourceBytesPerRow == other.sourceBytesPerRow &&
            logicalBytesPerRow == other.logicalBytesPerRow &&
            bytesPerRow == other.bytesPerRow &&
            rowsPerImage == other.rowsPerImage &&
            width == other.width &&
            height == other.height &&
            uploadBytes.contentEquals(other.uploadBytes)

    override fun hashCode(): Int {
        var result = sourceBytesPerRow.hashCode()
        result = 31 * result + logicalBytesPerRow.hashCode()
        result = 31 * result + bytesPerRow.hashCode()
        result = 31 * result + rowsPerImage
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + uploadBytes.contentHashCode()
        return result
    }

    override fun toString(): String =
        "GPUPreparedImageUploadLayout(sourceBytesPerRow=$sourceBytesPerRow, " +
            "logicalBytesPerRow=$logicalBytesPerRow, " +
            "bytesPerRow=$bytesPerRow, rowsPerImage=$rowsPerImage, width=$width, height=$height, " +
            "payloadByteSize=${uploadBytes.size}, payloadSha256=${uploadBytes.sha256()})"
}

data class GPUPreparedImageUniformAllocation(
    val packetId: String,
    val offset: Long,
    val size: Long,
)

data class GPUPreparedImageBindingInput(
    val packetId: String,
    val sampling: GPUPreparedImageSampling,
)

data class GPUPreparedImageBindingRequest(
    val packetId: String,
    val artifactKey: GPUImageUploadArtifactKey,
    val texture: GPUTextureDescriptor,
    val view: GPUTextureViewDescriptor,
    val sampler: GPUSamplerDescriptor,
    val bindingLayoutHash: String,
    val uniformAllocation: GPUPreparedImageUniformAllocation,
)

private object GPUPreparedImageFrameResourcePlanSnapshot

data class GPUPreparedImageFrameResourcePlan private constructor(
    val stagingRef: GPUFrameBufferRef,
    val textureRef: GPUTextureResourceRef,
    val frameTextureRef: GPUFrameTextureRef,
    val uniformRef: GPUFrameBufferRef,
    val textureDescriptor: GPUTextureDescriptor,
    val uploadLayout: GPUPreparedImageUploadLayout,
    val uploadTaskLayout: GPUUploadLayout,
    val bindingRequests: List<GPUPreparedImageBindingRequest>,
    val preparationRequests: List<GPUResourcePreparationRequest>,
    val memoryAllocations: List<GPUFrameMemoryAllocation>,
    val uploadTaskId: GPUTaskID,
    private val snapshotMarker: GPUPreparedImageFrameResourcePlanSnapshot,
) {
    constructor(
        stagingRef: GPUFrameBufferRef,
        textureRef: GPUTextureResourceRef,
        frameTextureRef: GPUFrameTextureRef,
        uniformRef: GPUFrameBufferRef,
        textureDescriptor: GPUTextureDescriptor,
        uploadLayout: GPUPreparedImageUploadLayout,
        uploadTaskLayout: GPUUploadLayout,
        bindingRequests: List<GPUPreparedImageBindingRequest>,
        preparationRequests: List<GPUResourcePreparationRequest>,
        memoryAllocations: List<GPUFrameMemoryAllocation>,
        uploadTaskId: GPUTaskID,
    ) : this(
        stagingRef = stagingRef,
        textureRef = textureRef,
        frameTextureRef = frameTextureRef,
        uniformRef = uniformRef,
        textureDescriptor = textureDescriptor.snapshot(),
        uploadLayout = uploadLayout.snapshot(),
        uploadTaskLayout = uploadTaskLayout.copy(),
        bindingRequests = bindingRequests.snapshotPreparedImageBindings(),
        preparationRequests = preparationRequests.snapshotPreparedImagePreparations(),
        memoryAllocations = memoryAllocations.snapshotPreparedImageAllocations(),
        uploadTaskId = uploadTaskId,
        snapshotMarker = GPUPreparedImageFrameResourcePlanSnapshot,
    )

    @Suppress("DataClassPrivateConstructor")
    fun copy(
        stagingRef: GPUFrameBufferRef = this.stagingRef,
        textureRef: GPUTextureResourceRef = this.textureRef,
        frameTextureRef: GPUFrameTextureRef = this.frameTextureRef,
        uniformRef: GPUFrameBufferRef = this.uniformRef,
        textureDescriptor: GPUTextureDescriptor = this.textureDescriptor,
        uploadLayout: GPUPreparedImageUploadLayout = this.uploadLayout,
        uploadTaskLayout: GPUUploadLayout = this.uploadTaskLayout,
        bindingRequests: List<GPUPreparedImageBindingRequest> = this.bindingRequests,
        preparationRequests: List<GPUResourcePreparationRequest> = this.preparationRequests,
        memoryAllocations: List<GPUFrameMemoryAllocation> = this.memoryAllocations,
        uploadTaskId: GPUTaskID = this.uploadTaskId,
    ): GPUPreparedImageFrameResourcePlan = GPUPreparedImageFrameResourcePlan(
        stagingRef = stagingRef,
        textureRef = textureRef,
        frameTextureRef = frameTextureRef,
        uniformRef = uniformRef,
        textureDescriptor = if (textureDescriptor === this.textureDescriptor) {
            textureDescriptor
        } else {
            textureDescriptor.snapshot()
        },
        uploadLayout = if (uploadLayout === this.uploadLayout) uploadLayout else uploadLayout.snapshot(),
        uploadTaskLayout = if (uploadTaskLayout === this.uploadTaskLayout) {
            uploadTaskLayout
        } else {
            uploadTaskLayout.copy()
        },
        bindingRequests = if (bindingRequests === this.bindingRequests) {
            bindingRequests
        } else {
            bindingRequests.snapshotPreparedImageBindings()
        },
        preparationRequests = if (preparationRequests === this.preparationRequests) {
            preparationRequests
        } else {
            preparationRequests.snapshotPreparedImagePreparations()
        },
        memoryAllocations = if (memoryAllocations === this.memoryAllocations) {
            memoryAllocations
        } else {
            memoryAllocations.snapshotPreparedImageAllocations()
        },
        uploadTaskId = uploadTaskId,
        snapshotMarker = GPUPreparedImageFrameResourcePlanSnapshot,
    )

    override fun toString(): String =
        "GPUPreparedImageFrameResourcePlan(stagingRef=$stagingRef, textureRef=$textureRef, " +
            "frameTextureRef=$frameTextureRef, uniformRef=$uniformRef, " +
            "textureDescriptor=$textureDescriptor, uploadLayout=$uploadLayout, " +
            "uploadTaskLayout=$uploadTaskLayout, bindingRequests=$bindingRequests, " +
            "preparationRequests=$preparationRequests, memoryAllocations=$memoryAllocations, " +
            "uploadTaskId=$uploadTaskId)"
}

internal fun buildPreparedImageFrameResourcePlanFromBindings(
    artifact: GPUPreparedImageUploadArtifact,
    bindingInputs: List<GPUPreparedImageBindingInput>,
    bindingLayoutHash: String,
    capabilities: GPUCapabilities,
    frameIdentity: String,
    uploadTaskId: GPUTaskID,
): GPUPreparedImageFrameResourcePlan {
    require(bindingInputs.isNotEmpty() &&
        bindingInputs.map(GPUPreparedImageBindingInput::packetId).distinct().size == bindingInputs.size
    ) {
        "Prepared-image packet IDs must be non-empty and unique"
    }
    require(bindingLayoutHash.isNotBlank()) { "bindingLayoutHash must not be blank" }
    require(frameIdentity.isNotBlank()) { "frameIdentity must not be blank" }
    require(artifact.colorInterpretation == GPUColorInterpretation.EncodedPremulSrgb.value) {
        "Prepared images must retain EncodedPremulSrgb interpretation"
    }

    val limits = requireNotNull(capabilities.limits) {
        "Prepared-image resource planning requires observed device limits"
    }
    val logicalBytesPerRow = Math.multiplyExact(artifact.width.toLong(), 4L)
    require(artifact.pixelLayout.normalizedRgba8RowBytes == logicalBytesPerRow) {
        "Prepared-image artifact must use a tight logical RGBA8 row stride"
    }
    val bytesPerRow = alignUp(logicalBytesPerRow, limits.copyBytesPerRowAlignment)
    val uploadByteSize = Math.multiplyExact(bytesPerRow, artifact.height.toLong())
    require(uploadByteSize <= Int.MAX_VALUE) { "Prepared-image upload exceeds JVM byte-array capacity" }
    require(limits.maxBufferSize == null || uploadByteSize <= limits.maxBufferSize) {
        "Prepared-image upload exceeds the observed buffer-size limit"
    }

    val tightBytes = artifact.tightRgba8BytesForUpload()
    require(tightBytes.size.toLong() == Math.multiplyExact(logicalBytesPerRow, artifact.height.toLong())) {
        "Prepared-image artifact byte length does not match its logical RGBA8 layout"
    }
    val paddedBytes = ByteArray(uploadByteSize.toInt())
    repeat(artifact.height) { row ->
        val sourceOffset = Math.multiplyExact(row.toLong(), logicalBytesPerRow).toInt()
        val targetOffset = Math.multiplyExact(row.toLong(), bytesPerRow).toInt()
        tightBytes.copyInto(
            destination = paddedBytes,
            destinationOffset = targetOffset,
            startIndex = sourceOffset,
            endIndex = sourceOffset + logicalBytesPerRow.toInt(),
        )
    }

    val textureDescriptor = GPUTextureDescriptor(
        width = artifact.width,
        height = artifact.height,
        format = "RGBA8Unorm",
        usageLabels = setOf("copy_dst", "texture_binding"),
    )
    val descriptorIdentity = listOf(
        artifact.key.value,
        artifact.contentHash,
        artifact.width,
        artifact.height,
        textureDescriptor.format,
    ).joinToString(":")
    val view = GPUTextureViewDescriptor(
        textureDescriptorHash = descriptorIdentity,
        viewDimension = "2d",
        mipRange = 0..0,
        arrayLayerRange = 0..0,
    )
    val uniformSize = 48L
    val uniformStride = alignUp(uniformSize, limits.minUniformBufferOffsetAlignment)
    val bindingRequests = bindingInputs.mapIndexed { index, bindingInput ->
        val filter = when (bindingInput.sampling) {
            GPUPreparedImageSampling.Nearest -> "nearest"
            GPUPreparedImageSampling.Linear -> "linear"
        }
        GPUPreparedImageBindingRequest(
            packetId = bindingInput.packetId,
            artifactKey = artifact.key,
            texture = textureDescriptor,
            view = view,
            sampler = GPUSamplerDescriptor(
                addressModeU = "clamp-to-edge",
                addressModeV = "clamp-to-edge",
                magFilter = filter,
                minFilter = filter,
                mipmapFilter = "none",
            ),
            bindingLayoutHash = bindingLayoutHash,
            uniformAllocation = GPUPreparedImageUniformAllocation(
                packetId = bindingInput.packetId,
                offset = Math.multiplyExact(index.toLong(), uniformStride),
                size = uniformSize,
            ),
        )
    }
    val frameResourceIdentity = "$frameIdentity|${artifact.key.value}".encodeToByteArray().sha256()
    val stagingRef = GPUFrameBufferRef("prepared-image-staging:$frameResourceIdentity")
    val textureRef = GPUTextureResourceRef("prepared-image-texture:$frameResourceIdentity")
    val frameTextureRef = GPUFrameTextureRef(textureRef.value)
    val uniformRef = GPUFrameBufferRef("prepared-image-uniforms:${textureRef.value}")
    val textureBytes = Math.multiplyExact(
        Math.multiplyExact(artifact.width.toLong(), artifact.height.toLong()),
        4L,
    )
    val uniformBytes = bindingRequests.maxOf { binding ->
        binding.uniformAllocation.offset + binding.uniformAllocation.size
    }
    val imageBounds = GPUPixelBounds(0, 0, artifact.width, artifact.height)
    val preparationRequests = listOf(
        GPUResourcePreparationRequest(
            resource = stagingRef,
            descriptor = GPUFrameBufferDescriptor(uploadByteSize, 4L),
            role = GPUFrameResourceRole.UploadStaging,
            usages = setOf(GPUFrameResourceUsage.CopySource),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = uploadByteSize,
            diagnosticLabel = "prepared-image.upload-staging.${textureRef.value}",
        ),
        GPUResourcePreparationRequest(
            resource = frameTextureRef,
            descriptor = GPUFrameTextureDescriptor(
                logicalBounds = imageBounds,
                format = GPUColorFormat("rgba8unorm"),
                sampleCount = 1,
            ),
            role = GPUFrameResourceRole.StorageData,
            usages = setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.TextureBinding,
            ),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = textureBytes,
            diagnosticLabel = "prepared-image.texture.${textureRef.value}",
        ),
        GPUResourcePreparationRequest(
            resource = uniformRef,
            descriptor = GPUFrameBufferDescriptor(
                byteSize = uniformBytes,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
            ),
            role = GPUFrameResourceRole.UniformData,
            usages = setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = uniformBytes,
            diagnosticLabel = "prepared-image.uniforms.${textureRef.value}",
        ),
    )
    return GPUPreparedImageFrameResourcePlan(
        stagingRef = stagingRef,
        textureRef = textureRef,
        frameTextureRef = frameTextureRef,
        uniformRef = uniformRef,
        textureDescriptor = textureDescriptor,
        uploadLayout = GPUPreparedImageUploadLayout(
            sourceBytesPerRow = artifact.pixelLayout.sourceRowBytes,
            logicalBytesPerRow = logicalBytesPerRow,
            bytesPerRow = bytesPerRow,
            rowsPerImage = artifact.height,
            width = artifact.width,
            height = artifact.height,
            paddedUploadBytes = paddedBytes,
        ),
        uploadTaskLayout = GPUUploadLayout(
            sourceOffsetBytes = 0L,
            bytesPerRow = bytesPerRow,
            rowsPerImage = artifact.height,
            byteSize = uploadByteSize,
        ),
        bindingRequests = bindingRequests,
        preparationRequests = preparationRequests,
        memoryAllocations = listOf(
            GPUFrameMemoryAllocation(
                label = "prepared-image.staging.${artifact.key.value}",
                category = GPUFrameMemoryCategory.ReusableScratch,
                bytes = uploadByteSize,
                resourceKind = GPUFrameMemoryResourceKind.Buffer,
                extent = null,
            ),
            GPUFrameMemoryAllocation(
                label = "prepared-image.texture.${artifact.key.value}",
                category = GPUFrameMemoryCategory.ReusableScratch,
                bytes = textureBytes,
                resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                extent = imageBounds,
            ),
            GPUFrameMemoryAllocation(
                label = "prepared-image.uniforms.${artifact.key.value}",
                category = GPUFrameMemoryCategory.ReusableScratch,
                bytes = uniformBytes,
                resourceKind = GPUFrameMemoryResourceKind.Buffer,
                extent = null,
            ),
        ),
        uploadTaskId = uploadTaskId,
    )
}

internal fun buildPreparedImageFrameResourcePlan(
    artifact: GPUPreparedImageUploadArtifact,
    packetIds: List<String>,
    bindingLayoutHash: String,
    capabilities: GPUCapabilities,
    frameIdentity: String,
    uploadTaskId: GPUTaskID,
): GPUPreparedImageFrameResourcePlan = buildPreparedImageFrameResourcePlanFromBindings(
    artifact = artifact,
    bindingInputs = packetIds.map { packetId ->
        GPUPreparedImageBindingInput(packetId, GPUPreparedImageSampling.Nearest)
    },
    bindingLayoutHash = bindingLayoutHash,
    capabilities = capabilities,
    frameIdentity = frameIdentity,
    uploadTaskId = uploadTaskId,
)

private fun alignUp(value: Long, alignment: Long): Long {
    require(value >= 0L && alignment > 0L)
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

private fun GPUTextureDescriptor.snapshot(): GPUTextureDescriptor = copy(
    usageLabels = immutableSet(usageLabels),
)

private fun GPUSamplerDescriptor.snapshot(): GPUSamplerDescriptor = copy(
    capabilityRequirements = immutableSet(capabilityRequirements),
)

private fun GPUPreparedImageBindingRequest.snapshot(): GPUPreparedImageBindingRequest = copy(
    texture = texture.snapshot(),
    view = view.copy(
        mipRange = view.mipRange.first..view.mipRange.last,
        arrayLayerRange = view.arrayLayerRange.first..view.arrayLayerRange.last,
    ),
    sampler = sampler.snapshot(),
    uniformAllocation = uniformAllocation.copy(),
)

private fun List<GPUPreparedImageBindingRequest>.snapshotPreparedImageBindings():
    List<GPUPreparedImageBindingRequest> = immutableList(
        map(GPUPreparedImageBindingRequest::snapshot),
    )

private fun List<GPUResourcePreparationRequest>.snapshotPreparedImagePreparations():
    List<GPUResourcePreparationRequest> = immutableList(
        map(GPUResourcePreparationRequest::snapshotForPreparedImage),
    )

private fun List<GPUFrameMemoryAllocation>.snapshotPreparedImageAllocations():
    List<GPUFrameMemoryAllocation> = immutableList(
        map(GPUFrameMemoryAllocation::copy),
    )

private fun GPUResourcePreparationRequest.snapshotForPreparedImage():
    GPUResourcePreparationRequest = GPUResourcePreparationRequest(
    resource = resource,
    descriptor = when (val value = descriptor) {
        is GPUFrameBufferDescriptor -> value.copy()
        is GPUFrameTextureDescriptor -> value.copy()
    },
    role = role,
    usages = immutableSet(usages),
    lifetime = lifetime,
    byteSize = byteSize,
    diagnosticLabel = diagnosticLabel,
)

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
