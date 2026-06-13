package org.graphiks.kanvas.gpu.renderer.filters

/** High-level filter or effect graph plan. */
class GPUFilterPlan

/** Descriptor for an image-filter DAG. */
class GPUFilterGraphDescriptor

/** Stable filter node identifier. */
class GPUFilterNodeID

/** Descriptor for one filter graph node. */
class GPUFilterNodeDescriptor

/** Accepted or refused plan for one filter node. */
class GPUFilterNodePlan

/** Route selected for one filter node. */
class GPUFilterNodeRoute

/** Input binding plan for a filter node. */
class GPUFilterInputPlan

/** Source input plan for a filter graph. */
class GPUFilterSourcePlan

/** Backdrop input plan for backdrop filters. */
class GPUFilterBackdropPlan

/** Bounds plan for filter expansion and cropping. */
class GPUFilterBoundsPlan

/** Crop rectangle plan for a filter node. */
class GPUFilterCropPlan

/** Tile-mode plan for filter sampling. */
class GPUFilterTilePlan

/** Sampling plan for filter inputs. */
class GPUFilterSamplingPlan

/** Intermediate resource descriptor for filter execution. */
class GPUFilterIntermediatePlan

/** Render-pass filter node plan. */
class GPUFilterRenderNodePlan

/** Compute-pass filter node plan. */
class GPUFilterComputeNodePlan

/** Kernel descriptor for compute or render filter execution. */
class GPUFilterKernelPlan

/** Registered runtime-effect filter node plan. */
class GPUFilterRuntimeEffectPlan

/** Color-filter placement plan inside a filter DAG. */
class GPUFilterColorPlan

/** Ordering token for filter node dependencies. */
class GPUFilterOrderingToken

/** Cache plan for filter graph or node outputs. */
class GPUFilterCachePlan

/** Budget policy for filter intermediates and nodes. */
class GPUFilterBudgetPolicy

/** Typed filter intermediate artifact consumed by GPU work. */
class FilterIntermediateArtifact

/** Diagnostic emitted by filter planning. */
class GPUFilterDiagnostic
