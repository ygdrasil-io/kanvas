package org.graphiks.kanvas.gpu.renderer.materials

/** Captured paint descriptor after legacy API adaptation. */
class GPUPaintDescriptor

/** Ordered paint-pipeline plan before material key derivation. */
class GPUPaintPipelinePlan

/** One stage in the paint-pipeline evaluation order. */
class GPUPaintStagePlan

/** Stable paint-stage evaluation order. */
class GPUPaintEvaluationOrder

/** Descriptor for a material source such as solid, gradient, image, or runtime effect. */
class GPUMaterialSourceDescriptor

/** Stable material-source family identity. */
class GPUMaterialSourceKind

/** Accepted or refused material-source plan. */
class GPUMaterialSourcePlan

/** Solid color source plan. */
class GPUSolidColorPlan

/** Gradient source plan. */
class GPUGradientPlan

/** Gradient family identity. */
class GPUGradientKind

/** Gradient geometry descriptor and evaluation facts. */
class GPUGradientGeometryPlan

/** Gradient stop normalization and validation plan. */
class GPUGradientStopPlan

/** Gradient stop storage route and budget plan. */
class GPUGradientStopStorePlan

/** Material tile-mode identity. */
class GPUMaterialTileMode

/** Material sampling plan for image and filtered sources. */
class GPUMaterialSamplingPlan

/** Image shader material-source plan. */
class GPUImageShaderPlan

/** Local matrix shader wrapper plan. */
class GPULocalMatrixShaderPlan

/** Shader-side blend source plan. */
class GPUShaderBlendSourcePlan

/** Paint color value plan before color-management lowering. */
class GPUPaintColorPlan

/** Durable material identity for render WGSL assembly. */
class MaterialKey

/** Dictionary that interns material keys and expands snippet trees. */
class GPUMaterialDictionary

/** Dictionary-local program identifier for an interned material key. */
class GPUMaterialProgramID

/** Read-only material lowering context equivalent to Graphite key context. */
class GPUMaterialLoweringContext

/** Explicit root set produced by material dictionary expansion. */
class GPUMaterialRootSet

/** WGSL material snippet registered in the material dictionary. */
class WGSLSnippet

/** Stable dictionary-local snippet identifier. */
class WGSLSnippetID

/** Node in the expanded material snippet tree. */
class WGSLSnippetNode

/** Plan for assembling material snippet roots into a WGSL module input. */
class GPUMaterialAssemblyPlan

/** Payload handoff plan contributed by material-source planning. */
class GPUMaterialSourcePayloadPlan

/** Diagnostic emitted by material-source planning. */
class GPUMaterialSourceDiagnostic

/** Diagnostic emitted by paint-pipeline planning. */
class GPUPaintPipelineDiagnostic
