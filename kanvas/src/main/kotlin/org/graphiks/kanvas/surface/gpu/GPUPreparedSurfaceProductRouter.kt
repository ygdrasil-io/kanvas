package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat as CanonicalGPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
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
        val candidate = when (val eligibility = GPUPreparedSurfaceFrameGate.classify(operations, config)) {
            is GPUPreparedSurfaceEligibility.Refused ->
                return GPUPreparedSurfaceProductRoute.Terminal(terminalDiagnostic(eligibility.code))
            is GPUPreparedSurfaceEligibility.Candidate -> eligibility
        }
        // The default RenderConfig carries RGBA8_UNORM_SRGB, so the config-derived color would
        // never select a bgra8unorm target. The requested surface format drives the target
        // (Graphite model: surface color type -> texture format).
        val targetCandidate = when (format) {
            PixelFormat.BGRA8 -> candidate.copy(
                color = GPUPreparedSurfaceColorMapping.Ready(
                    physicalFormat = CanonicalGPUColorFormat.BGRA8Unorm,
                    interpretation = GPUColorInterpretation.EncodedPremulSrgb,
                ),
            )
            // Asymmetric by design: the RGBA8 path keeps the config-derived color (the
            // default config carries RGBA8_UNORM_SRGB). A Surface(format = RGBA8) with an
            // explicit config.gpuColorFormat = BGRA8_UNORM opens a bgra8unorm target and
            // returns BGRA-ordered bytes labelled RGBA8 — a newly-reachable edge, not a
            // regression.
            PixelFormat.RGBA8 -> candidate
        }
        return when (val execution = executionPort.execute(
            GPUPreparedSurfaceExecutionRequest(targetCandidate, width, height),
        )) {
            is GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused ->
                GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)
            is GPUPreparedSurfaceExecutionResult.TerminalFailure ->
                GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)
            is GPUPreparedSurfaceExecutionResult.Succeeded -> success(width, height, format, execution)
        }
    }

    private fun success(
        width: Int,
        height: Int,
        format: PixelFormat,
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
}
