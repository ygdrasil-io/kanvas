package org.graphiks.kanvas.gpu.renderer.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind

data class GPUPreparedTextDrawUniformSlice(
    val packetId: GPUDrawPacketID,
    val offsetBytes: Long,
    val sizeBytes: Long,
    val contentHash: String,
) {
    init {
        require(offsetBytes >= 0L)
        require(sizeBytes == PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES)
        require(contentHash.isNotBlank())
    }
}

class GPUPreparedTextDrawUniformBufferPlan(
    val bufferRef: GPUFrameBufferRef,
    val alignmentBytes: Long,
    val logicalSliceSizeBytes: Long,
    val byteSize: Long,
    val contentHash: String,
    slices: List<GPUPreparedTextDrawUniformSlice>,
    uploadBytes: ByteArray,
) {
    private val uploadSnapshot = uploadBytes.copyOf()
    val memoryAllocation = GPUFrameMemoryAllocation(
        label = "prepared-text.draw-uniforms.$contentHash",
        category = GPUFrameMemoryCategory.ReusableScratch,
        bytes = byteSize,
        resourceKind = GPUFrameMemoryResourceKind.Buffer,
        extent = null,
    )
    val slices: List<GPUPreparedTextDrawUniformSlice> =
        immutableList(slices.map(GPUPreparedTextDrawUniformSlice::copy))

    init {
        require(alignmentBytes > 0L && alignmentBytes and (alignmentBytes - 1L) == 0L)
        require(logicalSliceSizeBytes == PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES)
        require(byteSize > 0L && byteSize == uploadSnapshot.size.toLong())
        require(contentHash == uploadSnapshot.preparedTextSha256())
        require(this.slices.isNotEmpty())
        require(this.slices.map { it.packetId }.distinct().size == this.slices.size)
        val strideBytes = try {
            alignUpPreparedTextDrawUniform(logicalSliceSizeBytes, alignmentBytes)
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Prepared text draw-uniform stride overflowed.", error)
        }
        this.slices.forEachIndexed { index, slice ->
            val expectedOffset = try {
                Math.multiplyExact(strideBytes, index.toLong())
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException(
                    "Prepared text draw-uniform slice offset overflowed.",
                    error,
                )
            }
            require(slice.offsetBytes == expectedOffset) {
                "Prepared text draw-uniform slice[$index] offset ${slice.offsetBytes} " +
                    "must equal canonical offset $expectedOffset."
            }
        }
        val expectedByteSize = try {
            Math.multiplyExact(strideBytes, this.slices.size.toLong())
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Prepared text draw-uniform byte size overflowed.", error)
        }
        require(byteSize == expectedByteSize)
        this.slices.forEach { slice ->
            require(slice.offsetBytes % alignmentBytes == 0L)
            val end = Math.addExact(slice.offsetBytes, slice.sizeBytes)
            require(end <= byteSize)
            require(
                slice.contentHash ==
                    uploadSnapshot.copyOfRange(slice.offsetBytes.toInt(), end.toInt())
                        .preparedTextSha256(),
            )
            val strideEnd = Math.addExact(slice.offsetBytes, strideBytes)
            require(
                (end.toInt() until strideEnd.toInt()).all { paddingIndex ->
                    uploadSnapshot[paddingIndex] == 0.toByte()
                },
            )
        }
    }

    fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()
}

internal data class GPUPreparedTextDrawUniformInput(
    val packetId: GPUDrawPacketID,
    val semantic: GPUDrawSemanticPayload.TextA8,
)

internal sealed interface GPUPreparedTextDrawUniformPlanResult {
    data class Prepared(
        val plan: GPUPreparedTextDrawUniformBufferPlan,
        val slicesByPacketId: Map<GPUDrawPacketID, GPUPreparedTextDrawUniformSlice>,
    ) : GPUPreparedTextDrawUniformPlanResult

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedTextDrawUniformPlanResult
}

internal fun buildPreparedTextDrawUniformBufferPlan(
    inputs: List<GPUPreparedTextDrawUniformInput>,
    frameIdentity: String,
    alignmentBytes: Long,
    maxBufferSize: Long?,
): GPUPreparedTextDrawUniformPlanResult {
    if (
        inputs.isEmpty() ||
        frameIdentity.isBlank() ||
        alignmentBytes <= 0L ||
        alignmentBytes and (alignmentBytes - 1L) != 0L
    ) {
        return GPUPreparedTextDrawUniformPlanResult.Refused(
            code = "invalid.recording.prepared_text_draw_uniform_alignment",
            message = "Prepared text draw uniforms require one observed power-of-two alignment.",
        )
    }
    val strideBytes = try {
        alignUpPreparedTextDrawUniform(
            PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
            alignmentBytes,
        )
    } catch (_: ArithmeticException) {
        return GPUPreparedTextDrawUniformPlanResult.Refused(
            code = "unsupported.recording.prepared_text_draw_uniform_buffer",
            message = "Prepared text draw-uniform stride overflowed.",
        )
    }
    val byteSize = try {
        Math.multiplyExact(strideBytes, inputs.size.toLong())
    } catch (_: ArithmeticException) {
        return GPUPreparedTextDrawUniformPlanResult.Refused(
            code = "unsupported.recording.prepared_text_draw_uniform_buffer",
            message = "Prepared text draw-uniform byte size overflowed.",
        )
    }
    if (
        byteSize <= 0L ||
        byteSize > Int.MAX_VALUE.toLong() ||
        maxBufferSize?.let { byteSize > it } == true
    ) {
        return GPUPreparedTextDrawUniformPlanResult.Refused(
            code = "unsupported.recording.prepared_text_draw_uniform_buffer",
            message = "Prepared text draw-uniform buffer exceeds the observed allocation limit.",
        )
    }

    val bytes = ByteArray(byteSize.toInt())
    val target = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val slices = ArrayList<GPUPreparedTextDrawUniformSlice>(inputs.size)
    inputs.forEachIndexed { index, input ->
        val semantic = input.semantic
        val material = semantic.material
        val affine = semantic.deviceToLocal
        if (
            semantic.targetBounds.left != 0 ||
            semantic.targetBounds.top != 0 ||
            semantic.targetBounds.width <= 0 ||
            semantic.targetBounds.height <= 0 ||
            !material.paintAlpha.isFinite() ||
            material.paintAlpha !in 0f..1f
        ) {
            return GPUPreparedTextDrawUniformPlanResult.Refused(
                code = "invalid.recording.prepared_text_draw_uniform",
                message = "Prepared text draw uniforms require exact finite target and alpha facts.",
            )
        }
        val offsetBytes = try {
            Math.multiplyExact(strideBytes, index.toLong())
        } catch (_: ArithmeticException) {
            return GPUPreparedTextDrawUniformPlanResult.Refused(
                code = "unsupported.recording.prepared_text_draw_uniform_buffer",
                message = "Prepared text draw-uniform offset overflowed.",
            )
        }
        target.position(offsetBytes.toInt())
        target.putFloat(semantic.targetBounds.width.toFloat())
        target.putFloat(semantic.targetBounds.height.toFloat())
        target.putFloat(material.paintAlpha)
        target.putFloat(0f)
        target.putFloat(affine.m00)
        target.putFloat(affine.m01)
        target.putFloat(affine.m02)
        target.putFloat(0f)
        target.putFloat(affine.m10)
        target.putFloat(affine.m11)
        target.putFloat(affine.m12)
        target.putFloat(0f)
        val logicalEnd = Math.addExact(
            offsetBytes,
            PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
        )
        slices += GPUPreparedTextDrawUniformSlice(
            packetId = input.packetId,
            offsetBytes = offsetBytes,
            sizeBytes = PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
            contentHash = bytes.copyOfRange(offsetBytes.toInt(), logicalEnd.toInt())
                .preparedTextSha256(),
        )
    }
    val contentHash = bytes.preparedTextSha256()
    val plan = GPUPreparedTextDrawUniformBufferPlan(
        bufferRef = GPUFrameBufferRef(
            "buffer.prepared-text.draw-uniforms:$frameIdentity:$contentHash",
        ),
        alignmentBytes = alignmentBytes,
        logicalSliceSizeBytes = PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
        byteSize = byteSize,
        contentHash = contentHash,
        slices = slices,
        uploadBytes = bytes,
    )
    return GPUPreparedTextDrawUniformPlanResult.Prepared(
        plan = plan,
        slicesByPacketId = plan.slices.associateBy { it.packetId },
    )
}

private fun alignUpPreparedTextDrawUniform(value: Long, alignment: Long): Long {
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

internal fun ByteArray.preparedTextSha256(): String =
    buildString(64) {
        MessageDigest.getInstance("SHA-256")
            .digest(this@preparedTextSha256)
            .forEach { byte ->
                val value = byte.toInt() and 0xff
                append(PREPARED_TEXT_LOWER_HEX_DIGITS[value ushr 4])
                append(PREPARED_TEXT_LOWER_HEX_DIGITS[value and 0x0f])
            }
    }

internal const val PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES: Long = 48L
private const val PREPARED_TEXT_LOWER_HEX_DIGITS = "0123456789abcdef"
