package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult

internal sealed interface GPUPreparedSurfaceRouteDecision {
    data class Legacy(val code: String) : GPUPreparedSurfaceRouteDecision
    data class Prepared(val evidence: GPUPreparedSurfaceExecutionEvidence) : GPUPreparedSurfaceRouteDecision
    data class Terminal(val code: String) : GPUPreparedSurfaceRouteDecision
}

internal fun interface GPUPreparedSurfaceRouteTrace {
    fun record(decision: GPUPreparedSurfaceRouteDecision)
}

internal fun interface GPUPreparedSurfaceLegacyPort {
    fun render(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
        format: PixelFormat,
        config: RenderConfig,
        routeTrace: GPUClipRouteTrace?,
    ): RenderResult
}

internal class GPUPreparedSurfaceTerminalException(
    val diagnostic: GPUDiagnostic,
) : IllegalStateException("${diagnostic.code.value}: ${diagnostic.message}")

/**
 * Process-wide owner of the shared mono-backend runtime.
 *
 * Both prepared and legacy Surface work is kept under this owner so a
 * before-entry refusal can continue through legacy rendering without allowing
 * another frame to overlap the shared runtime.
 */
private object GPUPreparedSurfaceRuntimeOwner {
    val lock = ReentrantLock(true)
}

internal object GPUPreparedSurfaceProductEntry {
    fun render(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
        format: PixelFormat,
        config: RenderConfig,
        executionPort: GPUPreparedSurfaceExecutionPort,
        legacyPort: GPUPreparedSurfaceLegacyPort,
        legacyRouteTrace: GPUClipRouteTrace? = null,
        trace: GPUPreparedSurfaceRouteTrace? = null,
    ): RenderResult = GPUPreparedSurfaceRuntimeOwner.lock.withLock {
        when (
            val route = GPUPreparedSurfaceProductRouter.route(
                operations,
                width,
                height,
                format,
                config,
                executionPort,
            )
        ) {
            is GPUPreparedSurfaceProductRoute.Legacy -> {
                trace.recordWithoutAffectingRoute(GPUPreparedSurfaceRouteDecision.Legacy(route.code))
                legacyPort.render(operations, width, height, format, config, legacyRouteTrace)
            }

            is GPUPreparedSurfaceProductRoute.Prepared -> {
                trace.recordWithoutAffectingRoute(GPUPreparedSurfaceRouteDecision.Prepared(route.evidence))
                route.result
            }

            is GPUPreparedSurfaceProductRoute.Terminal -> {
                trace.recordWithoutAffectingRoute(GPUPreparedSurfaceRouteDecision.Terminal(route.diagnostic.code.value))
                throw GPUPreparedSurfaceTerminalException(route.diagnostic)
            }
        }
    }
}

private fun GPUPreparedSurfaceRouteTrace?.recordWithoutAffectingRoute(
    decision: GPUPreparedSurfaceRouteDecision,
) {
    try {
        this?.record(decision)
    } catch (_: Throwable) {
        // Diagnostics are observational and cannot change a product decision.
    }
}
