package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1

/** The sole authenticated projection from a logical composition target to native storage. */
internal data class GpuTargetFormatResolution(
    val logicalFormat: PlanLogicalColorFormat,
    val nativeFormat: GPUColorFormat,
    val interpretation: GPUColorInterpretation,
    val outputPremultiplication: ImagePremultiplicationV1,
)

internal fun PlanLogicalColorFormat.resolveGpuTargetFormat(): GpuTargetFormatResolution = when (this) {
    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL -> GpuTargetFormatResolution(
        this, GPUColorFormat.RGBA8UnormSrgb, GPUColorInterpretation.LinearPremul,
        ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL,
    )
    PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL -> GpuTargetFormatResolution(
        this, GPUColorFormat.RGBA8Unorm, GPUColorInterpretation.EncodedPremulSrgb,
        ImagePremultiplicationV1.SOURCE_SPACE,
    )
    else -> error("Unsupported direct Surface logical target format: $this")
}
