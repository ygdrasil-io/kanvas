package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanTextureResolveSupport
import org.graphiks.kanvas.gpu.plan.PlanTextureSampleSupport
import org.graphiks.kanvas.gpu.plan.W3PlanDiagnostics
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.resources.CORE_PRIMITIVE_FRAME_POOL_INDEX_FLOOR_BYTES
import org.graphiks.kanvas.gpu.renderer.resources.CORE_PRIMITIVE_FRAME_POOL_UNIFORM_FLOOR_BYTES
import org.graphiks.kanvas.gpu.renderer.resources.CORE_PRIMITIVE_FRAME_POOL_VERTEX_FLOOR_BYTES
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
    val observedLimits = limits ?: return unsupported(
        message = "Renderer limits were not observed for this device session.",
        code = W3PlanDiagnostics.CapabilityBufferSize,
    )
    val maxBuffer = observedLimits.maxBufferSize ?: return unsupported(
        message = "Renderer maxBufferSize was not observed for this device session.",
        code = W3PlanDiagnostics.CapabilityBufferSize,
    )
    if (observedLimits.maxTextureDimension2D > Int.MAX_VALUE ||
        observedLimits.copyBytesPerRowAlignment > Int.MAX_VALUE ||
        observedLimits.minUniformBufferOffsetAlignment > Int.MAX_VALUE ||
        observedLimits.maxDynamicUniformBuffersPerPipelineLayout?.let { it > Int.MAX_VALUE } == true ||
        !observedLimits.copyBytesPerRowAlignment.isPositivePowerOfTwo()
    ) {
        return unsupported("Renderer capabilities cannot represent the W3 sRGB target contract.")
    }
    val srgbSamples = textureFormatSampleSupport[GPUTextureFormat.RGBA8UnormSrgb]
        ?.renderAttachmentSampleCounts
    if (GPUTextureFormat.RGBA8UnormSrgb !in supportedTextureFormats || 1 !in srgbSamples.orEmpty()) {
        return unsupported(
            message = "Renderer capabilities do not support a single-sample RGBA8UnormSrgb render target.",
            code = W3PlanDiagnostics.CapabilityFormat,
        )
    }
    val hasW4dTextureUsages = supportedTextureUsage?.supports(
        GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
    ) == true
    // W4c's historical single-sample depth/stencil contract predates the optional
    // physical-usage observation.  Its sample evidence remains authoritative; the
    // stricter W4d.2 physical topology is deliberately confined to 4x/resolve/mask.
    val depthStencilSamples = textureFormatSampleSupport[GPUTextureFormat.Depth24PlusStencil8]
        ?.renderAttachmentSampleCounts.orEmpty()
    val hasSingleSampleD24S8 = 1 in depthStencilSamples
    // D24S8 is intentionally table-only: CapabilityContracts accepts its sample
    // evidence for the exact RenderAttachment request even when the broad-format
    // observation omits it.  W4d needs both attachment sample counts, never a
    // synthetic broad-format entry.
    val hasW4dD24S8RenderAttachmentEvidence = 1 in depthStencilSamples && 4 in depthStencilSamples
    val hasW4dBroadColorFormats = setOf(
        GPUTextureFormat.RGBA8UnormSrgb,
        GPUTextureFormat.RGBA8Unorm,
    ).all { format -> format in supportedTextureFormats }
    val hasW4dPhysicalTopology = hasW4dBroadColorFormats &&
        hasW4dTextureUsages && hasW4dD24S8RenderAttachmentEvidence
    val hasFourSampleSrgb = hasW4dPhysicalTopology && 4 in srgbSamples.orEmpty()
    val operations = rendererFeatures.mapNotNull { feature ->
        when (feature) {
            GPURendererFeature.RenderPass -> PlanOperationCapability.RenderPass
            GPURendererFeature.CopyUpload -> PlanOperationCapability.CopyUpload
            GPURendererFeature.UniformBuffer -> PlanOperationCapability.UniformBuffer
            GPURendererFeature.Readback -> PlanOperationCapability.Readback
            else -> null
        }
    }.toMutableSet()
    val depthStencilFormats = mutableSetOf<PlanDepthStencilFormat>()
    if (hasSingleSampleD24S8) {
        operations += PlanOperationCapability.DepthStencilAttachment
        operations += PlanOperationCapability.StencilCover
        depthStencilFormats += PlanDepthStencilFormat.Depth24PlusStencil8
    }
    val sampleSupports = buildSet {
        srgbSamples.orEmpty().filter { sampleCount ->
            sampleCount == 1 || sampleCount == 4 && hasFourSampleSrgb
        }.forEach { sampleCount ->
            add(
                PlanTextureSampleSupport.of(
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                    sampleCount,
                    if (sampleCount == 1) {
                        setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource)
                    } else {
                        setOf(PlanResourceUsage.RenderAttachment)
                    },
                ),
            )
        }
        depthStencilSamples
            .filter { sampleCount -> hasSingleSampleD24S8 && sampleCount in setOf(1, 4) }
            .forEach { sampleCount ->
                add(
                    PlanTextureSampleSupport.of(
                        PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                        sampleCount,
                        setOf(PlanResourceUsage.DepthStencilAttachment),
                    ),
                )
            }
        val maskSamples = textureFormatSampleSupport[GPUTextureFormat.RGBA8Unorm]
            ?.renderAttachmentSampleCounts.orEmpty()
        val hasHardMaskTopology =
            GPUTextureFormat.RGBA8Unorm in supportedTextureFormats &&
                supportedTextureUsage?.supports(
                    GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                ) == true &&
                1 in maskSamples
        if (hasHardMaskTopology) {
            add(
                PlanTextureSampleSupport.of(
                    PlanTextureFormat.CoverageMask,
                    1,
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
                ),
            )
        }
        if (hasW4dPhysicalTopology && hasFourSampleSrgb && 4 in maskSamples) {
            add(PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask,
                4,
                setOf(PlanResourceUsage.RenderAttachment),
            ))
        }
    }
    val resolveSupports = buildSet {
        if (hasFourSampleSrgb && 4 in textureFormatSampleSupport[GPUTextureFormat.RGBA8UnormSrgb]
                ?.resolveSourceSampleCounts.orEmpty()
        ) {
            add(
                PlanTextureResolveSupport.of(
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                    4,
                    1,
                ),
            )
        }
        if (hasW4dPhysicalTopology && hasFourSampleSrgb &&
            4 in textureFormatSampleSupport[GPUTextureFormat.RGBA8Unorm]
                ?.resolveSourceSampleCounts.orEmpty()
        ) {
            add(PlanTextureResolveSupport.of(PlanTextureFormat.CoverageMask, 4, 1))
        }
    }
    return try {
        val snapshot = PlanCapabilitySnapshot.of(
                deviceGeneration = deviceGeneration.value,
                maxTextureDimension2D = observedLimits.maxTextureDimension2D.toInt(),
                maxBufferSizeBytes = maxBuffer,
                copyBytesPerRowAlignment = observedLimits.copyBytesPerRowAlignment.toInt(),
                supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                minUniformBufferOffsetAlignment = observedLimits.minUniformBufferOffsetAlignment.toInt(),
                maxDynamicUniformBuffersPerPipelineLayout =
                    observedLimits.maxDynamicUniformBuffersPerPipelineLayout?.toInt() ?: 0,
                supportedOperations = operations,
                bufferAllocationPolicy = PlanBufferAllocationPolicy.of(
                    CORE_PRIMITIVE_FRAME_POOL_VERTEX_FLOOR_BYTES,
                    CORE_PRIMITIVE_FRAME_POOL_INDEX_FLOOR_BYTES,
                    CORE_PRIMITIVE_FRAME_POOL_UNIFORM_FLOOR_BYTES,
                ),
                supportedDepthStencilFormats = depthStencilFormats,
                supportedTextureSampleSupports = sampleSupports,
                supportedTextureResolveSupports = resolveSupports,
            )
        GpuPlanCapabilityAdapterResult.Supported(snapshot)
    } catch (_: IllegalArgumentException) {
        unsupported("Renderer capabilities are incoherent for W3 planning.")
    }
}

private fun Long.isPositivePowerOfTwo(): Boolean = this > 0L && this and (this - 1L) == 0L

private fun GPUTextureUsage.supports(required: GPUTextureUsage): Boolean =
    (value and required.value) == required.value

private fun unsupported(
    message: String,
    code: RenderDiagnosticCode = RenderDiagnosticCode("w3.lowering.unsupported_capability"),
): GpuPlanCapabilityAdapterResult.Unsupported =
    GpuPlanCapabilityAdapterResult.Unsupported(
        RenderDiagnostic(
            code,
            RenderDiagnosticDomain.CAPABILITY,
            RenderDiagnosticSeverity.ERROR,
            message,
        ),
    )
