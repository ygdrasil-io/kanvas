package org.graphiks.kanvas.gpu.renderer.resources

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation

/** Exact authority carried by one persistent color-only MSAA attachment set. */
internal data class GPURetainedMsaaAttachmentSet(
    val color: GPUTextureResourceRef,
    val depthStencil: GPUTextureResourceRef?,
    val targetId: String,
    val width: Int,
    val height: Int,
    val format: GPUColorFormat,
    val colorInterpretation: GPUColorInterpretation,
    val sampleCount: Int,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
) {
    init {
        require(targetId.isNotBlank()) { "GPURetainedMsaaAttachmentSet.targetId must not be blank" }
        require(width > 0 && height > 0) {
            "GPURetainedMsaaAttachmentSet dimensions must be positive"
        }
        require(sampleCount > 1) {
            "GPURetainedMsaaAttachmentSet.sampleCount must be multisampled"
        }
        require(targetGeneration >= 0L) {
            "GPURetainedMsaaAttachmentSet.targetGeneration must be non-negative"
        }
        require(color != depthStencil) {
            "MSAA color and depth-stencil attachments must be distinct"
        }
    }
}

/** Canonical prepared texture target reused by the ordered frame executor. */
internal class GPUSceneTarget(
    val targetId: String,
    val resolvedTexture: GPUTextureResourceRef,
    val retainedMsaaAttachment: GPURetainedMsaaAttachmentSet?,
    val width: Int,
    val height: Int,
    val format: GPUColorFormat,
    val colorInterpretation: GPUColorInterpretation,
    usages: Set<GPUFrameResourceUsage>,
    val sampleCount: Int,
    val deviceGeneration: GPUDeviceGenerationID,
    val targetGeneration: Long,
) {
    val usages: Set<GPUFrameResourceUsage> = immutableSet(usages)

    init {
        require(targetId.isNotBlank()) { "GPUSceneTarget.targetId must not be blank" }
        require(width > 0) { "GPUSceneTarget.width must be positive" }
        require(height > 0) { "GPUSceneTarget.height must be positive" }
        require(usages.isNotEmpty()) { "GPUSceneTarget.usages must not be empty" }
        require(GPUFrameResourceUsage.RenderAttachment in usages) {
            "GPUSceneTarget must be usable as a render attachment"
        }
        require(sampleCount > 0) { "GPUSceneTarget.sampleCount must be positive" }
        require(targetGeneration >= 0L) { "GPUSceneTarget.targetGeneration must be non-negative" }
        require(resolvedTexture != retainedMsaaAttachment?.color &&
            resolvedTexture != retainedMsaaAttachment?.depthStencil
        ) {
            "GPUSceneTarget resolved texture cannot also be an MSAA attachment"
        }
        require((sampleCount > 1) == (retainedMsaaAttachment != null)) {
            "GPUSceneTarget sample count and retained MSAA attachment must agree"
        }
        retainedMsaaAttachment?.let { retained ->
            require(
                retained.targetId == targetId &&
                    retained.width == width && retained.height == height &&
                    retained.format == format &&
                    retained.colorInterpretation == colorInterpretation &&
                    retained.sampleCount == sampleCount &&
                    retained.deviceGeneration == deviceGeneration &&
                    retained.targetGeneration == targetGeneration
            ) {
                "GPUSceneTarget retained MSAA authority must exactly match the scene target"
            }
        }
    }
}
