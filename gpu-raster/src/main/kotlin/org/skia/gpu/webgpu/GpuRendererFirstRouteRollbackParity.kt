package org.skia.gpu.webgpu

/** Snapshot used by the controlled first-route rollback/parity validator. */
internal data class GpuRendererFirstRouteRollbackSnapshot(
    val label: String,
    val result: GpuRendererShadowResult,
    val pixelChecksum: String,
)

/** Rollback/parity evidence for the controlled solid FillRect product flag. */
internal data class GpuRendererFirstRouteRollbackParityReport(
    val gatePassed: Boolean,
    val parityMatched: Boolean,
    val rollbackRestoredLegacy: Boolean,
    val unsupportedVariantCovered: Boolean,
    val routeScope: String,
    val productRouteActivated: Boolean,
    val releaseBlocking: Boolean,
    val readinessDelta: Double,
    val diagnostics: List<String>,
    val transcript: String,
)

/** Validates that the controlled first-route flag can roll back to legacy behavior. */
internal object GpuRendererFirstRouteRollbackParityValidator {
    public fun validate(
        legacyBefore: GpuRendererFirstRouteRollbackSnapshot,
        productFlagged: GpuRendererFirstRouteRollbackSnapshot,
        legacyRollback: GpuRendererFirstRouteRollbackSnapshot,
        unsupportedVariant: GpuRendererFirstRouteRollbackSnapshot,
    ): GpuRendererFirstRouteRollbackParityReport {
        val diagnostics = mutableListOf<String>()
        val routeScope = productFlagged.result.productFlag.routeScope
        val parityMatched =
            legacyBefore.pixelChecksum == productFlagged.pixelChecksum &&
                legacyBefore.pixelChecksum == legacyRollback.pixelChecksum
        val rollbackRestoredLegacy =
            legacyRollback.result.status == GpuRendererShadowHandoffStatus.Skipped &&
                legacyRollback.result.mode == GpuRendererShadowMode.Disabled &&
                !legacyRollback.result.productFlag.enabled &&
                legacyBefore.pixelChecksum == legacyRollback.pixelChecksum
        val unsupportedVariantCovered =
            unsupportedVariant.result.status == GpuRendererShadowHandoffStatus.Refused &&
                unsupportedVariant.result.mode == GpuRendererShadowMode.ProductFlag &&
                unsupportedVariant.result.diagnosticCode == "unsupported.adapter.paint_style" &&
                unsupportedVariant.result.normalizedCommand == null

        if (legacyBefore.result.status != GpuRendererShadowHandoffStatus.Skipped) {
            diagnostics += "rollback.parity.legacy_before_not_skipped"
        }
        if (legacyBefore.result.mode != GpuRendererShadowMode.Disabled || legacyBefore.result.productFlag.enabled) {
            diagnostics += "rollback.parity.legacy_before_flagged"
        }
        if (productFlagged.result.status != GpuRendererShadowHandoffStatus.ProductFlagged) {
            diagnostics += "rollback.parity.product_flag_not_accepted"
        }
        if (productFlagged.result.mode != GpuRendererShadowMode.ProductFlag || !productFlagged.result.productFlag.enabled) {
            diagnostics += "rollback.parity.product_flag_missing"
        }
        if (routeScope != "solid-fill-rect") {
            diagnostics += "rollback.parity.route_scope_changed"
        }
        if (!productFlagged.result.legacyRouteAvailable) {
            diagnostics += "rollback.parity.legacy_route_unavailable"
        }
        if (!parityMatched) {
            diagnostics += "rollback.parity.checksum_mismatch"
        }
        if (!rollbackRestoredLegacy) {
            diagnostics += "rollback.parity.rollback_not_legacy"
        }
        if (!unsupportedVariantCovered) {
            diagnostics += "rollback.parity.unsupported_variant_missing"
        }

        val forbiddenMarkers = listOf(
            "GPUCommandSubmission.Submitted",
            "execution.submission:submitted",
            "GPUReadbackResult.Completed",
            "diagnostic-webgpu-first-route-pm-evidence",
        )
        val snapshots = listOf(legacyBefore, productFlagged, legacyRollback, unsupportedVariant)
        for (marker in forbiddenMarkers) {
            if (snapshots.any { snapshot -> snapshot.result.dump().contains(marker) }) {
                diagnostics += "rollback.parity.forbidden_marker:$marker"
            }
        }

        val gatePassed = diagnostics.isEmpty()
        val transcript = buildTranscript(
            gatePassed = gatePassed,
            parityMatched = parityMatched,
            rollbackRestoredLegacy = rollbackRestoredLegacy,
            unsupportedVariantCovered = unsupportedVariantCovered,
            routeScope = routeScope,
            diagnostics = diagnostics,
            snapshots = snapshots,
        )

        return GpuRendererFirstRouteRollbackParityReport(
            gatePassed = gatePassed,
            parityMatched = parityMatched,
            rollbackRestoredLegacy = rollbackRestoredLegacy,
            unsupportedVariantCovered = unsupportedVariantCovered,
            routeScope = routeScope,
            productRouteActivated = false,
            releaseBlocking = false,
            readinessDelta = 0.0,
            diagnostics = diagnostics.toList(),
            transcript = transcript,
        )
    }

    private fun buildTranscript(
        gatePassed: Boolean,
        parityMatched: Boolean,
        rollbackRestoredLegacy: Boolean,
        unsupportedVariantCovered: Boolean,
        routeScope: String,
        diagnostics: List<String>,
        snapshots: List<GpuRendererFirstRouteRollbackSnapshot>,
    ): String =
        buildString {
            appendLine(
                "rollbackParity v=1 gatePassed=$gatePassed routeScope=$routeScope " +
                    "parityMatched=$parityMatched rollbackRestoredLegacy=$rollbackRestoredLegacy " +
                    "unsupportedVariantCovered=$unsupportedVariantCovered productRouteActivated=false " +
                    "releaseBlocking=false readinessDelta=0.0",
            )
            for (snapshot in snapshots) {
                appendLine(snapshot.transcriptLine())
            }
            append("diagnostics=")
            append(if (diagnostics.isEmpty()) "none" else diagnostics.joinToString(","))
        }

    private fun GpuRendererFirstRouteRollbackSnapshot.transcriptLine(): String =
        "$label:status=${result.status.transcriptLabel()} mode=${result.mode.transcriptLabel()} " +
            "route=${result.routeLabel} diagnostic=${result.diagnosticCode ?: "none"} " +
            "checksum=$pixelChecksum legacyRouteAvailable=${result.legacyRouteAvailable} " +
            "productFlag=${result.productFlag.enabled}:${result.productFlag.routeScope} dump=${result.dump()}"

    private fun GpuRendererShadowHandoffStatus.transcriptLabel(): String =
        when (this) {
            GpuRendererShadowHandoffStatus.ProductFlagged -> "product-flagged"
            else -> name.lowercase()
        }

    private fun GpuRendererShadowMode.transcriptLabel(): String =
        when (this) {
            GpuRendererShadowMode.Disabled -> "disabled"
            GpuRendererShadowMode.Shadow -> "shadow"
            GpuRendererShadowMode.ProductFlag -> "product-flag"
        }
}
