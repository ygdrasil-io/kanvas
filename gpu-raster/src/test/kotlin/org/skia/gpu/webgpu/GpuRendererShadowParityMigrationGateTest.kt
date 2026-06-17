package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GpuRendererShadowParityMigrationGateTest {
    @Test
    fun `legacy route family registry covers the M10 inventory rows explicitly`() {
        assertEquals(
            listOf(
                "material-paint",
                "solid-rect-drawpaint",
                "rounded-rect-gradients",
                "rect-rrect-stroke",
                "device-scissor-simple-clips",
                "path-fill-stroke",
                "images-bitmap-codecs-uploads",
                "savelayer-destination-read-filters",
                "text-glyphs",
                "runtime-effects-color-blends",
                "vertices-points-meshes",
                "clear-discard-target-background",
            ),
            GpuRendererLegacyRouteFamily.values().map { family -> family.familyId },
        )
    }

    @Test
    fun `missing per family shadow parity evidence keeps every legacy default active`() {
        val report = GpuRendererShadowParityMigrationGate.evaluate(emptyList())

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererLegacyRouteFamily.values().size, report.missingFamilyCount)
        assertEquals(0, report.acceptedFamilyCount)
        assertTrue(report.familyGates.all { gate -> gate.legacyDefaultActive })
        assertTrue(report.familyGates.all { gate -> gate.status == GpuRendererShadowParityGateStatus.MissingEvidence })
        assertFalse(report.productRouteActivated)
        assertFalse(report.releaseBlocking)
        assertEquals(0.0, report.readinessDelta)

        val dump = report.dumpLines().joinToString("\n")
        assertTrue(dump.contains("shadow-parity-gates row=gpu-renderer.shadow-parity-gates"))
        assertTrue(dump.contains("families=${GpuRendererLegacyRouteFamily.values().size} accepted=0"))
        assertTrue(dump.contains("legacyDefaultActive=true"))
        assertTrue(dump.contains("diagnostic=shadow.parity.missing_adapter_backed_evidence"))
        assertFalse(dump.contains("productRouteActivated=true"))
        assertFalse(dump.contains("releaseBlocking=true"))
    }

    @Test
    fun `complete route specific parity evidence satisfies gates without switching defaults`() {
        val evidence = GpuRendererLegacyRouteFamily.values().map { family -> validEvidence(family) }

        val report = GpuRendererShadowParityMigrationGate.evaluate(evidence)

        assertTrue(report.gatePassed)
        assertEquals(GpuRendererLegacyRouteFamily.values().size, report.acceptedFamilyCount)
        assertEquals(0, report.missingFamilyCount)
        assertTrue(report.familyGates.all { gate -> gate.status == GpuRendererShadowParityGateStatus.Accepted })
        assertTrue(report.familyGates.all { gate -> gate.legacyDefaultActive })
        assertFalse(report.productRouteActivated)
        assertFalse(report.releaseBlocking)
        assertEquals(0.0, report.readinessDelta)

        val solidFill = report.familyGates.single {
            it.family == GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        }
        assertEquals("KGPU-M1-004", solidFill.acceptedReplacementTicket)
        assertEquals("legacy.solid-rect-drawpaint.rollback", solidFill.rollbackLabel)

        val dump = report.dumpLines().joinToString("\n")
        assertTrue(dump.contains("accepted=${GpuRendererLegacyRouteFamily.values().size} missing=0"))
        assertTrue(dump.contains("defaultRouteChanged=false"))
        assertTrue(dump.contains("family=solid-rect-drawpaint status=accepted"))
        assertTrue(dump.contains("rollback=legacy.solid-rect-drawpaint.rollback"))
    }

    @Test
    fun `unsafe or non route specific parity evidence refuses with stable diagnostics`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val unsafe = validEvidence(solidFill).copy(
            adapterBacked = false,
            rollbackLabel = "legacy.shared.rollback",
            productRouteActivated = true,
            releaseBlocking = true,
            readinessDelta = 0.25,
            pmRowId = "gpu-renderer.shadow-parity-gates",
        )

        val report = GpuRendererShadowParityMigrationGate.evaluate(listOf(unsafe))
        val gate = report.familyGates.single { it.family == solidFill }

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererShadowParityGateStatus.Refused, gate.status)
        assertTrue(gate.diagnostics.contains("shadow.parity.adapter_backed_required"))
        assertTrue(gate.diagnostics.contains("shadow.parity.rollback_label_not_family_scoped"))
        assertTrue(gate.diagnostics.contains("shadow.parity.pm_row_not_family_scoped"))
        assertTrue(gate.diagnostics.contains("shadow.parity.product_route_activation_forbidden"))
        assertTrue(gate.diagnostics.contains("shadow.parity.release_blocking_forbidden"))
        assertTrue(gate.diagnostics.contains("shadow.parity.readiness_delta_forbidden"))

        val dump = report.dumpLines().joinToString("\n")
        assertTrue(dump.contains("family=solid-rect-drawpaint status=refused"))
        assertTrue(dump.contains("diagnostic=shadow.parity.adapter_backed_required"))
    }

    @Test
    fun `duplicate family evidence refuses instead of merging unrelated parity rows`() {
        val solidFill = GpuRendererLegacyRouteFamily.SolidRectAndDrawPaintFill
        val first = validEvidence(solidFill)
        val second = validEvidence(solidFill).copy(
            shadowRouteTestId = "gpu-raster:other-shadow-test",
            beforeDumpHash = "sha256:before-other",
            afterDumpHash = "sha256:after-other",
        )

        val report = GpuRendererShadowParityMigrationGate.evaluate(listOf(first, second))
        val gate = report.familyGates.single { it.family == solidFill }

        assertFalse(report.gatePassed)
        assertEquals(GpuRendererShadowParityGateStatus.Refused, gate.status)
        assertTrue(gate.diagnostics.contains("shadow.parity.duplicate_family_evidence"))
        assertTrue(gate.legacyDefaultActive)
    }

    @Test
    fun `shared broad evidence across families refuses instead of satisfying parity`() {
        val allFamilyIds = GpuRendererLegacyRouteFamily.values().joinToString("-") { family -> family.familyId }
        val broadEvidence = GpuRendererLegacyRouteFamily.values().map { family ->
            validEvidence(family).copy(
                shadowRouteTestId = "gpu-raster:all-families:$allFamilyIds",
                beforeDumpHash = "sha256:shared-before",
                afterDumpHash = "sha256:shared-after",
            )
        }

        val report = GpuRendererShadowParityMigrationGate.evaluate(broadEvidence)

        assertFalse(report.gatePassed)
        assertEquals(0, report.acceptedFamilyCount)
        assertEquals(GpuRendererLegacyRouteFamily.values().size, report.refusedFamilyCount)
        assertTrue(
            report.familyGates.all { gate ->
                gate.diagnostics.contains("shadow.parity.shared_evidence_across_families")
            },
        )
        assertTrue(report.familyGates.all { gate -> gate.legacyDefaultActive })
    }
}

private fun validEvidence(
    family: GpuRendererLegacyRouteFamily,
): GpuRendererShadowParityEvidence =
    GpuRendererShadowParityEvidence(
        family = family,
        shadowRouteTestId = "gpu-raster:${family.familyId}:shadow-route",
        beforeDumpHash = "sha256:${family.familyId}:before",
        afterDumpHash = "sha256:${family.familyId}:after",
        pmRowId = "gpu-renderer.shadow-parity.${family.familyId}",
        rollbackLabel = "legacy.${family.familyId}.rollback",
        acceptedReplacementTicket = family.defaultReplacementTicket,
        adapterBacked = true,
        productRouteActivated = false,
        releaseBlocking = false,
        readinessDelta = 0.0,
    )
