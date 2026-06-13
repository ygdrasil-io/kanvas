package org.graphiks.kanvas.gpu.renderer.analysis

/** Immutable analysis result for one recording snap. */
class GPUDrawAnalysis

/** Per-command analysis record retained for diagnostics and evidence. */
class GPUDrawAnalysisRecord

/** Pre-materialization analysis decision for a command or invocation. */
class GPUDrawAnalysisDecision

/** Conservative occlusion tracker contract. */
class GPUOcclusionTracker

/** Proof that an occlusion or cull decision is correct. */
class GPUOcclusionProof

/** Deterministic sort key for legal draw reordering windows. */
class SortKey

/** Dependency fact discovered during draw analysis. */
class GPUAnalysisDependency

/** Diagnostic emitted by draw analysis. */
class GPUAnalysisDiagnostic
