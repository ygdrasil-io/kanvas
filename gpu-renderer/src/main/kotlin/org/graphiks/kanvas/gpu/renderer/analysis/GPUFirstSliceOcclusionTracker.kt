package org.graphiks.kanvas.gpu.renderer.analysis

/**
 * Concrete occlusion tracker for first-slice solid rect/rrect draws.
 * Proofs are produced only when a later opaque solid fill fully covers an earlier record
 * with the same bounds.
 */
class GPUFirstSliceOcclusionTracker : GPUOcclusionTracker {
    override fun prove(records: List<GPUDrawAnalysisRecord>): List<GPUOcclusionProof> {
        if (records.size < 2) return emptyList()

        val proofs = mutableListOf<GPUOcclusionProof>()

        for (i in records.indices) {
            val hidden = records[i]
            for (j in i + 1 until records.size) {
                val covering = records[j]
                if (!covering.isSolidOpaqueFill()) continue
                if (hidden.boundsHash == covering.boundsHash &&
                    hidden.recordId !in proofs.provenHiddenRecordIds()
                ) {
                    proofs.add(
                        GPUOcclusionProof(
                            proofId = "occlusion.proof.${hidden.recordId}.by.${covering.recordId}",
                            hiddenRecordId = hidden.recordId,
                            coveringRecordId = covering.recordId,
                            proofClass = "solid-opaque-bounds-cover",
                            boundsProofHash = hidden.boundsHash,
                            reasonCode = "occluded.by.solid.opaque.fill.same.bounds",
                        ),
                    )
                    break
                }
            }
        }

        return proofs
    }

    private fun GPUDrawAnalysisRecord.isSolidOpaqueFill(): Boolean =
        "solid" in routeDecisionLabel

    private fun List<GPUOcclusionProof>.provenHiddenRecordIds(): Set<String> =
        map { it.hiddenRecordId }.toSet()
}
