package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.gpu.evidence.runner.ScenePreparation
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectDescriptor
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectExecutor
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectID
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectRegistry
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectChildSlotPlan
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectUniformSchema

private const val PERFORMANCE_COMPLETION_ONLY_REQUEST_ID = "gpu-evidence.performance.completion-only"

/** The two low-level renderer contracts that intentionally remain refusal-only. */
object RendererRefusalPrograms {
    fun unregisteredRuntimeEffect(id: GPUCustomRuntimeEffectID): RoutedSceneProgram = routed("product.runtime-effect.custom") {
        val execution = GPUCustomRuntimeEffectExecutor(EmptyCustomRuntimeEffectRegistry).execute(id)
        ScenePreparation.Refused(execution.reason, "Custom runtime effect ${execution.descriptorId} was ${execution.outcome}.", execution.dumpLines())
    }

    fun aggregateMemoryBudget(): RoutedSceneProgram = routed("product.solid-rect") { context ->
        when (val recorded = GPUSolidRectFrameRecorder().record(
            GPUSolidRectFrameRecordingRequest(
                frameId = GPUFrameID(context.frameOrdinal),
                recordingId = GPURecordingID("gpu-evidence.${context.frameOrdinal}"),
                capabilities = context.capabilities,
                deviceGeneration = context.deviceGeneration,
                target = context.target,
                targetBounds = context.targetBounds,
                draws = listOf(
                    org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameResolvedDraw(
                        1,
                        GPUPixelBounds(0, 0, 16, 16),
                        listOf(0.2f, 0.4f, 0.8f, 1f),
                    ),
                ),
                readbackRequestId = context.readbackRequestId.takeIf { it.value != PERFORMANCE_COMPLETION_ONLY_REQUEST_ID },
                configuredAggregateBudgetBytes = 1L,
            ),
        )) {
            is GPUSolidRectFrameRecordingResult.Recorded -> ScenePreparation.Recorded(
                "product.solid-rect",
                recorded.taskList,
                emptyList(),
            )
            is GPUSolidRectFrameRecordingResult.Refused -> ScenePreparation.Refused(
                recorded.diagnostic.code.value,
                recorded.diagnostic.message,
                diagnosticLines(recorded.diagnostic),
            )
        }
    }

    private fun routed(routeId: String, prepare: (org.graphiks.kanvas.gpu.evidence.runner.SceneRecordingContext) -> ScenePreparation): RoutedSceneProgram = object : RoutedSceneProgram {
        override val routeId = routeId
        override fun prepare(context: org.graphiks.kanvas.gpu.evidence.runner.SceneRecordingContext) = prepare(context)
    }

    private fun diagnosticLines(diagnostic: GPUDiagnostic): List<String> = buildList {
        add("diagnostic.code=${diagnostic.code.value}")
        add("diagnostic.domain=${diagnostic.domain.name}")
        add("diagnostic.severity=${diagnostic.severity.name}")
        add("diagnostic.message=${diagnostic.message}")
        add("diagnostic.terminal=${diagnostic.isTerminal}")
        add("diagnostic.retryable=${diagnostic.isRetryable}")
        diagnostic.facts.toSortedMap().forEach { (key, value) -> add("diagnostic.fact.$key=$value") }
    }

    private object EmptyCustomRuntimeEffectRegistry : GPUCustomRuntimeEffectRegistry {
        override fun registerCustomEffect(source: String, uniformSchema: GPURuntimeEffectUniformSchema, childSlots: List<GPURuntimeEffectChildSlotPlan>, sourceProvenance: String): Result<GPUCustomRuntimeEffectID> =
            error("The evidence refusal registry is intentionally empty")
        override fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = null
        override fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID) = Unit
        override fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean = false
    }
}
