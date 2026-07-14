package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SDRColorBoundaryTest {
    @Test
    fun `finite srgb color produces deterministic sdr value and store evidence without promotion`() {
        val report = GPUSDRColorBoundaryPlanner.plan(
            GPUSDRColorBoundaryRequest(
                sourceLabel = "paint:solid:fixture-a",
                sourceColorSpace = GPUSDRColorBoundaryPlanner.SRGB,
                targetColorSpace = GPUSDRColorBoundaryPlanner.SRGB,
                targetFormat = "rgba8unorm",
            ),
        )

        assertEquals("DependencyGated", report.classification)
        assertFalse(report.promotable)
        assertEquals("srgb", report.plan.inputSpec.colorSpace.name)
        assertEquals("srgb", report.plan.workingSpace.space.name)
        assertEquals("rgba8unorm-u8-unorm", report.plan.store.quantization)
        assertTrue(report.diagnostics.isEmpty())
        assertEquals(
            listOf(
                "color:sdr-boundary classification=DependencyGated source=paint:solid:fixture-a plan=finite-srgb-store targetFormat=rgba8unorm",
                "color:value components=4 space=srgb alpha=Unpremul encoding=f32 finite=true",
                "color:working space=srgb reason=sdr-first-slice highPrecision=false",
                "color:store targetSpace=srgb quantization=rgba8unorm-u8-unorm dither=false conversion=none",
                "color:keyFacts alpha=Unpremul componentCount=4 numeric=f32 sourceSpace=srgb store=rgba8unorm-u8-unorm targetSpace=srgb",
                "nonclaim:no-product-activation no-gpu-native-color-route no-hdr-support no-gainmap-support no-icc-cicp-transform no-untagged-policy no-platform-color-conversion",
            ),
            report.dumpLines(),
        )
    }

    @Test
    fun `unsupported hdr profile gainmap and untagged cases refuse with stable diagnostics`() {
        val cases = listOf(
            RefusalCase(
                expectedCode = "unsupported.color.hdr_transfer",
                request = sdrRequest.copy(
                    hdr = GPUHDRColorPlan(enabled = true, transferFunction = "PQ", maxNits = 1000f),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.color.icc_v4",
                request = sdrRequest.copy(
                    profile = GPUColorProfileDescriptor(
                        sourceKind = "icc-v4",
                        profileId = "display-p3-profile",
                        profileHash = "sha256:p3-profile",
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.color.cicp",
                request = sdrRequest.copy(
                    profile = GPUColorProfileDescriptor(
                        sourceKind = "cicp",
                        profileId = "bt2020-pq",
                        profileHash = "sha256:cicp-bt2020-pq",
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.color.gainmap",
                request = sdrRequest.copy(
                    gainmap = GPUGainmapPlan(
                        kind = "gainmap",
                        baseSpace = GPUSDRColorBoundaryPlanner.SRGB,
                        alternateSpace = GPUSDRColorBoundaryPlanner.SRGB,
                        metadataHash = "sha256:gainmap",
                        supported = false,
                    ),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.color.untagged_policy",
                request = sdrRequest.copy(sourceColorSpace = GPUSDRColorBoundaryPlanner.UNTAGGED),
            ),
            RefusalCase(
                expectedCode = "unsupported.color.extended_range",
                request = sdrRequest.copy(extendedRange = true),
            ),
        )

        for (case in cases) {
            val report = GPUSDRColorBoundaryPlanner.plan(case.request)

            assertEquals("RefuseDiagnostic", report.classification)
            assertEquals(case.expectedCode, report.diagnostics.single().code)
            assertTrue(report.diagnostics.single().isTerminal)
            assertFalse(report.promotable)
            assertContains(report.dumpLines().first(), "reason=${case.expectedCode}")
            assertContains(report.dumpLines().last(), "no-platform-color-conversion")
        }
    }

    @Test
    fun `behavior key facts exclude source provenance and profile identity`() {
        val first = GPUSDRColorBoundaryPlanner.plan(
            sdrRequest.copy(sourceLabel = "paint:solid:fixture-a"),
        )
        val second = GPUSDRColorBoundaryPlanner.plan(
            sdrRequest.copy(sourceLabel = "paint:solid:fixture-b"),
        )
        val refused = GPUSDRColorBoundaryPlanner.plan(
            sdrRequest.copy(
                profile = GPUColorProfileDescriptor(
                    sourceKind = "icc-v2",
                    profileId = "display-profile-object",
                    profileHash = "sha256:profile-object",
                ),
            ),
        )

        assertEquals(first.behaviorKeyFacts, second.behaviorKeyFacts)
        assertFalse(first.behaviorKeyFacts.joinToString(" ").contains("fixture-a"))
        assertFalse(first.behaviorKeyFacts.joinToString(" ").contains("fixture-b"))
        assertTrue(refused.behaviorKeyFacts.isEmpty())
        assertFalse(refused.dumpLines().single { it.startsWith("color:diagnostic") }.contains("sha256:profile-object"))
    }

    @Test
    fun `color format and interpretation identities reject blank values`() {
        assertEquals(GPUColorFormat("rgba8unorm"), GPUColorFormat("rgba8unorm"))
        assertEquals(
            GPUColorInterpretation("encoded-premul-srgb"),
            GPUColorInterpretation("encoded-premul-srgb"),
        )
        assertFailsWith<IllegalArgumentException> { GPUColorFormat(" ") }
        assertFailsWith<IllegalArgumentException> { GPUColorInterpretation("") }
    }
}

private data class RefusalCase(
    val expectedCode: String,
    val request: GPUSDRColorBoundaryRequest,
)

private val sdrRequest = GPUSDRColorBoundaryRequest(
    sourceLabel = "paint:solid:fixture",
    sourceColorSpace = GPUSDRColorBoundaryPlanner.SRGB,
    targetColorSpace = GPUSDRColorBoundaryPlanner.SRGB,
    targetFormat = "rgba8unorm",
)
