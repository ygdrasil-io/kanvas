package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

private const val WEBGPU_TEXTURE_COPY_BYTES_PER_ROW_ALIGNMENT = 256L

/** Complete immutable identity shared by R8 recording, resource plans, and preflight. */
internal data class GPUR8ArtifactIdentity(
    val key: String,
    val generation: Long,
    val contentHash: String,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
)

internal val GPUPreparedR8UploadArtifact.r8ArtifactIdentity: GPUR8ArtifactIdentity
    get() = GPUR8ArtifactIdentity(
        key = key,
        generation = generation,
        contentHash = contentHash,
        width = width,
        height = height,
        rowBytes = rowBytes,
    )

/** Immutable handle-free staging and texture plan for one R8 artifact. */
class GPUR8FrameResourcePlan private constructor(
    override val stagingRef: GPUFrameBufferRef,
    override val frameTextureRef: GPUFrameTextureRef,
    override val uploadTaskLayout: GPUUploadLayout,
    preparationRequests: List<GPUResourcePreparationRequest>,
    memoryAllocations: List<GPUFrameMemoryAllocation>,
    val artifactKey: String,
    val artifactWidth: Int,
    val artifactHeight: Int,
    val artifactRowBytes: Int,
    val artifactGeneration: Long,
    val artifactContentHash: String,
    uploadBytes: ByteArray,
) : GPUTextureFrameResourcePlan {
    override val preparationRequests: List<GPUResourcePreparationRequest> =
        immutableList(preparationRequests.map(GPUResourcePreparationRequest::snapshotForR8))
    override val memoryAllocations: List<GPUFrameMemoryAllocation> =
        immutableList(memoryAllocations.map(GPUFrameMemoryAllocation::copy))
    private val uploadSnapshot = uploadBytes.copyOf()

    init {
        require(uploadSnapshot.size.toLong() == uploadTaskLayout.byteSize) {
            "R8 staging bytes must match the exact upload layout"
        }
    }

    override fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()

    internal val r8ArtifactIdentity: GPUR8ArtifactIdentity
        get() = GPUR8ArtifactIdentity(
            key = artifactKey,
            generation = artifactGeneration,
            contentHash = artifactContentHash,
            width = artifactWidth,
            height = artifactHeight,
            rowBytes = artifactRowBytes,
        )

    internal companion object {
        fun create(
            artifact: GPUPreparedR8UploadArtifact,
            capabilities: GPUCapabilities,
            frameIdentity: String,
        ): GPUR8FrameResourcePlan {
            require(frameIdentity.isNotBlank()) { "frameIdentity must not be blank" }
            val limits = requireNotNull(capabilities.limits) {
                "R8 resource planning requires observed device limits"
            }
            require(
                artifact.width.toLong() <= limits.maxTextureDimension2D &&
                    artifact.height.toLong() <= limits.maxTextureDimension2D,
            ) {
                "R8 texture exceeds the observed maxTextureDimension2D limit"
            }

            val logicalBytesPerRow = artifact.width.toLong()
            val requiredCopyBytesPerRowAlignment = leastCommonMultipleR8(
                WEBGPU_TEXTURE_COPY_BYTES_PER_ROW_ALIGNMENT,
                limits.copyBytesPerRowAlignment,
            )
            val stagingBytesPerRow = alignUpR8(
                logicalBytesPerRow,
                requiredCopyBytesPerRowAlignment,
            )
            val stagingByteSize = Math.multiplyExact(
                stagingBytesPerRow,
                artifact.height.toLong(),
            )
            require(stagingByteSize <= Int.MAX_VALUE) {
                "R8 staging upload exceeds JVM byte-array capacity"
            }
            require(limits.maxBufferSize == null || stagingByteSize <= limits.maxBufferSize) {
                "R8 staging upload exceeds the observed buffer-size limit"
            }
            val textureByteSize = Math.multiplyExact(
                artifact.width.toLong(),
                artifact.height.toLong(),
            )

            // Limit and overflow checks above intentionally precede both source
            // snapshot retrieval and staging allocation.
            val sourceBytes = artifact.tightBytesForUpload()
            val expectedSourceByteSize = Math.multiplyExact(
                artifact.rowBytes.toLong(),
                artifact.height.toLong(),
            )
            require(sourceBytes.size.toLong() == expectedSourceByteSize) {
                "R8 artifact bytes no longer match their exact source row layout"
            }
            val stagingBytes = ByteArray(stagingByteSize.toInt())
            repeat(artifact.height) { row ->
                val sourceOffset = Math.multiplyExact(
                    row.toLong(),
                    artifact.rowBytes.toLong(),
                ).toInt()
                val destinationOffset = Math.multiplyExact(
                    row.toLong(),
                    stagingBytesPerRow,
                ).toInt()
                sourceBytes.copyInto(
                    destination = stagingBytes,
                    destinationOffset = destinationOffset,
                    startIndex = sourceOffset,
                    endIndex = Math.addExact(sourceOffset, artifact.width),
                )
            }

            val resourceIdentity =
                listOf(
                    "prepared-r8-resource:v1",
                    frameIdentity,
                    artifact.key,
                    artifact.generation.toString(),
                    artifact.contentHash,
                    artifact.width.toString(),
                    artifact.height.toString(),
                    artifact.rowBytes.toString(),
                ).joinToString(separator = "\u0000")
                    .encodeToByteArray()
                    .sha256()
            val stagingRef = GPUFrameBufferRef("prepared-r8-staging:$resourceIdentity")
            val frameTextureRef = GPUFrameTextureRef("prepared-r8-texture:$resourceIdentity")
            val textureBounds = GPUPixelBounds(0, 0, artifact.width, artifact.height)
            val preparationRequests = listOf(
                GPUResourcePreparationRequest(
                    resource = stagingRef,
                    descriptor = GPUFrameBufferDescriptor(
                        byteSize = stagingByteSize,
                        alignmentBytes = requiredCopyBytesPerRowAlignment,
                    ),
                    role = GPUFrameResourceRole.UploadStaging,
                    usages = setOf(GPUFrameResourceUsage.CopySource),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = stagingByteSize,
                    diagnosticLabel = "prepared-r8.upload-staging.${frameTextureRef.value}",
                ),
                GPUResourcePreparationRequest(
                    resource = frameTextureRef,
                    descriptor = GPUFrameTextureDescriptor(
                        logicalBounds = textureBounds,
                        format = GPUColorFormat("r8unorm"),
                        sampleCount = 1,
                    ),
                    role = GPUFrameResourceRole.GlyphAtlas,
                    usages = setOf(
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.TextureBinding,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = textureByteSize,
                    diagnosticLabel = "prepared-r8.texture.${frameTextureRef.value}",
                ),
            )
            return GPUR8FrameResourcePlan(
                stagingRef = stagingRef,
                frameTextureRef = frameTextureRef,
                uploadTaskLayout = GPUUploadLayout(
                    sourceOffsetBytes = 0L,
                    bytesPerRow = stagingBytesPerRow,
                    rowsPerImage = artifact.height,
                    byteSize = stagingByteSize,
                ),
                preparationRequests = preparationRequests,
                memoryAllocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "prepared-r8.staging.$resourceIdentity",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = stagingByteSize,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                    GPUFrameMemoryAllocation(
                        label = "prepared-r8.texture.$resourceIdentity",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = textureByteSize,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = textureBounds,
                    ),
                ),
                artifactKey = artifact.key,
                artifactWidth = artifact.width,
                artifactHeight = artifact.height,
                artifactRowBytes = artifact.rowBytes,
                artifactGeneration = artifact.generation,
                artifactContentHash = artifact.contentHash,
                uploadBytes = stagingBytes,
            )
        }
    }
}

internal fun buildR8FrameResourcePlan(
    artifact: GPUPreparedR8UploadArtifact,
    capabilities: GPUCapabilities,
    frameIdentity: String,
): GPUR8FrameResourcePlan = GPUR8FrameResourcePlan.create(
    artifact = artifact,
    capabilities = capabilities,
    frameIdentity = frameIdentity,
)

private fun alignUpR8(value: Long, alignment: Long): Long {
    require(value >= 0L && alignment > 0L)
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

private fun leastCommonMultipleR8(first: Long, second: Long): Long {
    require(first > 0L && second > 0L)
    return Math.multiplyExact(first / greatestCommonDivisorR8(first, second), second)
}

private fun greatestCommonDivisorR8(first: Long, second: Long): Long {
    var left = first
    var right = second
    while (right != 0L) {
        val remainder = left % right
        left = right
        right = remainder
    }
    return left
}

private fun GPUResourcePreparationRequest.snapshotForR8(): GPUResourcePreparationRequest =
    GPUResourcePreparationRequest(
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

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
