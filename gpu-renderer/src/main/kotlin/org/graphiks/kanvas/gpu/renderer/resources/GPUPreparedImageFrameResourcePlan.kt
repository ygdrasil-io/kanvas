package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUImageUploadArtifactKey
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedColorUploadEncoding
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.preparedSdrColorContract
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling

class GPUPreparedImageUploadLayout internal constructor(
    val logicalBytesPerRow: Long,
    val sourceBytesPerRow: Long = logicalBytesPerRow,
    override val bytesPerRow: Long,
    override val rowsPerImage: Int,
    override val width: Int,
    override val height: Int,
    paddedUploadBytes: ByteArray,
) : GPUPreparedTextureUploadLayout {
    private val uploadBytes = paddedUploadBytes.copyOf()

    init {
        require(sourceBytesPerRow > 0L) { "Prepared-image source row stride must be positive" }
        require(logicalBytesPerRow > 0L && logicalBytesPerRow <= bytesPerRow) {
            "Prepared-image logical row stride must fit the native row stride"
        }
        require(bytesPerRow <= Int.MAX_VALUE) { "Prepared-image native row stride exceeds JVM indexing" }
        require(width > 0 && height > 0 && rowsPerImage == height) {
            "Prepared-image upload dimensions and rows-per-image must agree"
        }
        require(uploadBytes.size.toLong() == Math.multiplyExact(bytesPerRow, height.toLong())) {
            "Prepared-image native payload size must match its padded row layout"
        }
        repeat(height) { row ->
            val paddingStart = Math.addExact(
                Math.multiplyExact(row.toLong(), bytesPerRow),
                logicalBytesPerRow,
            ).toInt()
            val rowEnd = Math.multiplyExact(row.toLong() + 1L, bytesPerRow).toInt()
            require((paddingStart until rowEnd).all { index -> uploadBytes[index] == 0.toByte() }) {
                "Prepared-image native row padding must be zero"
            }
        }
    }

    override fun bytesForUpload(): ByteArray = uploadBytes.copyOf()

    internal fun logicalBytesForHash(): ByteArray {
        val logicalSize = Math.multiplyExact(logicalBytesPerRow, height.toLong())
        require(logicalSize <= Int.MAX_VALUE) { "Prepared-image logical payload exceeds JVM indexing" }
        return ByteArray(logicalSize.toInt()).also { logical ->
            repeat(height) { row ->
                val sourceOffset = Math.multiplyExact(row.toLong(), bytesPerRow).toInt()
                val targetOffset = Math.multiplyExact(row.toLong(), logicalBytesPerRow).toInt()
                uploadBytes.copyInto(
                    destination = logical,
                    destinationOffset = targetOffset,
                    startIndex = sourceOffset,
                    endIndex = sourceOffset + logicalBytesPerRow.toInt(),
                )
            }
        }
    }

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
            "nativePayloadByteSize=${uploadBytes.size}, " +
            "payloadByteSize=${logicalBytesForHash().size}, " +
            "payloadSha256=${logicalBytesForHash().sha256()})"
}

data class GPUPreparedImageUniformAllocation(
    val packetId: String,
    val offset: Long,
    val size: Long,
)

data class GPUImageBindingInput(
    val packetId: String,
    val sampling: GPUPreparedImageSampling,
)

data class GPUImageBindingRequest(
    val packetId: String,
    val artifactKey: GPUImageUploadArtifactKey,
    val texture: GPUTextureDescriptor,
    val view: GPUTextureViewDescriptor,
    val sampler: GPUSamplerDescriptor,
    val bindingLayoutHash: String,
    val uniformAllocation: GPUPreparedImageUniformAllocation,
)

internal const val GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES = 112L

private object GPUImageFrameResourcePlanSnapshot

class GPUImageFrameResourcePlan private constructor(
    override val stagingRef: GPUFrameBufferRef,
    val textureRef: GPUTextureResourceRef,
    override val frameTextureRef: GPUFrameTextureRef,
    val uniformRef: GPUFrameBufferRef,
    val textureDescriptor: GPUTextureDescriptor,
    val uploadLayout: GPUPreparedImageUploadLayout,
    override val uploadTaskLayout: GPUUploadLayout,
    val bindingRequests: List<GPUImageBindingRequest>,
    override val preparationRequests: List<GPUResourcePreparationRequest>,
    override val memoryAllocations: List<GPUFrameMemoryAllocation>,
    val artifactKey: GPUImageUploadArtifactKey,
    val artifactWidth: Int,
    val artifactHeight: Int,
    val artifactContentHash: String,
    private val snapshotMarker: GPUImageFrameResourcePlanSnapshot,
) : GPUTextureFrameResourcePlan {
    constructor(
        stagingRef: GPUFrameBufferRef,
        textureRef: GPUTextureResourceRef,
        frameTextureRef: GPUFrameTextureRef,
        uniformRef: GPUFrameBufferRef,
        textureDescriptor: GPUTextureDescriptor,
        uploadLayout: GPUPreparedImageUploadLayout,
        uploadTaskLayout: GPUUploadLayout,
        bindingRequests: List<GPUImageBindingRequest>,
        preparationRequests: List<GPUResourcePreparationRequest>,
        memoryAllocations: List<GPUFrameMemoryAllocation>,
        artifact: GPUPreparedImageUploadArtifact,
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
        artifactKey = artifact.key,
        artifactWidth = artifact.width,
        artifactHeight = artifact.height,
        artifactContentHash = artifact.contentHash,
        snapshotMarker = GPUImageFrameResourcePlanSnapshot,
    )

    init {
        require(artifactWidth > 0 && artifactHeight > 0) {
            "Prepared-image artifact provenance dimensions must be positive"
        }
        require(artifactContentHash.isNotBlank()) {
            "Prepared-image artifact provenance hash must not be blank"
        }
    }

    override fun bytesForUpload(): ByteArray = uploadLayout.bytesForUpload()

    @Suppress("DataClassPrivateConstructor")
    fun copy(
        stagingRef: GPUFrameBufferRef = this.stagingRef,
        textureRef: GPUTextureResourceRef = this.textureRef,
        frameTextureRef: GPUFrameTextureRef = this.frameTextureRef,
        uniformRef: GPUFrameBufferRef = this.uniformRef,
        textureDescriptor: GPUTextureDescriptor = this.textureDescriptor,
        uploadLayout: GPUPreparedImageUploadLayout = this.uploadLayout,
        uploadTaskLayout: GPUUploadLayout = this.uploadTaskLayout,
        bindingRequests: List<GPUImageBindingRequest> = this.bindingRequests,
        preparationRequests: List<GPUResourcePreparationRequest> = this.preparationRequests,
        memoryAllocations: List<GPUFrameMemoryAllocation> = this.memoryAllocations,
    ): GPUImageFrameResourcePlan = GPUImageFrameResourcePlan(
        artifactKey = artifactKey,
        artifactWidth = artifactWidth,
        artifactHeight = artifactHeight,
        artifactContentHash = artifactContentHash,
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
        snapshotMarker = GPUImageFrameResourcePlanSnapshot,
    )

    operator fun component1() = stagingRef
    operator fun component2() = textureRef
    operator fun component3() = frameTextureRef
    operator fun component4() = uniformRef
    operator fun component5() = textureDescriptor
    operator fun component6() = uploadLayout
    operator fun component7() = uploadTaskLayout
    operator fun component8() = bindingRequests
    operator fun component9() = preparationRequests
    operator fun component10() = memoryAllocations
    operator fun component11() = artifactKey
    operator fun component12() = artifactWidth
    operator fun component13() = artifactHeight
    operator fun component14() = artifactContentHash

    override fun equals(other: Any?): Boolean =
        other is GPUImageFrameResourcePlan &&
            stagingRef == other.stagingRef &&
            textureRef == other.textureRef &&
            frameTextureRef == other.frameTextureRef &&
            uniformRef == other.uniformRef &&
            textureDescriptor == other.textureDescriptor &&
            uploadLayout == other.uploadLayout &&
            uploadTaskLayout == other.uploadTaskLayout &&
            bindingRequests == other.bindingRequests &&
            preparationRequests == other.preparationRequests &&
            memoryAllocations == other.memoryAllocations &&
            artifactKey == other.artifactKey &&
            artifactWidth == other.artifactWidth &&
            artifactHeight == other.artifactHeight &&
            artifactContentHash == other.artifactContentHash

    override fun hashCode(): Int {
        var result = stagingRef.hashCode()
        result = 31 * result + textureRef.hashCode()
        result = 31 * result + frameTextureRef.hashCode()
        result = 31 * result + uniformRef.hashCode()
        result = 31 * result + textureDescriptor.hashCode()
        result = 31 * result + uploadLayout.hashCode()
        result = 31 * result + uploadTaskLayout.hashCode()
        result = 31 * result + bindingRequests.hashCode()
        result = 31 * result + preparationRequests.hashCode()
        result = 31 * result + memoryAllocations.hashCode()
        result = 31 * result + artifactKey.hashCode()
        result = 31 * result + artifactWidth
        result = 31 * result + artifactHeight
        result = 31 * result + artifactContentHash.hashCode()
        return result
    }

    override fun toString(): String =
        "GPUImageFrameResourcePlan(artifactKey=$artifactKey, artifactWidth=$artifactWidth, " +
            "artifactHeight=$artifactHeight, artifactContentHash=$artifactContentHash, " +
            "stagingRef=$stagingRef, textureRef=$textureRef, " +
            "frameTextureRef=$frameTextureRef, uniformRef=$uniformRef, " +
            "textureDescriptor=$textureDescriptor, uploadLayout=$uploadLayout, " +
            "uploadTaskLayout=$uploadTaskLayout, bindingRequests=$bindingRequests, " +
            "preparationRequests=$preparationRequests, memoryAllocations=$memoryAllocations)"
}

internal fun buildImageFrameResourcePlanFromBindings(
    artifact: GPUPreparedImageUploadArtifact,
    bindingInputs: List<GPUImageBindingInput>,
    bindingLayoutHash: String,
    capabilities: GPUCapabilities,
    frameIdentity: String,
): GPUImageFrameResourcePlan {
    require(bindingInputs.isNotEmpty() &&
        bindingInputs.map(GPUImageBindingInput::packetId).distinct().size == bindingInputs.size
    ) {
        "Prepared-image packet IDs must be non-empty and unique"
    }
    require(bindingInputs.all { it.sampling == GPUPreparedImageSampling.Nearest }) {
        "${org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes.SAMPLING_FILTER}: " +
            "prepared-image resource plans support nearest sampling only"
    }
    require(bindingLayoutHash.isNotBlank()) { "bindingLayoutHash must not be blank" }
    require(frameIdentity.isNotBlank()) { "frameIdentity must not be blank" }
    require(artifact.colorInterpretation == GPUColorInterpretation.EncodedPremulSrgb.value) {
        "Prepared images must retain EncodedPremulSrgb interpretation"
    }
    val colorContract = preparedSdrColorContract()
    if (artifact.alphaOnly) {
        require(artifact.colorUploadEncoding == null &&
            artifact.colorUploadInterpretation == GPUColorInterpretation.LinearPremul.value
        ) {
            "Prepared A8 images must retain linear coverage upload interpretation"
        }
    } else {
        require(
            artifact.colorUploadEncoding ==
                GPUPreparedColorUploadEncoding.StraightEncodedSrgb &&
                artifact.colorUploadInterpretation ==
                GPUColorInterpretation.StraightEncodedSrgb.value,
        ) {
            "Prepared color images must retain straight encoded sRGB upload interpretation"
        }
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

    val sourceTextureFormat = if (artifact.alphaOnly) {
        colorContract.coverageSourceTextureFormat
    } else {
        colorContract.colorSourceTextureFormat
    }
    val sourceTextureFormatLabel = when (sourceTextureFormat) {
        io.ygdrasil.webgpu.GPUTextureFormat.RGBA8Unorm -> "RGBA8Unorm"
        io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
        else -> error("Prepared-image SDR source contract selected unsupported texture format")
    }
    val sourceFrameColorFormat = when (sourceTextureFormat) {
        io.ygdrasil.webgpu.GPUTextureFormat.RGBA8Unorm -> GPUColorFormat.RGBA8Unorm
        io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb -> GPUColorFormat.RGBA8UnormSrgb
    }
    val textureDescriptor = GPUTextureDescriptor(
        width = artifact.width,
        height = artifact.height,
        format = sourceTextureFormatLabel,
        usageLabels = setOf("copy_dst", "texture_binding"),
    )
    val view = GPUTextureViewDescriptor(
        textureDescriptorHash = textureDescriptor.preparedImageDescriptorHash(),
        viewDimension = "2d",
        mipRange = 0..0,
        arrayLayerRange = 0..0,
    )
    val uniformSize = GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES
    val uniformStride = alignUp(uniformSize, limits.minUniformBufferOffsetAlignment)
    val bindingRequests = bindingInputs.mapIndexed { index, bindingInput ->
        val filter = "nearest"
        GPUImageBindingRequest(
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
                format = sourceFrameColorFormat,
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
    return GPUImageFrameResourcePlan(
        artifact = artifact,
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
    )
}

internal fun buildPreparedImageFrameResourcePlan(
    artifact: GPUPreparedImageUploadArtifact,
    packetIds: List<String>,
    bindingLayoutHash: String,
    capabilities: GPUCapabilities,
    frameIdentity: String,
): GPUImageFrameResourcePlan = buildImageFrameResourcePlanFromBindings(
    artifact = artifact,
    bindingInputs = packetIds.map { packetId ->
        GPUImageBindingInput(packetId, GPUPreparedImageSampling.Nearest)
    },
    bindingLayoutHash = bindingLayoutHash,
    capabilities = capabilities,
    frameIdentity = frameIdentity,
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

private fun GPUImageBindingRequest.snapshot(): GPUImageBindingRequest = copy(
    texture = texture.snapshot(),
    view = view.copy(
        mipRange = view.mipRange.first..view.mipRange.last,
        arrayLayerRange = view.arrayLayerRange.first..view.arrayLayerRange.last,
    ),
    sampler = sampler.snapshot(),
    uniformAllocation = uniformAllocation.copy(),
)

private fun List<GPUImageBindingRequest>.snapshotPreparedImageBindings():
    List<GPUImageBindingRequest> = immutableList(
        map(GPUImageBindingRequest::snapshot),
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

internal fun GPUTextureDescriptor.preparedImageDescriptorHash(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(
            listOf(
                "texture",
                width.toString(),
                height.toString(),
                format,
                sampleCount.toString(),
                usageLabels.sorted().joinToString("+"),
            ).joinToString("\u0000").encodeToByteArray(),
        )
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
