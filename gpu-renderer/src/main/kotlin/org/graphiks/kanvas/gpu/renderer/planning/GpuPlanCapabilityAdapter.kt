package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

public sealed interface GpuPlanCapabilityAdapterResult {
    public data class Supported(public val snapshot: PlanCapabilitySnapshot) : GpuPlanCapabilityAdapterResult
    public data class Unsupported(public val diagnostic: RenderDiagnostic) : GpuPlanCapabilityAdapterResult
}

/** Converts the selected renderer session's observed limits to a handle-free planning snapshot. */
public fun GPUCapabilities.toPlanCapabilitySnapshot(
    deviceGeneration: GPUDeviceGenerationID,
): GpuPlanCapabilityAdapterResult {
    val observedLimits = limits ?: return unsupported("Renderer limits were not observed for this device session.")
    val maxBuffer = observedLimits.maxBufferSize ?: return unsupported(
        "Renderer maxBufferSize was not observed for this device session.",
    )
    if (observedLimits.maxTextureDimension2D > Int.MAX_VALUE ||
        observedLimits.copyBytesPerRowAlignment > Int.MAX_VALUE ||
        !observedLimits.copyBytesPerRowAlignment.isPositivePowerOfTwo() ||
        GPUTextureFormat.RGBA8UnormSrgb !in supportedTextureFormats
    ) {
        return unsupported("Renderer capabilities cannot represent the W3 sRGB target contract.")
    }
    return try {
        GpuPlanCapabilityAdapterResult.Supported(
            PlanCapabilitySnapshot.of(
                deviceGeneration = deviceGeneration.value,
                maxTextureDimension2D = observedLimits.maxTextureDimension2D.toInt(),
                maxBufferSizeBytes = maxBuffer,
                copyBytesPerRowAlignment = observedLimits.copyBytesPerRowAlignment.toInt(),
                supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            ),
        )
    } catch (_: IllegalArgumentException) {
        unsupported("Renderer capabilities are incoherent for W3 planning.")
    }
}

private fun Long.isPositivePowerOfTwo(): Boolean = this > 0L && this and (this - 1L) == 0L

private fun unsupported(message: String): GpuPlanCapabilityAdapterResult.Unsupported =
    GpuPlanCapabilityAdapterResult.Unsupported(
        RenderDiagnostic(
            RenderDiagnosticCode("w3.lowering.unsupported_capability"),
            RenderDiagnosticDomain.CAPABILITY,
            RenderDiagnosticSeverity.ERROR,
            message,
        ),
    )
