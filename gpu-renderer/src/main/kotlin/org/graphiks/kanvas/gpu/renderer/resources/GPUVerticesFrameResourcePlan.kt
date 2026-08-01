package org.graphiks.kanvas.gpu.renderer.vertices

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.vertices.GPUIndexBufferPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexBufferPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan

/** Frame-owned, handle-free resource plan for one unique prepared vertices artifact. */
data class GPUVerticesFrameResourcePlan(
    val artifactKey: String,
    val vertexBuffer: GPUVertexBufferPlan,
    val indexBuffer: GPUIndexBufferPlan?,
    val uploadBeforeUseToken: String,
    val ownerScope: String = PREPARED_VERTICES_OWNER_SCOPE,
) {
    init {
        require(artifactKey.isNotBlank()) {
            "GPUVerticesFrameResourcePlan.artifactKey must not be blank"
        }
        require(uploadBeforeUseToken.isNotBlank()) {
            "GPUVerticesFrameResourcePlan.uploadBeforeUseToken must not be blank"
        }
        require(ownerScope.isNotBlank()) {
            "GPUVerticesFrameResourcePlan.ownerScope must not be blank"
        }
        require(vertexBuffer.byteCount > 0L) {
            "GPUVerticesFrameResourcePlan.vertexBuffer.byteCount must be positive"
        }
        require(vertexBuffer.alignment > 0) {
            "GPUVerticesFrameResourcePlan.vertexBuffer.alignment must be positive"
        }
        indexBuffer?.let { index ->
            require(index.byteCount > 0L) {
                "GPUVerticesFrameResourcePlan.indexBuffer.byteCount must be positive"
            }
            require(index.alignment > 0) {
                "GPUVerticesFrameResourcePlan.indexBuffer.alignment must be positive"
            }
            require(index.indexFormat == "uint16" || index.indexFormat == "uint32") {
                "GPUVerticesFrameResourcePlan.indexBuffer.indexFormat must be uint16 or uint32"
            }
        }
    }

    /** Exact aligned vertex byte range inside the frame staging buffer. */
    val vertexByteRange: LongRange
        get() = 0L until vertexBuffer.byteCount

    /** Exact aligned index byte range inside the frame staging buffer, when indexed. */
    val indexByteRange: LongRange?
        get() = indexBuffer?.let { index ->
            vertexBuffer.byteCount until checkedVerticesByteAdd(vertexBuffer.byteCount, index.byteCount)
        }

    /** Total exact byte count contributed by this artifact. */
    val totalByteCount: Long
        get() = checkedVerticesByteAdd(vertexBuffer.byteCount, indexBuffer?.byteCount ?: 0L)

    /** Invalidation facts retained for generation-aware frame evidence. */
    val invalidationFacts: List<String> = listOf(
        "device-generation:${vertexBuffer.deviceGeneration}",
        "buffer-generation:${vertexBuffer.bufferGeneration}",
    ) + (indexBuffer?.let { index ->
        listOf(
            "index-device-generation:${index.deviceGeneration}",
            "index-buffer-generation:${index.bufferGeneration}",
        )
    } ?: emptyList())
}

/** One checked byte range inside the shared frame vertices staging buffer. */
data class GPUVerticesStagingRange(
    val artifactKey: String,
    val bufferKind: String,
    val offsetBytes: Long,
    val byteCount: Long,
) {
    init {
        require(artifactKey.isNotBlank()) {
            "GPUVerticesStagingRange.artifactKey must not be blank"
        }
        require(bufferKind == "vertex" || bufferKind == "index") {
            "GPUVerticesStagingRange.bufferKind must be vertex or index"
        }
        require(offsetBytes >= 0L) {
            "GPUVerticesStagingRange.offsetBytes must not be negative"
        }
        require(byteCount > 0L) {
            "GPUVerticesStagingRange.byteCount must be positive"
        }
    }
}

/** Checked frame-owned layout of every prepared-vertices staging range. */
data class GPUVerticesStagingLayout(
    val totalBytes: Long,
    val ranges: List<GPUVerticesStagingRange>,
) {
    init {
        require(totalBytes > 0L) { "GPUVerticesStagingLayout.totalBytes must be positive" }
        require(ranges.isNotEmpty()) { "GPUVerticesStagingLayout.ranges must not be empty" }
        ranges.zipWithNext().forEach { (left, right) ->
            require(checkedVerticesByteAdd(left.offsetBytes, left.byteCount) <= right.offsetBytes) {
                "GPUVerticesStagingLayout ranges must not overlap"
            }
        }
        require(
            checkedVerticesByteAdd(ranges.last().offsetBytes, ranges.last().byteCount) <= totalBytes,
        ) {
            "GPUVerticesStagingLayout ranges must fit the total byte count"
        }
    }
}

/**
 * Derives the frame-owned resource plan for one unique prepared vertices artifact.
 *
 * The plan is derived per unique artifact and deduplicates by artifact key: identical
 * immutable artifacts yield identical plans, and byte counts use checked arithmetic.
 */
fun buildVerticesFrameResourcePlan(
    artifact: GPUPreparedVerticesUploadArtifact,
    deviceGeneration: Long,
    bufferGeneration: Long = 0L,
): GPUVerticesFrameResourcePlan {
    require(deviceGeneration >= 0L) {
        "Prepared vertices device generation must not be negative"
    }
    require(bufferGeneration >= 0L) {
        "Prepared vertices buffer generation must not be negative"
    }
    val vertexBytes = checkedVerticesByteCount(artifact.vertexCount, artifact.layout.strideBytes)
    val indexBuffer = artifact.indexCount?.let { count ->
        val indexFormat = requireNotNull(artifact.indexFormat)
        val elementBytes = if (indexFormat == "uint16") 2 else 4
        GPUIndexBufferPlan(
            indexFormat = indexFormat,
            count = count,
            validationLabel = "prepared-vertices-index:$count",
            sourceDescriptorHash = artifact.key,
            sourceIndexContentHash = requireNotNull(artifact.indexContentHash),
            byteCount = checkedVerticesByteCount(count, elementBytes),
            alignment = PREPARED_VERTICES_BUFFER_ALIGNMENT,
            usageFlags = listOf("copy_dst", "index"),
            ownerScope = PREPARED_VERTICES_OWNER_SCOPE,
            uploadStagingScope = PREPARED_VERTICES_UPLOAD_STAGING_SCOPE,
            uploadRequirement = "upload-before-draw",
            deviceGeneration = deviceGeneration,
            bufferGeneration = bufferGeneration,
            materialKey = false,
        )
    }
    return GPUVerticesFrameResourcePlan(
        artifactKey = artifact.key,
        vertexBuffer = GPUVertexBufferPlan(
            byteCount = vertexBytes,
            layout = artifact.layout,
            uploadRequirement = "upload-before-draw",
            sourceDescriptorHash = artifact.key,
            sourceVertexContentHash = artifact.vertexContentHash,
            layoutHash = verticesLayoutHash(artifact.layout),
            alignment = PREPARED_VERTICES_BUFFER_ALIGNMENT,
            usageFlags = listOf("copy_dst", "vertex"),
            ownerScope = PREPARED_VERTICES_OWNER_SCOPE,
            uploadStagingScope = PREPARED_VERTICES_UPLOAD_STAGING_SCOPE,
            deviceGeneration = deviceGeneration,
            bufferGeneration = bufferGeneration,
            materialKey = false,
        ),
        indexBuffer = indexBuffer,
        uploadBeforeUseToken = "prepared-vertices.upload-before-consumer:${artifact.key}",
        ownerScope = PREPARED_VERTICES_OWNER_SCOPE,
    )
}

/**
 * Builds aligned, non-overlapping staging ranges across every unique artifact plan.
 *
 * Ranges are laid out in plan order with the shared alignment; offsets use checked
 * arithmetic so overflowing byte accounting is rejected before any task is emitted.
 */
fun buildVerticesStagingLayout(
    plans: List<GPUVerticesFrameResourcePlan>,
    alignmentBytes: Long = PREPARED_VERTICES_BUFFER_ALIGNMENT.toLong(),
): GPUVerticesStagingLayout {
    require(alignmentBytes > 0L) { "Prepared vertices staging alignment must be positive" }
    var cursor = 0L
    val ranges = mutableListOf<GPUVerticesStagingRange>()
    plans.forEach { plan ->
        val vertexOffset = alignVerticesUp(cursor, alignmentBytes)
        ranges += GPUVerticesStagingRange(
            artifactKey = plan.artifactKey,
            bufferKind = "vertex",
            offsetBytes = vertexOffset,
            byteCount = plan.vertexBuffer.byteCount,
        )
        val vertexEnd = checkedVerticesByteAdd(vertexOffset, plan.vertexBuffer.byteCount)
        plan.indexBuffer?.let { index ->
            val indexOffset = alignVerticesUp(vertexEnd, alignmentBytes)
            ranges += GPUVerticesStagingRange(
                artifactKey = plan.artifactKey,
                bufferKind = "index",
                offsetBytes = indexOffset,
                byteCount = index.byteCount,
            )
            cursor = checkedVerticesByteAdd(indexOffset, index.byteCount)
        } ?: run { cursor = vertexEnd }
    }
    return GPUVerticesStagingLayout(totalBytes = cursor, ranges = immutableList(ranges))
}

internal const val PREPARED_VERTICES_BUFFER_ALIGNMENT = 4
private const val PREPARED_VERTICES_OWNER_SCOPE = "PayloadOwnedCompletion"
private const val PREPARED_VERTICES_UPLOAD_STAGING_SCOPE = "FrameUploadStaging"

private fun checkedVerticesByteCount(count: Int, elementBytes: Int): Long = try {
    Math.multiplyExact(count.toLong(), elementBytes.toLong())
} catch (failure: ArithmeticException) {
    throw IllegalArgumentException("Prepared vertices byte count overflowed", failure)
}

private fun checkedVerticesByteAdd(left: Long, right: Long): Long = try {
    Math.addExact(left, right)
} catch (failure: ArithmeticException) {
    throw IllegalArgumentException("Prepared vertices byte accounting overflowed", failure)
}

private fun alignVerticesUp(value: Long, alignment: Long): Long {
    require(value >= 0L && alignment > 0L)
    val remainder = value % alignment
    return if (remainder == 0L) value else checkedVerticesByteAdd(value, alignment - remainder)
}

private fun verticesLayoutHash(layout: GPUVertexLayoutPlan): String =
    MessageDigest.getInstance("SHA-256")
        .digest(
            listOf(
                "prepared-vertices-layout-v1",
                layout.attributes.joinToString(","),
                layout.strideBytes.toString(),
                layout.offsets.toSortedMap().map { (name, offset) -> "$name=$offset" }
                    .joinToString(","),
                layout.shaderLocations.toSortedMap().map { (name, location) -> "$name=$location" }
                    .joinToString(","),
            ).joinToString("\u0000").encodeToByteArray(),
        )
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
