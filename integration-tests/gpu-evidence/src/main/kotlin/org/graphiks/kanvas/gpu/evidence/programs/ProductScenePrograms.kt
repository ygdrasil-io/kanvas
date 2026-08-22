package org.graphiks.kanvas.gpu.evidence.programs

import org.graphiks.kanvas.gpu.evidence.runner.ScenePreparation
import org.graphiks.kanvas.gpu.evidence.runner.SceneProgram
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameResolvedDraw
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectDescriptor
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectExecutor
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectID
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPUCustomRuntimeEffectRegistry
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectChildSlotPlan
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectUniformSchema

/** Product-only scene programs; this package never owns WGSL, ABI packing, or backend encoding. */
object ProductScenePrograms {
    fun solidRects(
        draws: List<GPUSolidRectFrameResolvedDraw>,
        budgetBytes: Long = 1L shl 30,
    ): SceneProgram = SceneProgram { context ->
        when (val recorded = GPUSolidRectFrameRecorder().record(
            GPUSolidRectFrameRecordingRequest(
                frameId = GPUFrameID(context.frameOrdinal),
                recordingId = GPURecordingID("gpu-evidence.${context.frameOrdinal}"),
                capabilities = context.capabilities,
                deviceGeneration = context.deviceGeneration,
                target = context.target,
                targetBounds = context.targetBounds,
                draws = draws,
                readbackRequestId = context.readbackRequestId,
                configuredAggregateBudgetBytes = budgetBytes,
            ),
        )) {
            is GPUSolidRectFrameRecordingResult.Recorded ->
                ScenePreparation.Recorded("product.solid-rect", recorded.taskList, emptyList())
            is GPUSolidRectFrameRecordingResult.Refused -> ScenePreparation.Refused(
                recorded.diagnostic.code.value,
                recorded.diagnostic.message,
                listOf("${recorded.diagnostic.code.value}: ${recorded.diagnostic.message}"),
            )
        }
    }

    fun unregisteredRuntimeEffect(id: GPUCustomRuntimeEffectID): SceneProgram = SceneProgram {
        val execution = GPUCustomRuntimeEffectExecutor(EmptyCustomRuntimeEffectRegistry).execute(id)
        ScenePreparation.Refused(execution.reason, "Custom runtime effect ${execution.descriptorId} was ${execution.outcome}.", execution.dumpLines())
    }

    private object EmptyCustomRuntimeEffectRegistry : GPUCustomRuntimeEffectRegistry {
        override fun registerCustomEffect(source: String, uniformSchema: GPURuntimeEffectUniformSchema, childSlots: List<GPURuntimeEffectChildSlotPlan>, sourceProvenance: String): Result<GPUCustomRuntimeEffectID> =
            error("The evidence refusal registry is intentionally empty")
        override fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = null
        override fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID) = Unit
        override fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean = false
    }
}
