package org.graphiks.kanvas.gpu.renderer.filters

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
                code = GPUPreparedFilterRefusalCodes.NATIVE_CAPABILITY,
                facts = mapOf("kind" to maskFilter::class.simpleName.orEmpty()),
            )
        }
    }
}
