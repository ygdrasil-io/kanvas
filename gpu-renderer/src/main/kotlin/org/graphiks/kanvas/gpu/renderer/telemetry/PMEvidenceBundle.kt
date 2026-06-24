package org.graphiks.kanvas.gpu.renderer.telemetry

/** M23 program-management evidence bundle for readiness reviews. */
data class M23PMEvidenceBundle(
    val bundleId: String,
    val allFamiliesActivated: Boolean,
    val gatesGreen: Boolean,
    val rollbackTested: Boolean,
    val exportTimestamp: String = "",
    val nonClaims: List<String> = listOf(
        "no-release-blocking-gate",
        "no-readiness-delta",
        "no-product-activation",
    ),
) {
    init {
        require(bundleId.isNotBlank()) { "M23 PM evidence bundleId must not be blank" }
    }

    /** Exports the bundle as evidence text lines. */
    fun exportBundleLines(): List<String> = listOf(
        "pm:m23-evidence-bundle id=$bundleId allFamiliesActivated=$allFamiliesActivated " +
            "gatesGreen=$gatesGreen rollbackTested=$rollbackTested exportTimestamp=$exportTimestamp",
        "pm:m23-evidence-bundle status=${if (isReady) "ready" else "not-ready"} " +
            "families=$allFamiliesActivated gates=$gatesGreen rollback=$rollbackTested",
        "nonclaim:${nonClaims.joinToString(" ")}",
    )

    val isReady: Boolean get() = allFamiliesActivated && gatesGreen && rollbackTested
}
