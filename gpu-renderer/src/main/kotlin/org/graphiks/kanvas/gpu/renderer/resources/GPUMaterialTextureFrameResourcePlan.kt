package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

private const val MATERIAL_TEXTURE_COPY_BYTES_PER_ROW_ALIGNMENT = 256L

/** Immutable frame-local upload plan for one prepared-material sampled RGBA resource. */
class GPUMaterialTextureFrameResourcePlan private constructor(
    override val stagingRef: GPUFrameBufferRef,
    override val frameTextureRef: GPUFrameTextureRef,
    override val uploadTaskLayout: GPUUploadLayout,
    override val preparationRequests: List<GPUResourcePreparationRequest>,
    override val memoryAllocations: List<GPUFrameMemoryAllocation>,
    val resourceKey: String,
    val width: Int,
    val height: Int,
    val samplingFilterMode: String,
    val alphaOnly: Boolean,
    val contentHash: String,
    uploadBytes: ByteArray,
) : GPUTextureFrameResourcePlan {
    private val uploadSnapshot = uploadBytes.copyOf()

    init {
        require(uploadSnapshot.size.toLong() == uploadTaskLayout.byteSize)
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
    }

    override fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()

    internal companion object {
        fun create(
            resourceKey: String,
            width: Int,
            height: Int,
            samplingFilterMode: String,
            alphaOnly: Boolean,
            contentHash: String,
            rgba8Bytes: ByteArray,
            capabilities: GPUCapabilities,
            frameIdentity: String,
        ): GPUMaterialTextureFrameResourcePlan {
            require(frameIdentity.isNotBlank())
            val limits = requireNotNull(capabilities.limits) {
                "Prepared-material resource planning requires observed device limits"
            }
            require(
                width.toLong() <= limits.maxTextureDimension2D &&
                    height.toLong() <= limits.maxTextureDimension2D,
            ) {
                "Prepared-material sampled resource exceeds maxTextureDimension2D"
            }
            val logicalBytesPerRow = Math.multiplyExact(width.toLong(), 4L)
            val rowAlignment = leastCommonMultipleMaterialTexture(
                MATERIAL_TEXTURE_COPY_BYTES_PER_ROW_ALIGNMENT,
                limits.copyBytesPerRowAlignment,
            )
            val stagingBytesPerRow = alignUpMaterialTexture(logicalBytesPerRow, rowAlignment)
            val stagingByteSize = Math.multiplyExact(
                stagingBytesPerRow,
                height.toLong(),
            )
            require(stagingByteSize <= Int.MAX_VALUE) {
                "Prepared-material sampled upload exceeds JVM byte-array capacity"
            }
            require(limits.maxBufferSize == null || stagingByteSize <= limits.maxBufferSize) {
                "Prepared-material sampled upload exceeds maxBufferSize"
            }
            val sourceBytes = rgba8Bytes.copyOf()
            require(
                sourceBytes.size.toLong() ==
                    Math.multiplyExact(logicalBytesPerRow, height.toLong()),
            ) {
                "Prepared-material sampled bytes must contain exactly width * height * 4 bytes"
            }
            require(sha256MaterialTexture(sourceBytes) == contentHash) {
                "Prepared-material sampled bytes must match their content hash"
            }
            val stagingBytes = GPUMaterialTextureUploadV1.of(width, height, 4, sourceBytes,
                rowAlignment, limits.maxBufferSize ?: Long.MAX_VALUE).copyBytes()
            val identity = sha256MaterialTexture(
                listOf(
                    "prepared-material-texture:v1",
                    frameIdentity,
                    resourceKey,
                    contentHash,
                    width.toString(),
                    height.toString(),
                    alphaOnly.toString(),
                ).joinToString("\u0000").encodeToByteArray(),
            )
            val stagingRef = GPUFrameBufferRef("prepared-material-staging:$identity")
            val textureRef = GPUFrameTextureRef("prepared-material-texture:$identity")
            val bounds = GPUPixelBounds(0, 0, width, height)
            val textureBytes = Math.multiplyExact(logicalBytesPerRow, height.toLong())
            val preparations = immutableList(
                listOf(
                    GPUResourcePreparationRequest(
                        resource = stagingRef,
                        descriptor = GPUFrameBufferDescriptor(stagingByteSize, rowAlignment),
                        role = GPUFrameResourceRole.UploadStaging,
                        usages = setOf(GPUFrameResourceUsage.CopySource),
                        lifetime = GPUFrameResourceLifetime.FrameLocal,
                        byteSize = stagingByteSize,
                        diagnosticLabel = "prepared-material.upload-staging.$identity",
                    ),
                    GPUResourcePreparationRequest(
                        resource = textureRef,
                        descriptor = GPUFrameTextureDescriptor(
                            logicalBounds = bounds,
                            format = if (alphaOnly) {
                                GPUColorFormat.RGBA8Unorm
                            } else {
                                GPUColorFormat.RGBA8UnormSrgb
                            },
                            sampleCount = 1,
                        ),
                        role = GPUFrameResourceRole.StorageData,
                        usages = setOf(
                            GPUFrameResourceUsage.CopyDestination,
                            GPUFrameResourceUsage.TextureBinding,
                        ),
                        lifetime = GPUFrameResourceLifetime.FrameLocal,
                        byteSize = textureBytes,
                        diagnosticLabel = "prepared-material.texture.$identity",
                    ),
                ),
            )
            val allocations = immutableList(
                listOf(
                    GPUFrameMemoryAllocation(
                        label = "prepared-material.staging.$identity",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = stagingByteSize,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                    GPUFrameMemoryAllocation(
                        label = "prepared-material.texture.$identity",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = textureBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = bounds,
                    ),
                ),
            )
            return GPUMaterialTextureFrameResourcePlan(
                stagingRef = stagingRef,
                frameTextureRef = textureRef,
                uploadTaskLayout = GPUUploadLayout(
                    sourceOffsetBytes = 0L,
                    bytesPerRow = stagingBytesPerRow,
                    rowsPerImage = height,
                    byteSize = stagingByteSize,
                ),
                preparationRequests = preparations,
                memoryAllocations = allocations,
                resourceKey = resourceKey,
                width = width,
                height = height,
                samplingFilterMode = samplingFilterMode,
                alphaOnly = alphaOnly,
                contentHash = contentHash,
                uploadBytes = stagingBytes,
            )
        }
    }
}

internal fun buildMaterialTextureFrameResourcePlan(
    resourceKey: String,
    width: Int,
    height: Int,
    samplingFilterMode: String,
    alphaOnly: Boolean,
    contentHash: String,
    rgba8Bytes: ByteArray,
    capabilities: GPUCapabilities,
    frameIdentity: String,
): GPUMaterialTextureFrameResourcePlan =
    GPUMaterialTextureFrameResourcePlan.create(
        resourceKey = resourceKey,
        width = width,
        height = height,
        samplingFilterMode = samplingFilterMode,
        alphaOnly = alphaOnly,
        contentHash = contentHash,
        rgba8Bytes = rgba8Bytes,
        capabilities = capabilities,
        frameIdentity = frameIdentity,
    )

private fun alignUpMaterialTexture(value: Long, alignment: Long): Long {
    require(value >= 0L && alignment > 0L)
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

private fun leastCommonMultipleMaterialTexture(first: Long, second: Long): Long =
    Math.multiplyExact(
        first / greatestCommonDivisorMaterialTexture(first, second),
        second,
    )

private fun greatestCommonDivisorMaterialTexture(first: Long, second: Long): Long {
    var left = first
    var right = second
    while (right != 0L) {
        val remainder = left % right
        left = right
        right = remainder
    }
    return left
}

private fun sha256MaterialTexture(bytes: ByteArray): String =
    buildString(64) {
        MessageDigest.getInstance("SHA-256").digest(bytes).forEach { byte ->
            val value = byte.toInt() and 0xff
            append(MATERIAL_TEXTURE_HEX[value ushr 4])
            append(MATERIAL_TEXTURE_HEX[value and 0x0f])
        }
    }

private const val MATERIAL_TEXTURE_HEX = "0123456789abcdef"

/** Format-neutral physical staging shared by prepared materials and W5e session uploads. */
internal class GPUMaterialTextureUploadV1 private constructor(
    val widthI32: Int, val heightI32: Int, val bytesPerPixelI32: Int,
    val bytesPerRowI64: Long, bytes: ByteArray,
) {
    private val bytes = bytes.copyOf()
    val byteSizeI64: Long get() = bytes.size.toLong()
    fun copyBytes(): ByteArray = bytes.copyOf()
    companion object {
        fun of(widthI32: Int, heightI32: Int, bytesPerPixelI32: Int, logicalBytes: ByteArray,
            rowAlignmentI64: Long, maxBufferBytesI64: Long): GPUMaterialTextureUploadV1 {
            require(widthI32 > 0 && heightI32 > 0 && bytesPerPixelI32 in setOf(1, 4))
            val logicalRowI64 = Math.multiplyExact(widthI32.toLong(), bytesPerPixelI32.toLong())
            require(logicalBytes.size.toLong() == Math.multiplyExact(logicalRowI64, heightI32.toLong()))
            val alignmentI64 = leastCommonMultipleMaterialTexture(256L, rowAlignmentI64)
            val rowI64 = alignUpMaterialTexture(logicalRowI64, alignmentI64)
            val sizeI64 = Math.multiplyExact(rowI64, heightI32.toLong())
            require(sizeI64 <= Int.MAX_VALUE && sizeI64 <= maxBufferBytesI64)
            return planned(widthI32, heightI32, bytesPerPixelI32, logicalBytes, rowI64, sizeI64)
        }

        /** Copies bytes into the exact pre-publication W6 allocation; never chooses padding or size. */
        fun planned(widthI32: Int, heightI32: Int, bytesPerPixelI32: Int, logicalBytes: ByteArray,
            rowI64: Long, sizeI64: Long): GPUMaterialTextureUploadV1 {
            val logicalRowI64 = Math.multiplyExact(widthI32.toLong(), bytesPerPixelI32.toLong())
            require(logicalBytes.size.toLong() == Math.multiplyExact(logicalRowI64, heightI32.toLong()) &&
                rowI64 >= logicalRowI64 && sizeI64 == Math.multiplyExact(rowI64, heightI32.toLong()) && sizeI64 <= Int.MAX_VALUE)
            val padded = ByteArray(sizeI64.toInt())
            for (rowI32 in 0 until heightI32) logicalBytes.copyInto(padded,
                Math.toIntExact(rowI32 * rowI64), Math.toIntExact(rowI32 * logicalRowI64), Math.toIntExact((rowI32 + 1L) * logicalRowI64))
            return GPUMaterialTextureUploadV1(widthI32, heightI32, bytesPerPixelI32, rowI64, padded)
        }
    }
}
