package org.graphiks.kanvas.gpu.renderer.geometry

/** Shape descriptor consumed by geometry planning. */
class GPUShapeDescriptor

/** Path descriptor consumed by path and stroke planning. */
class GPUPathDescriptor

/** Stroke style descriptor consumed by stroke expansion planning. */
class GPUStrokeDescriptor

/** Accepted or refused geometry plan. */
class GPUGeometryPlan

/** Geometry route selected for a draw family. */
class GPUGeometryRoute

/** Bounds plan for a path route. */
class GPUPathBoundsPlan

/** Stroke expansion plan before render-step selection. */
class GPUStrokeExpansionPlan

/** Stencil-cover execution plan for geometry routes. */
class GPUStencilCoverPlan

/** CPU-prepared geometry artifact plan consumed by GPU work. */
class GPUPreparedGeometryPlan

/** Geometry render-step plan for pass construction. */
class GPUGeometryRenderStepPlan

/** Path atlas strategy and entry plan. */
class GPUPathAtlasPlan

/** Coverage atlas strategy and entry plan. */
class GPUCoverageAtlasPlan

/** Atlas selection and eviction policy. */
class GPUAtlasPolicy

/** Atlas budget policy for capacity and upload limits. */
class GPUAtlasBudgetPolicy

/** Reference to an atlas entry used as payload/resource fact. */
class GPUAtlasEntryRef

/** Atlas mutation plan for upload, reuse, or eviction. */
class GPUAtlasMutationPlan

/** Typed CPU-prepared path atlas artifact. */
class PathAtlasArtifact

/** Typed CPU-prepared coverage mask artifact. */
class CoverageMaskArtifact

/** Typed CPU-prepared geometry artifact. */
class PrecomputedGeometryArtifact

/** Diagnostic emitted by geometry route planning. */
class GPUGeometryDiagnostic
