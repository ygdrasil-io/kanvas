package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult

@OptIn(ExperimentalUnsignedTypes::class)
internal fun renderViaGpu(
    buffer: DisplayListBuffer,
    width: Int,
    height: Int,
    format: PixelFormat,
    config: RenderConfig,
    preparedRouteTrace: GPUPreparedSurfaceRouteTrace? = null,
): RenderResult {
    val operations = buffer.ops()
    return GPUPlanSurfaceRouter().render(operations, width, height, format, config) {
        GPUPreparedSurfaceProductEntry.render(
            operations = operations,
            width = width,
            height = height,
            format = format,
            config = config,
            executionPort = preparedSurfaceProductExecutionPort,
            trace = preparedRouteTrace,
        )
    }
}

private val preparedSurfaceProductExecutionPort: GPUPreparedSurfaceExecutionPort =
    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
