package org.graphiks.kanvas.gpu.renderer.resources

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.images.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID

class GPUPreparedImageUploadLayout internal constructor(
    val logicalBytesPerRow: Long,
    val bytesPerRow: Long,
    val rowsPerImage: Int,
    val width: Int,
    val height: Int,
    paddedUploadBytes: ByteArray,
) {
    private val uploadBytes = paddedUploadBytes.copyOf()

    fun bytesForUpload(): ByteArray = uploadBytes.copyOf()
}

data class GPUPreparedImageUniformAllocation(
    val packetId: String,
    val offset: Long,
    val size: Long,
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

data class GPUPreparedImageFrameResourcePlan(
    val stagingRef: GPUFrameBufferRef,
    val textureRef: GPUTextureResourceRef,
    val textureDescriptor: GPUTextureDescriptor,
    val uploadLayout: GPUPreparedImageUploadLayout,
    val bindingRequests: List<GPUPreparedImageBindingRequest>,
    val uploadTaskId: GPUTaskID,
)

internal fun buildPreparedImageFrameResourcePlan(
    artifact: GPUPreparedImageUploadArtifact,
    packetIds: List<String>,
    bindingLayoutHash: String,
    capabilities: GPUCapabilities,
    frameIdentity: String,
    uploadTaskId: GPUTaskID,
): GPUPreparedImageFrameResourcePlan {
    require(packetIds.isNotEmpty() && packetIds.distinct().size == packetIds.size) {
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
    val sampler = GPUSamplerDescriptor(
        addressModeU = "clamp-to-edge",
        addressModeV = "clamp-to-edge",
        magFilter = "nearest",
        minFilter = "nearest",
        mipmapFilter = "none",
    )
    val uniformSize = 48L
    val uniformStride = alignUp(uniformSize, limits.minUniformBufferOffsetAlignment)
    val bindingRequests = packetIds.mapIndexed { index, packetId ->
        GPUPreparedImageBindingRequest(
            packetId = packetId,
            artifactKey = artifact.key,
            texture = textureDescriptor,
            view = view,
            sampler = sampler,
            bindingLayoutHash = bindingLayoutHash,
            uniformAllocation = GPUPreparedImageUniformAllocation(
                packetId = packetId,
                offset = Math.multiplyExact(index.toLong(), uniformStride),
                size = uniformSize,
            ),
        )
    }
    return GPUPreparedImageFrameResourcePlan(
        stagingRef = GPUFrameBufferRef("prepared-image-staging:$frameIdentity:${artifact.key.value}"),
        textureRef = GPUTextureResourceRef("prepared-image-texture:$frameIdentity:${artifact.key.value}"),
        textureDescriptor = textureDescriptor,
        uploadLayout = GPUPreparedImageUploadLayout(
            logicalBytesPerRow = logicalBytesPerRow,
            bytesPerRow = bytesPerRow,
            rowsPerImage = artifact.height,
            width = artifact.width,
            height = artifact.height,
            paddedUploadBytes = paddedBytes,
        ),
        bindingRequests = bindingRequests,
        uploadTaskId = uploadTaskId,
    )
}

private fun alignUp(value: Long, alignment: Long): Long {
    require(value >= 0L && alignment > 0L)
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}
