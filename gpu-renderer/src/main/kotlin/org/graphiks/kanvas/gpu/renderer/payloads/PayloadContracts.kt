package org.graphiks.kanvas.gpu.renderer.payloads

/** Gathers per-draw and per-pass payload values. */
class GPUPayloadGatherer

/** Plan for gathering payload values for a route. */
class GPUPayloadGatherPlan

/** Write plan for one payload value or block. */
class GPUPayloadWritePlan

/** Material payload block consumed by render WGSL. */
class GPUMaterialPayload

/** Pass-local payload slot identifier. */
class GPUPayloadSlotID

/** Uniform payload block descriptor. */
class GPUUniformPayloadBlock

/** Uniform payload slot descriptor. */
class GPUUniformPayloadSlot

/** Resource binding block descriptor. */
class GPUResourceBindingBlock

/** Resource binding slot descriptor. */
class GPUResourceBindingSlot

/** Binding plan from payload slots to GPU bindings. */
class GPUPayloadBindingPlan

/** Upload plan for payload buffers and bindings. */
class GPUPayloadUploadPlan

/** Fingerprint for pass-local payload deduplication. */
class GPUPayloadFingerprint

/** Storage route for gradient stop payloads. */
class GPUGradientPayloadStore

/** Reference from a draw to a gathered payload slot. */
class GPUDrawPayloadRef

/** Diagnostic emitted by payload gathering or packing. */
class GPUPayloadDiagnostic
