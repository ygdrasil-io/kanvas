package org.graphiks.kanvas.gpu.renderer.passes

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityDiagnostic
import org.graphiks.kanvas.gpu.renderer.capabilities.validateTextureRequest
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode

/** Pure authority for the exact CorePrimitive coverage/sample lanes promoted by the renderer. */
internal sealed interface GPUCorePrimitiveCoverageSampleAuthority {
    data object Accepted : GPUCorePrimitiveCoverageSampleAuthority

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUCorePrimitiveCoverageSampleAuthority
}

/**
 * Freezes coverage/sample support before any resource planning or native route classification.
 *
 * Capability evidence is checked only for otherwise eligible multisample routes. The bounded
 * B3.5c lane promotes color-only hard direct geometry at 4x while stencil AA stays closed until
 * its independent depth/stencil attachment contract is implemented.
 */
internal fun validateCorePrimitiveCoverageSampleAuthority(
    geometry: GPUCorePrimitiveGeometry,
    coverageMode: GPUCorePrimitiveCoverageMode,
    targetBounds: GPUPixelBounds,
    samplePlan: GPUSamplePlan,
    capabilities: GPUCapabilities,
): GPUCorePrimitiveCoverageSampleAuthority {
    fun refused(code: String, message: String) =
        GPUCorePrimitiveCoverageSampleAuthority.Refused(code, message)

    if (coverageMode == GPUCorePrimitiveCoverageMode.StencilAA &&
        samplePlan == GPUSamplePlan.SingleSampleFrame
    ) {
        return refused(
            "invalid.core_primitive.coverage_sample.stencil_aa_requires_multisample",
            "StencilAA requires an explicit multisample frame and cannot alias to a single-sample hard edge.",
        )
    }
    if (coverageMode == GPUCorePrimitiveCoverageMode.Stencil1x &&
        samplePlan is GPUSamplePlan.MultisampleFrame
    ) {
        return refused(
            "invalid.core_primitive.coverage_sample.stencil_1x_requires_single_sample",
            "Stencil1x requires a single-sample frame and cannot be relabeled as multisample coverage.",
        )
    }
    if (samplePlan is GPUSamplePlan.LocalResolveApproximation) {
        return refused(
            "unsupported.core_primitive.coverage_sample.local_resolve",
            "CorePrimitive preparation does not accept a local-resolve approximation as frame sample authority.",
        )
    }
    if (geometry is GPUCorePrimitiveGeometry.RRect &&
        (coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor ||
            coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA) &&
        samplePlan != GPUSamplePlan.SingleSampleFrame
    ) {
        return refused(
            "unsupported.core_primitive.coverage_sample.rrect_not_promoted",
            "Analytic RRect coverage is promoted only by the exact single-sample B3.5b route.",
        )
    }
    if (coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA &&
        (geometry is GPUCorePrimitiveGeometry.Rect || geometry is GPUCorePrimitiveGeometry.RRect) &&
        samplePlan != GPUSamplePlan.SingleSampleFrame
    ) {
        return refused(
            "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
            "ScalarAA coverage is promoted only by the exact single-sample B3.5b analytic route.",
        )
    }
    val geometryCoverageCompatible = when (coverageMode) {
        GPUCorePrimitiveCoverageMode.FullOrScissor ->
            geometry is GPUCorePrimitiveGeometry.Rect ||
                geometry is GPUCorePrimitiveGeometry.RRect ||
                geometry is GPUCorePrimitiveGeometry.TriangulatedPath &&
                geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles
        GPUCorePrimitiveCoverageMode.Stencil1x,
        GPUCorePrimitiveCoverageMode.StencilAA,
        -> geometry is GPUCorePrimitiveGeometry.TriangulatedPath &&
            geometry.geometryMode == GPUCorePrimitiveGeometryMode.StencilEdgeFan
        GPUCorePrimitiveCoverageMode.ScalarAA ->
            geometry is GPUCorePrimitiveGeometry.Rect || geometry is GPUCorePrimitiveGeometry.RRect
    }
    if (!geometryCoverageCompatible) {
        return refused(
            "invalid.core_primitive.coverage_sample.geometry_coverage",
            "CorePrimitive geometry ${geometry.canonicalType} does not match the ${coverageMode.name} coverage lane.",
        )
    }

    val multisamplePlan = samplePlan as? GPUSamplePlan.MultisampleFrame
        ?: return GPUCorePrimitiveCoverageSampleAuthority.Accepted
    if (multisamplePlan.sampleCount != 4) {
        return refused(
            "unsupported.core_primitive.coverage_sample.sample_count",
            "The bounded CorePrimitive WebGPU lane supports only one or four samples, not ${multisamplePlan.sampleCount}.",
        )
    }
    if (targetBounds.isEmpty) {
        return refused(
            "invalid.core_primitive.coverage_sample.target_bounds",
            "Multisample CorePrimitive capability validation requires non-empty target bounds.",
        )
    }

    capabilities.validateTextureRequest(
        format = GPUTextureFormat.RGBA8Unorm,
        width = targetBounds.width,
        height = targetBounds.height,
        usage = GPUTextureUsage.RenderAttachment,
        sampleCount = multisamplePlan.sampleCount,
        requiresResolve = true,
    )?.let { capability ->
        return refused(
            "unsupported.core_primitive.coverage_sample.color_capability",
            capabilityRefusalMessage("RGBA8Unorm render-and-resolve", capability),
        )
    }

    if (coverageMode == GPUCorePrimitiveCoverageMode.StencilAA) {
        capabilities.validateTextureRequest(
            format = GPUTextureFormat.Depth24PlusStencil8,
            width = targetBounds.width,
            height = targetBounds.height,
            usage = GPUTextureUsage.RenderAttachment,
            sampleCount = multisamplePlan.sampleCount,
            requiresResolve = false,
        )?.let { capability ->
            return refused(
                "unsupported.core_primitive.coverage_sample.depth_stencil_capability",
                capabilityRefusalMessage("Depth24PlusStencil8 render", capability),
            )
        }
        return refused(
            "unsupported.core_primitive.coverage_sample.stencil_aa_not_promoted",
            "StencilAA has exact 4x attachment capability evidence but its producer/consumer route is not promoted until B3.5d.",
        )
    }

    return GPUCorePrimitiveCoverageSampleAuthority.Accepted
}

private fun capabilityRefusalMessage(
    lane: String,
    diagnostic: GPUCapabilityDiagnostic,
): String = buildString {
    append(lane)
    append(" capability is unavailable: ")
    append(diagnostic.code)
    append(" requires ")
    append(diagnostic.required)
    diagnostic.observed?.let {
        append(" but observed ")
        append(it)
    }
    append('.')
}
