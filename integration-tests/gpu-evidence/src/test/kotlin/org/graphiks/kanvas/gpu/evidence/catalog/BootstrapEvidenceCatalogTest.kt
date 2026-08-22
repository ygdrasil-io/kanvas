package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BootstrapEvidenceCatalogTest {
    @Test
    fun `bootstrap catalog is the approved two case gate`() {
        val cases = BootstrapEvidenceCatalog.cases

        assertEquals(
            listOf(
                "solid-card-stack",
                "custom-runtime-effect-unregistered-refusal",
            ),
            cases.map { it.descriptor.id.value },
        )
        assertEquals(cases.size, cases.map { it.descriptor.id }.toSet().size)

        val solid = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "solid-card-stack" })
        assertEquals(64, solid.descriptor.width)
        assertEquals(64, solid.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(solid.descriptor.expectation)
        assertNotNull(solid.oracle)
        assertEquals(0, solid.descriptor.comparison?.perChannelTolerance)
        assertEquals(100.0, solid.descriptor.comparison?.minimumSimilarityPercent)

        val refusal = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "custom-runtime-effect-unregistered-refusal" })
        assertEquals(16, refusal.descriptor.width)
        assertEquals(16, refusal.descriptor.height)
        assertEquals(
            "unsupported.runtime_effect.custom_wgsl_not_registered",
            assertIs<EvidenceExpectation.ShouldRefuse>(refusal.descriptor.expectation).stableReasonCode,
        )
        assertEquals(null, refusal.oracle)
    }
}
