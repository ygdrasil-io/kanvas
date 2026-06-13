package org.graphiks.kanvas.gpu.renderer.recording

/** Stable recording identifier. */
@JvmInline
value class GPURecordingID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURecordingID.value must not be blank" }
    }
}

/** Shared recorder scope identity. */
@JvmInline
value class GPUSharedScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUSharedScope.value must not be blank" }
    }
}

/** Recorder-local scope identity. */
@JvmInline
value class GPURecorderScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURecorderScope.value must not be blank" }
    }
}

/** Frame-local scope identity. */
@JvmInline
value class GPUFrameScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFrameScope.value must not be blank" }
    }
}

/** Atlas mutation scope identity. */
@JvmInline
value class GPUAtlasScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUAtlasScope.value must not be blank" }
    }
}

/** Compatibility key used to determine whether a recording may replay. */
data class GPURecordingCompatibilityKey(
    val keyHash: String,
    val commandShapeVersion: Int,
    val dictionaryVersion: String,
    val runtimeRegistrySnapshot: String,
    val capabilityClass: String,
    val targetFormatClass: String,
    val resourceTopologyClass: String,
    val replayPolicy: String,
)

/** Immutable GPU recording contract. */
data class GPURecording(
    val recordingId: GPURecordingID,
    val compatibilityKey: GPURecordingCompatibilityKey,
    val analysisHash: String,
    val taskList: GPUTaskList,
    val routeDiagnostics: List<String>,
    val featureAssumptions: List<String>,
)

/** Recording with explicit order and target scope. */
data class GPUOrderedRecording(
    val recordingId: GPURecordingID,
    val insertionOrder: Long,
    val targetScope: GPUFrameScope,
    val barrierClass: String,
)

/** Recorder contract for collecting normalized commands. */
interface GPURecorder {
    /** Records one normalized command label into the current scope. */
    fun record(commandLabel: String): Unit = TODO("Wire GPURecorder to normalized command capture")

    /** Closes the recorder and returns an immutable recording. */
    fun close(): GPURecording = TODO("Wire GPURecorder.close to analysis and task-list assembly")
}

/** Task emitted by recording and planning. */
sealed interface GPUTask {
    /** Resource preparation task. */
    data class PrepareResources(val taskId: String, val resourcePlanLabels: List<String>) : GPUTask

    /** Draw pass task. */
    data class DrawPass(val taskId: String, val passLabel: String) : GPUTask

    /** Compute task. */
    data class Compute(val taskId: String, val programLabel: String) : GPUTask

    /** Copy task. */
    data class Copy(val taskId: String, val copyLabel: String) : GPUTask

    /** Upload task. */
    data class Upload(val taskId: String, val uploadLabel: String) : GPUTask

    /** Barrier task. */
    data class Barrier(val taskId: String, val reasonCode: String) : GPUTask

    /** Refused task. */
    data class Refused(val taskId: String, val diagnostic: GPURecordingDiagnostic) : GPUTask
}

/** Ordered task list with dependencies. */
data class GPUTaskList(
    val tasks: List<GPUTask>,
    val dependencies: List<GPUTaskDependency>,
    val phaseOrder: List<String>,
    val diagnostics: List<GPURecordingDiagnostic> = emptyList(),
)

/** Dependency between planned tasks. */
data class GPUTaskDependency(
    val fromTaskId: String,
    val toTaskId: String,
    val dependencyKind: String,
    val useTokenLabel: String? = null,
    val reasonCode: String,
)

/** Diagnostic emitted by recording. */
data class GPURecordingDiagnostic(
    val code: String,
    val recordingId: GPURecordingID? = null,
    val taskId: String? = null,
    val terminal: Boolean,
)
