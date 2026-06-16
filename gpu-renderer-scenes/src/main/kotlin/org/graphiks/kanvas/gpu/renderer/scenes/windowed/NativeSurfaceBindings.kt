package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import org.graphiks.kanvas.gpu.renderer.execution.GPUNativePlatform
import org.graphiks.kanvas.gpu.renderer.execution.GPUNativeSurfaceBinding

fun appKitMetalLayerBinding(width: Int, height: Int, nsLayer: Long): GPUNativeSurfaceBinding {
    require(nsLayer != 0L) { "AppKit Metal layer pointer must be non-zero" }

    return GPUNativeSurfaceBinding(
        platform = GPUNativePlatform.AppKitMetalLayer,
        width = width,
        height = height,
        pointerLabels = mapOf("nsLayer" to nsLayer),
    )
}
