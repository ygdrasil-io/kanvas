package org.graphiks.kanvas.gpu.renderer.layers

/** High-level layer or saveLayer semantic plan. */
class GPULayerPlan

/** Executable lowering plan for a layer or saveLayer. */
class GPULayerExecutionPlan

/** Stable layer scope identifier. */
class GPULayerScopeID

/** Captured saveLayer record. */
class GPULayerSaveRecord

/** Captured restore plan for a layer. */
class GPULayerRestorePlan

/** Conservative layer bounds plan. */
class GPULayerBoundsPlan

/** Offscreen or parent target plan for a layer. */
class GPULayerTargetPlan

/** Layer initialization plan for clear, discard, or previous-content load. */
class GPULayerInitializationPlan

/** Backdrop capture and filtering plan. */
class GPULayerBackdropPlan

/** Layer source input plan. */
class GPULayerSourcePlan

/** Filter-chain plan attached to a layer. */
class GPULayerFilterChainPlan

/** Composite plan used when restoring a layer to its parent. */
class GPULayerCompositePlan

/** Proof that a layer can be elided without changing output. */
class GPULayerElisionPlan

/** Task plan generated for layer execution. */
class GPULayerTaskPlan

/** Resource descriptor plan for layer targets and bindings. */
class GPULayerResourcePlan

/** Ordering token for layer dependencies. */
class GPULayerOrderingToken

/** Cache plan for reusable layer intermediates. */
class GPULayerCachePlan

/** Budget policy for layer allocation and nesting. */
class GPULayerBudgetPolicy

/** Low-level immutable draw layer used by pass planning. */
class GPUDrawLayer

/** Planner that maps analysis records into draw layers. */
class GPUDrawLayerPlanner

/** Diagnostic emitted by layer planning. */
class GPULayerDiagnostic
