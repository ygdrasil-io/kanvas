package org.graphiks.kanvas.gpu.evidence.runner

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

/** Product facts observed before a scene records its backend task list. */
data class SceneRecordingContext(
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val frameOrdinal: Long,
    val readbackRequestId: GPUReadbackRequestID,
)

/** Result of scene preparation, before any backend submission. */
sealed interface ScenePreparation {
    data class Recorded(
        val routeId: String,
        val taskList: GPUTaskList,
        val diagnostics: List<String>,
    ) : ScenePreparation {
        init {
            require(routeId.isNotBlank()) { "ScenePreparation.Recorded.routeId must not be blank" }
        }
    }

    data class Refused(
        val stableReasonCode: String,
        val message: String,
        val diagnostics: List<String>,
    ) : ScenePreparation {
        init {
            require(stableReasonCode.isNotBlank()) {
                "ScenePreparation.Refused.stableReasonCode must not be blank"
            }
            require(message.isNotBlank()) { "ScenePreparation.Refused.message must not be blank" }
        }
    }
}

/** Backend-independent scene recording function. */
fun interface SceneProgram {
    fun prepare(context: SceneRecordingContext): ScenePreparation
}

/** Product route identity carried through both recorded and refused preparation. */
interface RoutedSceneProgram : SceneProgram {
    val routeId: String
}
