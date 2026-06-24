package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PMEvidenceBundleTest {

    @Test
    fun `bundle with all conditions met is ready`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = true,
            gatesGreen = true,
            rollbackTested = true,
        )
        assertTrue(bundle.isReady)
    }

    @Test
    fun `bundle with missing gates is not ready`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = true,
            gatesGreen = false,
            rollbackTested = true,
        )
        assertFalse(bundle.isReady)
    }

    @Test
    fun `bundle with missing families is not ready`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = false,
            gatesGreen = true,
            rollbackTested = true,
        )
        assertFalse(bundle.isReady)
    }

    @Test
    fun `bundle without rollback tested is not ready`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = true,
            gatesGreen = true,
            rollbackTested = false,
        )
        assertFalse(bundle.isReady)
    }

    @Test
    fun `bundle rejects blank id`() {
        assertFailsWith<IllegalArgumentException> {
            M23PMEvidenceBundle(
                bundleId = "",
                allFamiliesActivated = true,
                gatesGreen = true,
                rollbackTested = true,
            )
        }
    }

    @Test
    fun `export bundle lines contain all fields`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = true,
            gatesGreen = true,
            rollbackTested = true,
            exportTimestamp = "2026-06-24T12:00:00Z",
        )
        val lines = bundle.exportBundleLines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("m23-pm-bundle"))
        assertTrue(lines[0].contains("allFamiliesActivated=true"))
        assertTrue(lines[0].contains("gatesGreen=true"))
        assertTrue(lines[0].contains("rollbackTested=true"))
        assertTrue(lines[1].contains("status=ready"))
        assertTrue(lines[2].contains("nonclaim"))
    }

    @Test
    fun `export bundle lines show not ready when conditions not met`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = false,
            gatesGreen = false,
            rollbackTested = false,
        )
        val lines = bundle.exportBundleLines()
        assertTrue(lines[1].contains("status=not-ready"))
    }

    @Test
    fun `bundle has default nonClaims`() {
        val bundle = M23PMEvidenceBundle(
            bundleId = "m23-pm-bundle",
            allFamiliesActivated = true,
            gatesGreen = true,
            rollbackTested = true,
        )
        assertTrue(bundle.nonClaims.contains("no-release-blocking-gate"))
        assertTrue(bundle.nonClaims.contains("no-readiness-delta"))
        assertTrue(bundle.nonClaims.contains("no-product-activation"))
    }
}
