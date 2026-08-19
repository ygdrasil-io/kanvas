package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingDescriptorContract

/** Exact logical WebGPU texture-to-buffer readback layout. */
class GPUReadbackLayout internal constructor(
    val width: Int,
    val height: Int,
    val bytesPerPixel: Int,
    val copyBytesPerRowAlignment: Int,
    val unpaddedBytesPerRow: Long,
    val paddedBytesPerRow: Long,
    val rowsPerImage: Int,
    val bufferOffset: Long,
    val totalBufferBytes: Long,
)

/** Output-owned staging contract; pooled backing size stays distinct from the Dawn minimum. */
class GPUReadbackStagingDescriptor internal constructor(
    override val minimumBufferBytes: Long,
    override val maxBufferSize: Long,
    override val mapOffsetBytes: Long,
    usages: Set<GPUFrameResourceUsage>,
) : GPUReadbackStagingDescriptorContract {
    override val usages: Set<GPUFrameResourceUsage> = immutableSet(usages)
}

sealed interface GPUReadbackLayoutPlan {
    data class Planned(
        val layout: GPUReadbackLayout,
        val stagingDescriptor: GPUReadbackStagingDescriptor,
    ) : GPUReadbackLayoutPlan

    data class Refused(val diagnostic: GPUDiagnostic) : GPUReadbackLayoutPlan
}

/** Pure checked planner for canonical RGBA8 evidence readback. */
class GPUReadbackLayoutPlanner {
    fun plan(
        request: GPUFrameReadbackRequest,
        capabilities: GPUCapabilities,
    ): GPUReadbackLayoutPlan {
        if (request.sourceBounds.isEmpty) {
            return refused(
                "unsupported.readback.bounds_empty",
                "Readback bounds must have positive width and height.",
                mapOf("bounds" to request.sourceBounds.toString()),
            )
        }
        val limits = capabilities.limits ?: return refused(
            "unsupported.readback.limits_unavailable",
            "Readback planning requires a facade-observed limits snapshot.",
            emptyMap(),
        )
        val maxBufferSize = limits.maxBufferSize ?: return refused(
            "unsupported.readback.max_buffer_size_unavailable",
            "Readback planning requires a facade-observed maxBufferSize.",
            emptyMap(),
        )
        if (capabilities.rendererFeatures.isNotEmpty() &&
            GPURendererFeature.Readback !in capabilities.rendererFeatures
        ) {
            return refused(
                "unsupported.readback.capability_unavailable",
                "The selected capability snapshot does not expose renderer readback.",
                mapOf(
                    "rendererFeatures" to capabilities.rendererFeatures
                        .map { feature -> feature.dumpLabel }
                        .sorted()
                        .joinToString(","),
                ),
            )
        }
        if (request.pixelFormat != GPUReadbackPixelFormat.Rgba8Unorm) {
            return refused(
                "unsupported.readback.pixel_format",
                "Only canonical RGBA8 readback has a registered layout.",
                mapOf("pixelFormat" to request.pixelFormat.name),
            )
        }

        val alignment = limits.copyBytesPerRowAlignment
        if (alignment <= 0L || alignment and (alignment - 1L) != 0L) {
            return refused(
                "unsupported.readback.row_alignment_invalid",
                "copyBytesPerRowAlignment must be a positive power of two.",
                mapOf("copyBytesPerRowAlignment" to alignment.toString()),
            )
        }
        if (request.bufferOffsetBytes % RGBA8_BUFFER_OFFSET_ALIGNMENT != 0L) {
            return refused(
                "unsupported.readback.buffer_offset_alignment",
                "RGBA8 readback buffer offset must be a multiple of four and is never rounded.",
                mapOf(
                    "bufferOffsetBytes" to request.bufferOffsetBytes.toString(),
                    "requiredAlignmentBytes" to RGBA8_BUFFER_OFFSET_ALIGNMENT.toString(),
                ),
            )
        }

        val width = request.sourceBounds.width
        val height = request.sourceBounds.height
        val unpadded = try {
            Math.multiplyExact(width.toLong(), RGBA8_BYTES_PER_PIXEL.toLong())
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.readback.row_size_overflow",
                "Readback unpadded row size exceeds signed 64-bit arithmetic.",
                mapOf("width" to width.toString()),
            )
        }
        val padded = alignUp(unpadded, alignment) ?: return refused(
            "unsupported.readback.row_size_overflow",
            "Readback padded row size exceeds signed 64-bit arithmetic.",
            mapOf(
                "unpaddedBytesPerRow" to unpadded.toString(),
                "copyBytesPerRowAlignment" to alignment.toString(),
            ),
        )
        if (unpadded > UINT_MAX || padded > UINT_MAX) {
            return refused(
                "unsupported.readback.row_field_uint_overflow",
                "Readback row layout exceeds WebGPU UInt row fields.",
                mapOf(
                    "unpaddedBytesPerRow" to unpadded.toString(),
                    "paddedBytesPerRow" to padded.toString(),
                ),
            )
        }
        if (alignment > Int.MAX_VALUE) {
            return refused(
                "unsupported.readback.row_alignment_int_overflow",
                "Facade row alignment cannot be represented by the current layout contract.",
                mapOf("copyBytesPerRowAlignment" to alignment.toString()),
            )
        }

        val packedOutputBytes = try {
            Math.multiplyExact(unpadded, height.toLong())
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.readback.host_packed_output_too_large",
                "Tightly packed readback output exceeds current host storage.",
                mapOf("packedOutputBytes" to "overflow"),
            )
        }
        if (packedOutputBytes > Int.MAX_VALUE) {
            return refused(
                "unsupported.readback.host_packed_output_too_large",
                "Tightly packed readback output exceeds current ByteArray storage.",
                mapOf("packedOutputBytes" to packedOutputBytes.toString()),
            )
        }

        val totalBufferBytes = try {
            val precedingRows = Math.multiplyExact(padded, (height - 1).toLong())
            Math.addExact(request.bufferOffsetBytes, Math.addExact(precedingRows, unpadded))
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.readback.buffer_size_overflow",
                "Exact Dawn readback minimum exceeds signed 64-bit arithmetic.",
                mapOf("bufferOffsetBytes" to request.bufferOffsetBytes.toString()),
            )
        }
        if (totalBufferBytes > maxBufferSize) {
            return refused(
                "unsupported.readback.max_buffer_size_exceeded",
                "Exact Dawn readback minimum exceeds facade-observed maxBufferSize.",
                mapOf(
                    "totalBufferBytes" to totalBufferBytes.toString(),
                    "maxBufferSize" to maxBufferSize.toString(),
                ),
            )
        }
        if (totalBufferBytes > Int.MAX_VALUE) {
            return refused(
                "unsupported.readback.host_mapped_input_too_large",
                "Mapped readback input exceeds current ByteArray/facade storage.",
                mapOf("totalBufferBytes" to totalBufferBytes.toString()),
            )
        }

        val layout = GPUReadbackLayout(
            width = width,
            height = height,
            bytesPerPixel = RGBA8_BYTES_PER_PIXEL,
            copyBytesPerRowAlignment = alignment.toInt(),
            unpaddedBytesPerRow = unpadded,
            paddedBytesPerRow = padded,
            rowsPerImage = height,
            bufferOffset = request.bufferOffsetBytes,
            totalBufferBytes = totalBufferBytes,
        )
        return GPUReadbackLayoutPlan.Planned(
            layout = layout,
            stagingDescriptor = GPUReadbackStagingDescriptor(
                minimumBufferBytes = totalBufferBytes,
                maxBufferSize = maxBufferSize,
                mapOffsetBytes = 0,
                usages = setOf(
                    GPUFrameResourceUsage.MapRead,
                    GPUFrameResourceUsage.CopyDestination,
                ),
            ),
        )
    }
}

sealed interface GPUReadbackDepadPlan {
    class Depadded internal constructor(bytes: ByteArray) : GPUReadbackDepadPlan {
        private val snapshot = bytes.copyOf()

        fun copyBytes(): ByteArray = snapshot.copyOf()
    }

    data class Refused(val diagnostic: GPUDiagnostic) : GPUReadbackDepadPlan
}

/** Checked row depadding for a mapping that starts at offset zero. */
object GPUReadbackLayoutDepadder {
    fun depad(
        mappedFromZero: ByteArray,
        layout: GPUReadbackLayout,
    ): GPUReadbackDepadPlan {
        layout.invalidReason()?.let { reason ->
            return GPUReadbackDepadPlan.Refused(
                readbackDiagnostic(
                    "unsupported.readback.layout_invalid",
                    "Readback layout is internally inconsistent and cannot be depadded.",
                    mapOf("reason" to reason),
                ),
            )
        }
        if (mappedFromZero.size.toLong() < layout.totalBufferBytes) {
            return GPUReadbackDepadPlan.Refused(
                readbackDiagnostic(
                    "unsupported.readback.mapped_range_too_small",
                    "Mapped range is shorter than the exact Dawn minimum.",
                    mapOf(
                        "requiredBytes" to layout.totalBufferBytes.toString(),
                        "mappedBytes" to mappedFromZero.size.toString(),
                    ),
                ),
            )
        }
        val outputSize = try {
            Math.multiplyExact(layout.unpaddedBytesPerRow, layout.height.toLong()).toIntExact()
        } catch (_: ArithmeticException) {
            return GPUReadbackDepadPlan.Refused(
                readbackDiagnostic(
                    "unsupported.readback.host_packed_output_too_large",
                    "Depadded output exceeds current ByteArray storage.",
                    emptyMap(),
                ),
            )
        }
        val output = ByteArray(outputSize)
        val rowBytes = layout.unpaddedBytesPerRow.toInt()
        for (row in 0 until layout.height) {
            val sourceOffset = Math.addExact(
                layout.bufferOffset,
                Math.multiplyExact(row.toLong(), layout.paddedBytesPerRow),
            ).toInt()
            mappedFromZero.copyInto(
                destination = output,
                destinationOffset = row * rowBytes,
                startIndex = sourceOffset,
                endIndex = sourceOffset + rowBytes,
            )
        }
        return GPUReadbackDepadPlan.Depadded(output)
    }
}

private fun GPUReadbackLayout.invalidReason(): String? {
    if (width <= 0 || height <= 0 || bytesPerPixel <= 0) return "non-positive-dimension-or-pixel-size"
    if (copyBytesPerRowAlignment <= 0 ||
        copyBytesPerRowAlignment and (copyBytesPerRowAlignment - 1) != 0
    ) {
        return "row-alignment-invalid"
    }
    if (rowsPerImage != height) return "rows-per-image-mismatch"
    if (bufferOffset < 0L || bufferOffset % bytesPerPixel.toLong() != 0L) return "buffer-offset-invalid"
    val expectedUnpadded = try {
        Math.multiplyExact(width.toLong(), bytesPerPixel.toLong())
    } catch (_: ArithmeticException) {
        return "unpadded-row-overflow"
    }
    if (unpaddedBytesPerRow != expectedUnpadded) return "unpadded-row-mismatch"
    if (paddedBytesPerRow < unpaddedBytesPerRow ||
        paddedBytesPerRow % copyBytesPerRowAlignment.toLong() != 0L
    ) {
        return "padded-row-invalid"
    }
    val expectedTotal = try {
        Math.addExact(
            bufferOffset,
            Math.addExact(
                Math.multiplyExact(paddedBytesPerRow, (height - 1).toLong()),
                unpaddedBytesPerRow,
            ),
        )
    } catch (_: ArithmeticException) {
        return "total-buffer-overflow"
    }
    if (totalBufferBytes != expectedTotal || totalBufferBytes > Int.MAX_VALUE) {
        return "total-buffer-mismatch"
    }
    return null
}

private fun alignUp(value: Long, alignment: Long): Long? = try {
    val remainder = value % alignment
    if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
} catch (_: ArithmeticException) {
    null
}

private fun Long.toIntExact(): Int {
    if (this < 0L || this > Int.MAX_VALUE) throw ArithmeticException("value does not fit Int")
    return toInt()
}

private fun refused(
    code: String,
    message: String,
    facts: Map<String, String>,
): GPUReadbackLayoutPlan.Refused =
    GPUReadbackLayoutPlan.Refused(readbackDiagnostic(code, message, facts))

private fun readbackDiagnostic(
    code: String,
    message: String,
    facts: Map<String, String>,
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Execution,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = immutableMap(facts),
)

private const val RGBA8_BYTES_PER_PIXEL = 4
private const val RGBA8_BUFFER_OFFSET_ALIGNMENT = 4L
private const val UINT_MAX = 0xffff_ffffL
