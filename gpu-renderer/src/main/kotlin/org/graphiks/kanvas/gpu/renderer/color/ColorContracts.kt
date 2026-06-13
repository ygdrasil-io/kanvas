package org.graphiks.kanvas.gpu.renderer.color

/** Complete color-management route plan for a command or target. */
class GPUColorManagementPlan

/** Typed color value metadata including color space and alpha domain. */
class GPUColorValueSpec

/** Stable descriptor for a color space. */
class GPUColorSpaceDescriptor

/** Stable descriptor for an ICC, CICP, or related profile source. */
class GPUColorProfileDescriptor

/** Plan for converting color values between spaces. */
class GPUColorConversionPlan

/** Concrete transform plan used by CPU oracle or WGSL helpers. */
class GPUColorTransformPlan

/** Working color-space decision for material and filter evaluation. */
class GPUWorkingColorSpacePlan

/** Gradient interpolation and stop color behavior plan. */
class GPUGradientColorPlan

/** Uniform layout and payload plan for runtime color values. */
class GPUColorUniformPlan

/** HDR handling and refusal plan. */
class GPUHDRColorPlan

/** Gainmap handling and refusal plan. */
class GPUGainmapPlan

/** Final color store conversion plan. */
class GPUColorStorePlan

/** Cache key and invalidation facts for color transforms. */
class GPUColorCachePlan

/** Diagnostic emitted by color-management planning. */
class GPUColorDiagnostic
