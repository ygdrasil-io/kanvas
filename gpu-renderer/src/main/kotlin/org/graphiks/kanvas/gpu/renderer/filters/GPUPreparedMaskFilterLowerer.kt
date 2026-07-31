package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCoverageFormat
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMaskFilterKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMaskFilterLowering
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedMaskFilterPlan

object GPUPreparedMaskFilterLowerer {
    fun lower(maskFilter: NormalizedMaskFilter): GPUPreparedMaskFilterLowering {
        return when (maskFilter) {
            is NormalizedMaskFilter.Blur -> GPUPreparedMaskFilterLowering.Ready(
                GPUPreparedMaskFilterPlan(
                    kind = GPUPreparedMaskFilterKind.Blur,
                    coverageFormat = GPUPreparedCoverageFormat.A8,
                    executionIdentity = "mask-blur-${maskFilter.sigma}-${maskFilter.style}",
                    tableEntries = emptyList(),
                ),
            )
            else -> GPUPreparedMaskFilterLowering.Refused(
                code = GPUPreparedCompositeRefusalCodes.NATIVE_CAPABILITY,
                facts = mapOf("kind" to maskFilter::class.simpleName.orEmpty()),
            )
        }
    }
}
