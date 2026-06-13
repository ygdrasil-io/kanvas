package org.graphiks.kanvas.gpu.renderer.recording

/** Recorder entry point for normalized GPU renderer commands. */
class GPURecorder

/** Immutable recording produced by a closed recorder. */
class GPURecording

/** Compatibility key that decides whether a recording can be replayed. */
class GPURecordingCompatibilityKey

/** Stable recording identifier. */
class GPURecordingID

/** Recording plus explicit target insertion order token. */
class GPUOrderedRecording

/** Shared renderer scope for dictionary, cache, and capability facts. */
class GPUSharedScope

/** Recorder-local lifetime scope. */
class GPURecorderScope

/** Frame-local lifetime scope. */
class GPUFrameScope

/** Atlas-local lifetime scope. */
class GPUAtlasScope

/** Abstract task in a GPU recording task list. */
class GPUTask

/** Ordered task list produced from a recording. */
class GPUTaskList

/** Dependency edge between GPU recording tasks. */
class GPUTaskDependency

/** Diagnostic emitted by recording construction. */
class GPURecordingDiagnostic
