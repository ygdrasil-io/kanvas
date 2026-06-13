package org.graphiks.kanvas.gpu.renderer.coordinates

/** Coordinate space label used by transform and bounds plans. */
class GPUCoordinateSpace

/** Classified transform chain accepted or refused by a route. */
class GPUTransformPlan

/** Inverse-transform availability and precision plan. */
class GPUInverseTransformPlan

/** Pixel-grid alignment and snapping policy. */
class GPUPixelGridPlan

/** Conservative bounds plan for a command, layer, or resource. */
class GPUBoundsPlan

/** Proof record for conservative bounds correctness. */
class GPUBoundsProof

/** Rounding policy used when moving between float and integer bounds. */
class GPURoundingPlan

/** Proof that clip reduction preserves correctness. */
class GPUClipReductionProof

/** Payload contract for coordinate data consumed by WGSL. */
class GPUCoordinatePayloadPlan

/** Diagnostic emitted by transform or bounds planning. */
class GPUTransformDiagnostic
