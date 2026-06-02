package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M87RuntimeEffectLiveEditingTest {
    @Test
    fun liveParameterMetadataAndTelemetryAreStable() {
        val evidence = buildM87RuntimeEffectLiveEditingEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertEquals("pass", evidence.status)
        assertEquals(2, evidence.states.size)
        assertContains(json, "\"packId\":\"m87-runtime-effect-live-editing-v1\"")
        assertContains(json, "\"stableId\":\"runtime.simple_rt\"")
        assertContains(json, "\"name\":\"gColor.b\"")
        assertContains(json, "\"pipelineKeyAxis\":false")
        assertContains(json, "\"pipelineKeyStableAcrossUniformEdits\":true")
        assertContains(json, "\"diagnostic\":\"m87.runtime-effect.parameter-out-of-range\"")
        assertContains(json, "\"arbitrarySkSLFallbackReason\":\"runtime-effect.arbitrary-sksl-unsupported\"")
    }

    @Test
    fun reflectionEvidenceVerifiesSimpleRtUniformLayout() {
        val evidence = buildM87RuntimeEffectLiveEditingEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertContains(json, "\"source\":\"wgsl4k-validation-report\"")
        assertContains(json, "\"binding\":\"uniforms@group=0,binding=0\"")
        assertContains(json, "\"uniform\":\"gColor\"")
        assertContains(json, "\"declaredOffset\":0")
        assertContains(json, "\"reflectedOffset\":0")
        assertContains(json, "\"layoutVerified\":true")
        assertContains(json, "\"mismatchDiagnostic\":\"m87.runtime-effect.uniform-layout-mismatch\"")
    }

    @Test
    fun editedStatesHaveParityArtifactsAndStableRefusals() {
        val evidence = buildM87RuntimeEffectLiveEditingEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertEquals(2, evidence.parityRows.size)
        evidence.parityRows.forEach { row ->
            assertEquals(4096, row.pixels)
            assertEquals(4096, row.matchingPixels)
            assertEquals(100.0, row.similarity)
            assertEquals(0, row.maxChannelDelta)
            assertTrue(row.artifactRoot.startsWith("reports/wgsl-pipeline/m87-runtime-effect-live-editing/states/"))
        }
        assertContains(json, "\"fallbackReason\":\"runtime-effect.arbitrary-sksl-unsupported\"")
        assertContains(json, "\"fallbackReason\":\"runtime-effect.wgsl-descriptor-missing\"")
        assertContains(json, "\"m87.edited-state-parity\"")
    }
}
