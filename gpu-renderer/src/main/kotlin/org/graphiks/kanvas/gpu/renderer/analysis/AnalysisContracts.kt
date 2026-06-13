package org.graphiks.kanvas.gpu.renderer.analysis

/** Compact stable sort-key value. */
@JvmInline
value class SortKey(val value: Long)

/** Immutable draw analysis for one recording scope. */
data class GPUDrawAnalysis(
    val analysisId: String,
    val records: List<GPUDrawAnalysisRecord>,
    val dependencies: List<GPUAnalysisDependency>,
    val occlusionProofs: List<GPUOcclusionProof>,
    val diagnostics: List<GPUAnalysisDiagnostic>,
)

/** One analyzed draw command record. */
data class GPUDrawAnalysisRecord(
    val recordId: String,
    val commandIdValue: Int,
    val commandFamily: String,
    val boundsHash: String,
    val routeDecisionLabel: String,
    val materialKeyHash: String,
    val renderStepCandidates: List<String>,
    val sortKey: SortKey,
    val diagnostics: List<GPUAnalysisDiagnostic> = emptyList(),
)

/** Analysis-time decision for a draw record. */
sealed interface GPUDrawAnalysisDecision {
    /** Draw remains a route candidate. */
    data class Candidate(
        val recordId: String,
        val routeDecisionLabel: String,
        val resourceDeclarations: List<String>,
        val renderStepCandidates: List<String>,
    ) : GPUDrawAnalysisDecision

    /** Draw is culled by bounds or clip evidence. */
    data class Cull(val recordId: String, val reasonCode: String) : GPUDrawAnalysisDecision

    /** Draw is discarded by analysis as no-op. */
    data class Discard(val recordId: String, val reasonCode: String) : GPUDrawAnalysisDecision

    /** Draw is refused by analysis. */
    data class Refuse(val recordId: String, val diagnostic: GPUAnalysisDiagnostic) : GPUDrawAnalysisDecision
}

/** Occlusion analysis service contract. */
interface GPUOcclusionTracker {
    /** Proves occlusion without mutating recordings. */
    fun prove(records: List<GPUDrawAnalysisRecord>): List<GPUOcclusionProof> = TODO("Wire GPUOcclusionTracker to concrete occlusion analysis")
}

/** Proof that one analyzed draw is hidden or constrained. */
data class GPUOcclusionProof(
    val proofId: String,
    val hiddenRecordId: String,
    val coveringRecordId: String,
    val proofClass: String,
    val boundsProofHash: String,
    val reasonCode: String,
)

/** Analysis dependency between draw records. */
data class GPUAnalysisDependency(
    val fromRecordId: String,
    val toRecordId: String,
    val kind: String,
    val barrierGeneration: Long,
    val reasonCode: String,
)

/** Diagnostic emitted by draw analysis. */
data class GPUAnalysisDiagnostic(
    val code: String,
    val recordId: String? = null,
    val decisionId: String? = null,
    val terminal: Boolean,
)
