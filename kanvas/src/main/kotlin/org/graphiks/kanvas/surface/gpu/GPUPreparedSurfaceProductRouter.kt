@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

internal sealed interface GPUPreparedSurfaceProductRoute {
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
        try {
            config.requireCompositionTarget()
        } catch (failure: GPUPlanSurfaceTerminalException) {
            return GPUPreparedSurfaceProductRoute.Terminal(terminalDiagnostic(failure.code))
        }
        val candidate = when (val eligibility = GPUPreparedSurfaceFrameGate.classify(operations, config)) {
            is GPUPreparedSurfaceEligibility.Refused ->
                return GPUPreparedSurfaceProductRoute.Terminal(terminalDiagnostic(eligibility.code))
            is GPUPreparedSurfaceEligibility.Candidate -> eligibility
        }
        return when (val execution = executionPort.execute(
            GPUPreparedSurfaceExecutionRequest(candidate, width, height),
        )) {
            is GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused ->
                GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)
            is GPUPreparedSurfaceExecutionResult.TerminalFailure ->
                GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)
            is GPUPreparedSurfaceExecutionResult.Succeeded -> success(width, height, format, config, execution)
        }
    }

    private fun success(
        width: Int,
        height: Int,
        format: PixelFormat,
        config: RenderConfig,
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
                pixels = publicPixels(execution.rgba, format),
                width = width,
                height = height,
                format = format,
                diagnostics = Diagnostics().apply {
                    execution.evidence.destinationReadEvidence
                        .sortedBy(GPUPreparedSurfaceDestinationReadEvidence::commandId)
                        .forEach { routeEvidence ->
                            val operation = "${routeEvidence.operationFamily}:${routeEvidence.commandId}"
                            degrade(
                                code = "route:destination-read:$operation",
                                operation = operation,
                                reason = "gpu-copy-then-formula",
                                facts = listOf(
                                    DiagnosticFact(
                                        "destination-read.source",
                                        routeEvidence.sourceLabel,
                                    ),
                                    DiagnosticFact(
                                        "destination-read.snapshot",
                                        routeEvidence.snapshotLabel,
                                    ),
                                    DiagnosticFact(
                                        "destination-read.mode",
                                        routeEvidence.modeLabel,
                                    ),
                                    DiagnosticFact(
                                        "clip.strategy",
                                        routeEvidence.clipStrategy,
                                    ),
                                    DiagnosticFact(
                                        "destination-read.action",
                                        routeEvidence.action,
                                    ),
                                ),
                            )
                        }
                },
                stats = RenderStats(
                    opsDispatched = execution.visualOperationCount,
                    opsRefused = 0,
                    pipelineCount = pipelineCount,
                    drawCallCount = drawCallCount,
                    coverage = if (execution.visualOperationCount == 0) 0f else 1f,
                    coverageMeasured = false,
                ),
                structuralSteps = execution.evidence.structuralSteps,
                nativeEvidenceCounters = mapOf(
                    "preparedImage.textureUploadScope" to
                        execution.evidence.preparedImageFrameTextureUploadScopesEncoded,
                    "preparedImage.frameTextureCreations" to
                        execution.evidence.preparedImageFrameTextureCreations,
                    "preparedImage.frameSamplerCreations" to
                        execution.evidence.preparedImageFrameSamplerCreations,
                    "preparedImage.frameBindGroupCreations" to
                        execution.evidence.preparedImageFrameBindGroupCreations,
                    "preparedImage.queueWriteTextureCalls" to
                        execution.evidence.preparedImageFrameTextureWriteTextureCalls,
                ),
                nativeEvidenceScopeKinds = if (
                    execution.evidence.preparedImageFrameTextureUploadScopesEncoded > 0L
                ) listOf("Upload") else emptyList(),
                premultiplication = config.resolvedCompositionPremultiplication(),
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

    private fun terminalDiagnostic(code: String) = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Execution,
        severity = GPUDiagnosticSeverity.Error,
        message = "The prepared Surface route cannot render this frame.",
    )

    /** PixelFormat controls only public byte layout; the prepared target is resolved by domain. */
    private fun publicPixels(rgba: ByteArray, format: PixelFormat): UByteArray = when (format) {
        PixelFormat.RGBA8 -> rgba.toUByteArray()
        PixelFormat.BGRA8 -> rgba.copyOf().also { bytes ->
            bytes.indices.step(4).forEach { index ->
                val red = bytes[index]
                bytes[index] = bytes[index + 2]
                bytes[index + 2] = red
            }
        }.toUByteArray()
    }

}
