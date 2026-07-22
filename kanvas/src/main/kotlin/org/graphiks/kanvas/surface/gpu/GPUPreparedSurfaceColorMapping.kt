package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat as CanonicalGPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

internal sealed interface GPUPreparedSurfaceColorMapping {
    data class Ready(
        val physicalFormat: CanonicalGPUColorFormat,
        val interpretation: GPUColorInterpretation,
    ) : GPUPreparedSurfaceColorMapping

    data class Refused(val code: String) : GPUPreparedSurfaceColorMapping
}

internal fun RenderConfig.mapPreparedGpuColorConfig(): GPUPreparedSurfaceColorMapping =
    when (gpuColorFormat) {
        GPUColorFormat.RGBA8_UNORM_SRGB -> GPUPreparedSurfaceColorMapping.Ready(
            physicalFormat = CanonicalGPUColorFormat.RGBA8Unorm,
            interpretation = GPUColorInterpretation.EncodedPremulSrgb,
        )
        GPUColorFormat.RGBA8_UNORM -> GPUPreparedSurfaceColorMapping.Refused(
            "unsupported.surface.gpu-color-format.rgba8-unorm",
        )
        GPUColorFormat.BGRA8_UNORM -> GPUPreparedSurfaceColorMapping.Refused(
            "unsupported.surface.gpu-color-format.bgra8-unorm",
        )
    }
