package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult

internal sealed interface GPUPreparedSurfaceRouteDecision {
    data class Prepared(val evidence: GPUPreparedSurfaceExecutionEvidence) : GPUPreparedSurfaceRouteDecision
    data class Terminal(val code: String) : GPUPreparedSurfaceRouteDecision
}

internal fun interface GPUPreparedSurfaceRouteTrace {
    fun record(decision: GPUPreparedSurfaceRouteDecision)
}

internal class GPUPreparedSurfaceTerminalException(
    val diagnostic: GPUDiagnostic,
) : IllegalStateException("${diagnostic.code.value}: ${diagnostic.message}")

/**
 * Process-wide owner of the shared mono-backend runtime.
 *
 * All prepared Surface work is kept under this owner so no frame can overlap the
 * shared runtime. Refusals are terminal before this boundary.
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
