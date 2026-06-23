package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GpuRendererLegacyRetirementGateTest {
    @Test
    fun `missing retirement evidence keeps every legacy route active`() {
        val report = GpuRendererLegacyRetirementGate.evaluate(emptyList())

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererLegacyRouteFamily.values().size, report.missingFamilyCount)
        assertEquals(0, report.acceptedFamilyCount)
        assertTrue(report.familyGates.all { gate -> gate.status == GpuRendererLegacyRetirementGateStatus.MissingEvidence })
        assertTrue(report.familyGates.all { gate -> gate.legacyRouteActive })
        assertFalse(report.familyGates.any { gate -> gate.retirementAuthorized })
        assertFalse(report.productRouteActivated)
        assertFalse(report.releaseBlocking)
        assertEquals(0.0, report.readinessDelta)

        val dump = report.dumpLines().joinToString("\n")
        assertTrue(dump.contains("legacy-retirement row=gpu-renderer.legacy-retirement"))
        assertTrue(dump.contains("families=${GpuRendererLegacyRouteFamily.values().size} accepted=0"))
        assertTrue(dump.contains("legacyRouteActive=true"))
        assertTrue(dump.contains("diagnostic=legacy.retirement.missing_promoted_replacement_evidence"))
        assertFalse(dump.contains("productRouteActivated=true"))
        assertFalse(dump.contains("releaseBlocking=true"))
    }

    @Test
    fun `complete route specific retirement evidence authorizes only the named legacy slice`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill

        val report = GpuRendererLegacyRetirementGate.evaluate(listOf(validRetirementEvidence(solidFill)))
        val gate = report.familyGates.single { item -> item.family == solidFill }

        assertFalse(report.gatePassed)
        assertEquals(1, report.acceptedFamilyCount)
        assertEquals(GpuRendererLegacyRetirementGateStatus.Accepted, gate.status)
        assertTrue(gate.legacyRouteActive)
        assertTrue(gate.retirementAuthorized)
        assertEquals("KGPU-M1-004", gate.acceptedReplacementTicket)
        assertEquals("gpu-renderer.legacy-retirement.solid-rect-drawpaint", gate.pmEvidenceRowId)
        assertEquals("legacy.solid-rect-drawpaint.retirement", gate.scopeLabel)
        assertFalse(report.productRouteActivated)
        assertFalse(report.releaseBlocking)
        assertEquals(0.0, report.readinessDelta)

        val dump = report.dumpLines().joinToString("\n")
        assertTrue(dump.contains("family=solid-rect-drawpaint status=accepted"))
        assertTrue(dump.contains("legacyRouteActive=true"))
        assertTrue(dump.contains("retirementAuthorized=true"))
        assertTrue(dump.contains("oldPathUsage=0"))
        assertTrue(dump.contains("legacy-retirement:nonclaim productRouteActivated=false"))
    }

    @Test
    fun `unsafe broad retirement evidence is refused with stable diagnostics`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val unsafe = validRetirementEvidence(solidFill).copy(
            replacementAccepted = false,
            activationDecisionId = "   ",
            activationDecisionAccepted = false,
            rollbackEvidenceId = "",
            rollbackValidationHash = "not-a-sha",
            pmEvidenceRowId = "gpu-renderer.legacy-retirement",
            oldPathUsageEvidenceId = "",
            oldPathUsageCount = 2,
            scopeLabel = "legacy.all.retirement",
            genericMigrationGate = true,
            broadDeletion = true,
            productRouteActivated = true,
            releaseBlocking = true,
            readinessDelta = 0.5,
            shadowParityAccepted = false,
        )

        val report = GpuRendererLegacyRetirementGate.evaluate(listOf(unsafe))
        val gate = report.familyGates.single { item -> item.family == solidFill }

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererLegacyRetirementGateStatus.Refused, gate.status)
        assertTrue(gate.legacyRouteActive)
        assertFalse(gate.retirementAuthorized)
        assertTrue(gate.diagnostics.contains("legacy.retirement.replacement_ticket_not_accepted"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.activation_decision_required"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.activation_decision_not_accepted"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.rollback_evidence_required"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.rollback_validation_hash_required"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.pm_row_not_family_scoped"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.old_path_usage_evidence_required"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.old_path_usage_must_be_zero"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.scope_not_family_scoped"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.generic_migration_gate_forbidden"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.broad_deletion_forbidden"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.product_route_activation_forbidden"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.release_blocking_forbidden"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.readiness_delta_forbidden"))
        assertTrue(gate.diagnostics.contains("legacy.retirement.shadow_parity_gate_required"))
    }

    @Test
    fun `archived evidence cannot be used as a retirement gate`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val archivedOnly = validRetirementEvidence(solidFill).copy(archivedEvidenceOnly = true)

        val report = GpuRendererLegacyRetirementGate.evaluate(listOf(archivedOnly))
        val gate = report.familyGates.single { item -> item.family == solidFill }

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererLegacyRetirementGateStatus.Refused, gate.status)
        assertTrue(gate.legacyRouteActive)
        assertFalse(gate.retirementAuthorized)
        assertTrue(gate.diagnostics.contains("legacy.retirement.archived_evidence_only_forbidden"))
    }

    @Test
    fun `duplicate family evidence refuses instead of merging retirement rows`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val first = validRetirementEvidence(solidFill)
        val second = validRetirementEvidence(solidFill).copy(
            activationDecisionId = "activation:${solidFill.familyId}:second",
            rollbackEvidenceId = "rollback:${solidFill.familyId}:second",
            oldPathUsageEvidenceId = "old-path:${solidFill.familyId}:second",
        )

        val report = GpuRendererLegacyRetirementGate.evaluate(listOf(first, second))
        val gate = report.familyGates.single { item -> item.family == solidFill }

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererLegacyRetirementGateStatus.Refused, gate.status)
        assertTrue(gate.legacyRouteActive)
        assertFalse(gate.retirementAuthorized)
        assertTrue(gate.diagnostics.contains("legacy.retirement.duplicate_family_evidence"))
    }

    @Test
    fun `shared evidence across families is refused instead of deleting broadly`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val roundedRect = GpuRendererLegacyRouteFamily.RoundedRectAndGradients
        val evidence = listOf(
            validRetirementEvidence(solidFill).copy(
                activationDecisionId = "decision:shared",
                rollbackEvidenceId = "rollback:shared",
                oldPathUsageEvidenceId = "old-path:shared",
            ),
            validRetirementEvidence(roundedRect).copy(
                activationDecisionId = "decision:shared",
                rollbackEvidenceId = "rollback:shared",
                oldPathUsageEvidenceId = "old-path:shared",
            ),
        )

        val report = GpuRendererLegacyRetirementGate.evaluate(evidence)

        assertFalse(report.gatePassed)
        assertEquals(0, report.acceptedFamilyCount)
        assertEquals(2, report.refusedFamilyCount)
        assertTrue(
            report.familyGates
                .filter { gate -> gate.family == solidFill || gate.family == roundedRect }
                .all { gate -> gate.diagnostics.contains("legacy.retirement.shared_evidence_across_families") },
        )
    }

    @Test
    fun `partially shared activation rollback or usage evidence is refused per family`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val roundedRect = GpuRendererLegacyRouteFamily.RoundedRectAndGradients
        val sharedActivation = "activation:${solidFill.familyId}:${roundedRect.familyId}:shared"
        val evidence = listOf(
            validRetirementEvidence(solidFill).copy(activationDecisionId = sharedActivation),
            validRetirementEvidence(roundedRect).copy(activationDecisionId = sharedActivation),
        )

        val report = GpuRendererLegacyRetirementGate.evaluate(evidence)

        assertFalse(report.gatePassed)
        assertEquals(0, report.acceptedFamilyCount)
        assertEquals(2, report.refusedFamilyCount)
        assertTrue(
            report.familyGates
                .filter { gate -> gate.family == solidFill || gate.family == roundedRect }
                .all { gate -> gate.diagnostics.contains("legacy.retirement.shared_activation_decision_across_families") },
        )
    }
}

private fun validRetirementEvidence(
    family: GpuRendererLegacyRouteFamily,
): GpuRendererLegacyRetirementEvidence =
    GpuRendererLegacyRetirementEvidence(
        family = family,
        acceptedReplacementTicket = family.defaultReplacementTicket,
        replacementAccepted = true,
        activationDecisionId = "activation:${family.familyId}:accepted",
        activationDecisionAccepted = true,
        rollbackEvidenceId = "rollback:${family.familyId}:validated",
        rollbackValidationHash = "sha256:${family.familyId}:rollback",
        pmEvidenceRowId = "gpu-renderer.legacy-retirement.${family.familyId}",
        oldPathUsageEvidenceId = "old-path:${family.familyId}:zero",
        oldPathUsageCount = 0,
        scopeLabel = "legacy.${family.familyId}.retirement",
        archivedEvidenceOnly = false,
        genericMigrationGate = false,
        broadDeletion = false,
        productRouteActivated = false,
        releaseBlocking = false,
        readinessDelta = 0.0,
        shadowParityAccepted = true,
    )
