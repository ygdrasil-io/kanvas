package org.graphiks.kanvas.gpu.renderer.execution

/** Execution context that owns access to the selected GPU facade. */
class GPUExecutionContext

/** Command scope used while encoding GPU work. */
class GPUCommandScope

/** Submission record for encoded GPU commands. */
class GPUCommandSubmission

/** Surface or offscreen target selected for execution. */
class GPUSurfaceTarget

/** Descriptor for a surface target before acquiring frame resources. */
class GPUSurfaceTargetDescriptor

/** Frame submission record including target and device-generation facts. */
class GPUFrameSubmission

/** Readback request descriptor. */
class GPUReadbackRequest

/** Readback result descriptor. */
class GPUReadbackResult

/** Device-generation marker used to invalidate stale resources. */
class GPUDeviceGeneration

/** Diagnostic emitted by execution or submission. */
class GPUExecutionDiagnostic
