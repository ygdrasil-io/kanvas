package org.graphiks.kanvas.gpu.renderer.routing

/** Top-level route kind selected for a command or task. */
class GPURouteKind

/** GPU-native product route marker. */
class GPUNativeRoute

/** CPU-prepared typed artifact route consumed by GPU work. */
class CPUPreparedGPURoute

/** CPU reference route used only for oracle evidence. */
class CPUReferenceOnlyRoute

/** Stable product refusal result when no accepted route exists. */
class RefuseDiagnostic

/** Route decision emitted before analysis or materialization continues. */
class GPURouteDecision

/** Deterministic preimage for route diagnostics. */
class GPURoutePreimage

/** Registry of typed CPU-prepared GPU artifact kinds. */
class CPUPreparedGPUArtifactRegistry

/** Descriptor for a CPU-prepared GPU artifact kind. */
class CPUPreparedGPUArtifactDescriptor

/** Stable key for a CPU-prepared GPU artifact. */
class CPUPreparedGPUArtifactKey

/** Diagnostic emitted by route selection. */
class GPURouteDiagnostic
