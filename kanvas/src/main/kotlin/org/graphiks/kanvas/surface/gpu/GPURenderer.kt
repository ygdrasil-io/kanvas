package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.SnapshotOwningDisplayListBuffer
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
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
    captureLimits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT,
): RenderResult {
    val operations = if (buffer is SnapshotOwningDisplayListBuffer) buffer.sealedOps() else buffer.ops()
    return GPUPlanSurfaceRouter(captureLimits = captureLimits).render(operations, width, height, format, config) {
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
