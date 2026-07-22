package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

internal sealed interface GPUPreparedSurfaceProductRoute {
    data class Legacy(val code: String) : GPUPreparedSurfaceProductRoute
    data class Prepared(
        val result: RenderResult,
        val evidence: GPUPreparedSurfaceExecutionEvidence,
    ) : GPUPreparedSurfaceProductRoute
    data class Terminal(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceProductRoute
}

internal object GPUPreparedSurfaceProductRouter {
    fun route(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
        format: PixelFormat,
        config: RenderConfig,
        executionPort: GPUPreparedSurfaceExecutionPort,
    ): GPUPreparedSurfaceProductRoute {
        if (format == PixelFormat.BGRA8) {
            return GPUPreparedSurfaceProductRoute.Legacy("legacy.surface.prepared.pixel-format.bgra8")
        }
        val candidate = when (val eligibility = GPUPreparedSurfaceFrameGate.classify(operations, config)) {
            is GPUPreparedSurfaceEligibility.Legacy -> return GPUPreparedSurfaceProductRoute.Legacy(eligibility.code)
            is GPUPreparedSurfaceEligibility.Candidate -> eligibility
        }
        return when (val execution = executionPort.execute(
            GPUPreparedSurfaceExecutionRequest(candidate, width, height),
        )) {
            is GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused ->
                GPUPreparedSurfaceProductRoute.Legacy(execution.diagnostic.code.value)
            is GPUPreparedSurfaceExecutionResult.TerminalFailure ->
                GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)
            is GPUPreparedSurfaceExecutionResult.Succeeded -> success(width, height, execution)
        }
    }

    private fun success(
        width: Int,
        height: Int,
        execution: GPUPreparedSurfaceExecutionResult.Succeeded,
    ): GPUPreparedSurfaceProductRoute {
        val drawCallCount = try {
            Math.toIntExact(Math.addExact(execution.evidence.draws, execution.evidence.drawIndexed))
        } catch (_: ArithmeticException) {
            return overflow("drawCallCount", "${execution.evidence.draws}+${execution.evidence.drawIndexed}")
        }
        val pipelineCount = try {
            Math.toIntExact(execution.evidence.pipelineBinds)
        } catch (_: ArithmeticException) {
            return overflow("pipelineCount", execution.evidence.pipelineBinds.toString())
        }
        return GPUPreparedSurfaceProductRoute.Prepared(
            result = RenderResult(
                pixels = execution.rgba.toUByteArray(),
                width = width,
                height = height,
                format = PixelFormat.RGBA8,
                diagnostics = Diagnostics(),
                stats = RenderStats(
                    opsDispatched = execution.visualOperationCount,
                    opsRefused = 0,
                    pipelineCount = pipelineCount,
                    drawCallCount = drawCallCount,
                    coverage = if (execution.visualOperationCount == 0) 0f else 1f,
                    coverageMeasured = false,
                ),
            ),
            evidence = execution.evidence,
        )
    }

    private fun overflow(field: String, value: String) = GPUPreparedSurfaceProductRoute.Terminal(
        GPUDiagnostic(
            code = GPUDiagnosticCode("invalid.surface.prepared.render-stats-overflow"),
            domain = GPUDiagnosticDomain.Execution,
            severity = GPUDiagnosticSeverity.Error,
            message = "Prepared Surface native counters do not fit RenderStats.",
            facts = mapOf("field" to field, "value" to value),
        ),
    )
}
