package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ImageFamilyResourceEvidenceTest {
    @Test
    fun `repeated image texture sampler evidence records create then reuse without broad support claim`() {
        val evidence = buildRepeatedImageTextureSamplerEvidence()

        assertEquals("phase6-image-repeated-texture-sampler", evidence.rowId)
        val lines = evidence.dumpLines.joinToString("\n")
        assertContains(lines, "resource-provider.cache lane=texture-sampler result=create")
        assertContains(lines, "resource-provider.cache lane=texture-sampler result=reuse")
        assertContains(lines, "subject=sampled-texture.phase6-checker")
        assertContains(evidence.nonClaims, "no-broad-image-support")
        assertContains(evidence.nonClaims, "no-codec-support")
        assertFalse(lines.contains("handle:") || lines.contains("0x") || lines.contains("@"))
    }
}
