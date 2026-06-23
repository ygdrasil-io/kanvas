package org.skia.gpu.webgpu

/** Evidence supplied before one concrete legacy gpu-raster slice may be retired. */
internal data class GpuRendererLegacyRetirementEvidence(
    val family: GpuRendererLegacyRouteFamily,
    val acceptedReplacementTicket: String,
    val replacementAccepted: Boolean,
    val activationDecisionId: String,
    val activationDecisionAccepted: Boolean,
    val rollbackEvidenceId: String,
    val rollbackValidationHash: String,
    val pmEvidenceRowId: String,
    val oldPathUsageEvidenceId: String,
    val oldPathUsageCount: Int,
    val scopeLabel: String,
    val archivedEvidenceOnly: Boolean,
    val genericMigrationGate: Boolean,
    val broadDeletion: Boolean,
    val productRouteActivated: Boolean,
    val releaseBlocking: Boolean,
    val readinessDelta: Double,
    val shadowParityAccepted: Boolean,
)

/** Per-family gate status before one legacy route slice can be removed. */
internal enum class GpuRendererLegacyRetirementGateStatus {
    Accepted,
    MissingEvidence,
    Refused,
}

/** Gate result for one legacy route-family retirement candidate. */
internal data class GpuRendererLegacyRetirementFamilyGate(
    val family: GpuRendererLegacyRouteFamily,
    val status: GpuRendererLegacyRetirementGateStatus,
    val legacyRouteActive: Boolean,
    val retirementAuthorized: Boolean,
    val acceptedReplacementTicket: String,
    val activationDecisionId: String?,
    val rollbackEvidenceId: String?,
    val rollbackValidationHash: String?,
    val pmEvidenceRowId: String?,
    val oldPathUsageEvidenceId: String?,
    val oldPathUsageCount: Int?,
    val scopeLabel: String?,
    val diagnostics: List<String>,
) {
    init {
        require(legacyRouteActive) {
            "GpuRendererLegacyRetirementFamilyGate only authorizes retirement; it does not remove routes"
        }
    }

    /** Stable PM/debug dump for this retirement gate row. */
    fun dumpLine(): String =
        "legacy-retirement:family family=${family.familyId} status=${status.dumpLabel()} " +
            "legacyRouteActive=$legacyRouteActive retirementAuthorized=$retirementAuthorized " +
            "replacement=$acceptedReplacementTicket activation=${activationDecisionId ?: "missing"} " +
            "rollback=${rollbackEvidenceId ?: "missing"} rollbackHash=${rollbackValidationHash ?: "missing"} " +
            "pmRow=${pmEvidenceRowId ?: expectedPmRow()} oldPathUsage=${oldPathUsageCount ?: "missing"} " +
            "oldPathEvidence=${oldPathUsageEvidenceId ?: "missing"} scope=${scopeLabel ?: expectedScope()} " +
            "diagnostic=${diagnostics.ifEmpty { listOf("none") }.joinToString(",")}"

    private fun expectedPmRow(): String = "gpu-renderer.legacy-retirement.${family.familyId}"

    private fun expectedScope(): String = "legacy.${family.familyId}.retirement"
}

/** Full M10-003 legacy retirement gate report. */
internal data class GpuRendererLegacyRetirementGateReport(
    val familyGates: List<GpuRendererLegacyRetirementFamilyGate>,
) {
    val acceptedFamilyCount: Int =
        familyGates.count { gate -> gate.status == GpuRendererLegacyRetirementGateStatus.Accepted }

    val missingFamilyCount: Int =
        familyGates.count { gate -> gate.status == GpuRendererLegacyRetirementGateStatus.MissingEvidence }

    val refusedFamilyCount: Int =
        familyGates.count { gate -> gate.status == GpuRendererLegacyRetirementGateStatus.Refused }

    val gatePassed: Boolean =
        familyGates.all { gate -> gate.status == GpuRendererLegacyRetirementGateStatus.Accepted }

    val productRouteActivated: Boolean = false

    val releaseBlocking: Boolean = false

    val readinessDelta: Double = 0.0

    /** Stable report dump for PM and review evidence. */
    fun dumpLines(): List<String> =
        listOf(
            "legacy-retirement row=gpu-renderer.legacy-retirement " +
                "families=${familyGates.size} accepted=$acceptedFamilyCount missing=$missingFamilyCount " +
                "refused=$refusedFamilyCount productRouteActivated=$productRouteActivated " +
                "releaseBlocking=$releaseBlocking readinessDelta=$readinessDelta gatePassed=$gatePassed",
        ) + familyGates.map { gate -> gate.dumpLine() } +
            listOf(
                "legacy-retirement:nonclaim productRouteActivated=false releaseBlocking=false " +
                    "readinessDelta=0.0 broadDeletion=false archivedPlansActive=false",
            )
}

/** M10-003 validator for route-family-specific legacy retirement evidence. */
internal object GpuRendererLegacyRetirementGate {
    fun evaluate(
        evidence: List<GpuRendererLegacyRetirementEvidence>,
    ): GpuRendererLegacyRetirementGateReport {
        val byFamily = evidence.groupBy { item -> item.family }
        val sharedEvidenceKeys = SharedRetirementEvidenceKeys(
            fullRows = evidence.sharedKeysBy { item -> item.retirementEvidenceKey() },
            activationDecisions = evidence.sharedKeysBy { item -> item.activationDecisionId },
            rollbackEvidence = evidence.sharedKeysBy { item -> item.rollbackEvidenceId },
            oldPathUsageEvidence = evidence.sharedKeysBy { item -> item.oldPathUsageEvidenceId },
        )

        val gates = GpuRendererLegacyRouteFamily.values().map { family ->
            val familyEvidence = byFamily[family].orEmpty()
            when (familyEvidence.size) {
                0 -> missingGate(family)
                1 -> evaluateFamilyEvidence(family, familyEvidence.single(), sharedEvidenceKeys)
                else -> duplicateGate(family, familyEvidence)
            }
        }
        return GpuRendererLegacyRetirementGateReport(familyGates = gates)
    }

    private fun missingGate(family: GpuRendererLegacyRouteFamily): GpuRendererLegacyRetirementFamilyGate =
        GpuRendererLegacyRetirementFamilyGate(
            family = family,
            status = GpuRendererLegacyRetirementGateStatus.MissingEvidence,
            legacyRouteActive = true,
            retirementAuthorized = false,
            acceptedReplacementTicket = family.defaultReplacementTicket,
            activationDecisionId = null,
            rollbackEvidenceId = null,
            rollbackValidationHash = null,
            pmEvidenceRowId = null,
            oldPathUsageEvidenceId = null,
            oldPathUsageCount = null,
            scopeLabel = null,
            diagnostics = listOf("legacy.retirement.missing_promoted_replacement_evidence"),
        )

    private fun duplicateGate(
        family: GpuRendererLegacyRouteFamily,
        evidence: List<GpuRendererLegacyRetirementEvidence>,
    ): GpuRendererLegacyRetirementFamilyGate =
        GpuRendererLegacyRetirementFamilyGate(
            family = family,
            status = GpuRendererLegacyRetirementGateStatus.Refused,
            legacyRouteActive = true,
            retirementAuthorized = false,
            acceptedReplacementTicket = family.defaultReplacementTicket,
            activationDecisionId = evidence.joinToString("|") { item -> item.activationDecisionId },
            rollbackEvidenceId = evidence.joinToString("|") { item -> item.rollbackEvidenceId },
            rollbackValidationHash = evidence.joinToString("|") { item -> item.rollbackValidationHash },
            pmEvidenceRowId = evidence.joinToString("|") { item -> item.pmEvidenceRowId },
            oldPathUsageEvidenceId = evidence.joinToString("|") { item -> item.oldPathUsageEvidenceId },
            oldPathUsageCount = null,
            scopeLabel = "legacy.${family.familyId}.retirement",
            diagnostics = listOf("legacy.retirement.duplicate_family_evidence"),
        )

    private fun evaluateFamilyEvidence(
        family: GpuRendererLegacyRouteFamily,
        evidence: GpuRendererLegacyRetirementEvidence,
        sharedEvidenceKeys: SharedRetirementEvidenceKeys,
    ): GpuRendererLegacyRetirementFamilyGate {
        val diagnostics = evidence.diagnosticsFor(family, sharedEvidenceKeys)
        val accepted = diagnostics.isEmpty()
        return GpuRendererLegacyRetirementFamilyGate(
            family = family,
            status = if (accepted) {
                GpuRendererLegacyRetirementGateStatus.Accepted
            } else {
                GpuRendererLegacyRetirementGateStatus.Refused
            },
            legacyRouteActive = true,
            retirementAuthorized = accepted,
            acceptedReplacementTicket = evidence.acceptedReplacementTicket,
            activationDecisionId = evidence.activationDecisionId,
            rollbackEvidenceId = evidence.rollbackEvidenceId,
            rollbackValidationHash = evidence.rollbackValidationHash,
            pmEvidenceRowId = evidence.pmEvidenceRowId,
            oldPathUsageEvidenceId = evidence.oldPathUsageEvidenceId,
            oldPathUsageCount = evidence.oldPathUsageCount,
            scopeLabel = evidence.scopeLabel,
            diagnostics = diagnostics,
        )
    }

    private fun GpuRendererLegacyRetirementEvidence.diagnosticsFor(
        family: GpuRendererLegacyRouteFamily,
        sharedEvidenceKeys: SharedRetirementEvidenceKeys,
    ): List<String> = buildList {
        if (acceptedReplacementTicket != family.defaultReplacementTicket) {
            add("legacy.retirement.replacement_ticket_mismatch")
        }
        if (!replacementAccepted) {
            add("legacy.retirement.replacement_ticket_not_accepted")
        }
        if (activationDecisionId.isBlank()) {
            add("legacy.retirement.activation_decision_required")
        }
        if (activationDecisionId.isNotBlank() && !activationDecisionId.contains(":${family.familyId}:")) {
            add("legacy.retirement.activation_decision_not_family_scoped")
        }
        if (!activationDecisionAccepted) {
            add("legacy.retirement.activation_decision_not_accepted")
        }
        if (!shadowParityAccepted) {
            add("legacy.retirement.shadow_parity_gate_required")
        }
        if (rollbackEvidenceId.isBlank()) {
            add("legacy.retirement.rollback_evidence_required")
        }
        if (rollbackEvidenceId.isNotBlank() && !rollbackEvidenceId.contains(":${family.familyId}:")) {
            add("legacy.retirement.rollback_evidence_not_family_scoped")
        }
        if (!rollbackValidationHash.startsWith("sha256:")) {
            add("legacy.retirement.rollback_validation_hash_required")
        }
        if (pmEvidenceRowId != "gpu-renderer.legacy-retirement.${family.familyId}") {
            add("legacy.retirement.pm_row_not_family_scoped")
        }
        if (oldPathUsageEvidenceId.isBlank()) {
            add("legacy.retirement.old_path_usage_evidence_required")
        }
        if (oldPathUsageEvidenceId.isNotBlank() && !oldPathUsageEvidenceId.contains(":${family.familyId}:")) {
            add("legacy.retirement.old_path_usage_evidence_not_family_scoped")
        }
        if (oldPathUsageCount != 0) {
            add("legacy.retirement.old_path_usage_must_be_zero")
        }
        if (scopeLabel != "legacy.${family.familyId}.retirement") {
            add("legacy.retirement.scope_not_family_scoped")
        }
        if (archivedEvidenceOnly) {
            add("legacy.retirement.archived_evidence_only_forbidden")
        }
        if (genericMigrationGate) {
            add("legacy.retirement.generic_migration_gate_forbidden")
        }
        if (broadDeletion) {
            add("legacy.retirement.broad_deletion_forbidden")
        }
        if (productRouteActivated) {
            add("legacy.retirement.product_route_activation_forbidden")
        }
        if (releaseBlocking) {
            add("legacy.retirement.release_blocking_forbidden")
        }
        if (readinessDelta != 0.0) {
            add("legacy.retirement.readiness_delta_forbidden")
        }
        if (retirementEvidenceKey() in sharedEvidenceKeys.fullRows) {
            add("legacy.retirement.shared_evidence_across_families")
        }
        if (activationDecisionId in sharedEvidenceKeys.activationDecisions) {
            add("legacy.retirement.shared_activation_decision_across_families")
        }
        if (rollbackEvidenceId in sharedEvidenceKeys.rollbackEvidence) {
            add("legacy.retirement.shared_rollback_evidence_across_families")
        }
        if (oldPathUsageEvidenceId in sharedEvidenceKeys.oldPathUsageEvidence) {
            add("legacy.retirement.shared_old_path_usage_evidence_across_families")
        }
    }

    private fun GpuRendererLegacyRetirementEvidence.retirementEvidenceKey(): String =
        "$activationDecisionId|$rollbackEvidenceId|$oldPathUsageEvidenceId"

    private fun List<GpuRendererLegacyRetirementEvidence>.sharedKeysBy(
        selector: (GpuRendererLegacyRetirementEvidence) -> String,
    ): Set<String> =
        groupBy(selector)
            .filterKeys { key -> key.isNotBlank() }
            .filterValues { items -> items.map { item -> item.family }.distinct().size > 1 }
            .keys
}

private data class SharedRetirementEvidenceKeys(
    val fullRows: Set<String>,
    val activationDecisions: Set<String>,
    val rollbackEvidence: Set<String>,
    val oldPathUsageEvidence: Set<String>,
)

private fun GpuRendererLegacyRetirementGateStatus.dumpLabel(): String =
    when (this) {
        GpuRendererLegacyRetirementGateStatus.Accepted -> "accepted"
        GpuRendererLegacyRetirementGateStatus.MissingEvidence -> "missing"
        GpuRendererLegacyRetirementGateStatus.Refused -> "refused"
    }
