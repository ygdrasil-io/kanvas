package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

/** Staging buffer and texture upload plan for decoded image pixels. */
class GPUStagingUploadPlan(
    val sourceId: String,
    val width: Int,
    val height: Int,
    val format: String,
    val stagingBufferLabel: String,
    val textureLabel: String,
)

/** Materializes decoded image pixels into staging buffer and texture resource plans. */
class GPUImageUploadMaterializer(
    private val maxUploadBytes: Long = 1_048_576L,
) {
    /** Materializes decoded pixel data into a resource materialization decision. */
    fun materialize(
        sourceId: String,
        width: Int,
        height: Int,
        pixelFormat: String,
        rowBytes: Long,
        contentHash: String,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val resourceLabel = "uploaded-image:$sourceId"

        if (pixelFormat != "RGBA8Unorm") {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = GPUResourceDiagnostic(
                    code = "unsupported.image.pixel.format",
                    resourceLabel = resourceLabel,
                    message = "Texture upload requires RGBA8Unorm pixel format, got $pixelFormat.",
                    terminal = true,
                    facts = mapOf("pixelFormat" to pixelFormat),
                ),
                targetId = context.targetId,
                resourcePlanLabels = listOf(resourceLabel),
            )
        }

        val uploadBytes = rowBytes * height
        if (uploadBytes > maxUploadBytes) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = GPUResourceDiagnostic(
                    code = "unsupported.image.upload.budget_exceeded",
                    resourceLabel = resourceLabel,
                    message = "Image upload $uploadBytes bytes exceeds budget of $maxUploadBytes bytes.",
                    terminal = true,
                    facts = mapOf(
                        "uploadBytes" to uploadBytes.toString(),
                        "budgetBytes" to maxUploadBytes.toString(),
                    ),
                ),
                targetId = context.targetId,
                resourcePlanLabels = listOf(resourceLabel),
            )
        }

        val textureRef = GPUTextureResourceRef("texture-ref:$sourceId")
        val textureOperand = GPUMaterializedCommandOperandReference(
            label = "texture:$sourceId",
            kind = GPUMaterializedCommandOperandKind.Texture,
            descriptorHash = "sha256:$contentHash",
            deviceGeneration = context.deviceGeneration,
            ownerScope = "image-upload",
            usageLabels = listOf("texture_binding", "copy_dst"),
            invalidationPolicy = "recording-complete",
            evidenceFacts = mapOf(
                "sourceId" to sourceId,
                "pixelFormat" to pixelFormat,
                "width" to width.toString(),
                "height" to height.toString(),
            ),
        )

        return GPUResourceMaterializationDecision.Materialized(
            resources = listOf(textureRef),
            targetId = context.targetId,
            resourcePlanLabels = listOf(resourceLabel),
            operandBridge = listOf(
                GPUMaterializedCommandOperandBinding(
                    commandLabel = "upload-texture",
                    operand = textureOperand,
                ),
            ),
        )
    }
}
