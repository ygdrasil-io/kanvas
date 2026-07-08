package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUMsaaTest {

    private val adapter4x = GPUMsaaAdapterCapability(
        adapterLabel = "adapter:wgpu4k:test-device",
        maxSampleCount = 4,
        supportsAlphaToCoverage = false,
        supportsNativeResolve = true,
    )
    private val adapter8x = GPUMsaaAdapterCapability(
        adapterLabel = "adapter:wgpu4k:test-device-8x",
        maxSampleCount = 8,
        supportsAlphaToCoverage = true,
        supportsNativeResolve = true,
    )

    @Test
    fun `4x MSAA resolve accepted with standard coverage`() {
        val route = GPUMsaa.resolve4x(adapter4x)

        val accepted = assertIs<GPUMsaaRoute.Accepted>(route)
        assertEquals(4, accepted.sampleCount)
        assertEquals(GPUMsaaCoverageMode.Standard, accepted.coverageMode)
        assertEquals("adapter:wgpu4k:test-device", accepted.resolved.adapter.adapterLabel)
        assertTrue(accepted.resolved.psnrEvidence == null)
    }

    @Test
    fun `8x MSAA resolve accepted per adapter`() {
        val route = GPUMsaa.resolve8x(adapter8x)

        val accepted = assertIs<GPUMsaaRoute.Accepted>(route)
        assertEquals(8, accepted.sampleCount)
        assertEquals(GPUMsaaCoverageMode.Standard, accepted.coverageMode)
        assertEquals("adapter:wgpu4k:test-device-8x", accepted.resolved.adapter.adapterLabel)
    }

    @Test
    fun `alpha-to-coverage accepted when adapter supports it`() {
        val route = GPUMsaa.resolve(
            GPUMsaaRequest(
                requestedSampleCount = 4,
                coverageMode = GPUMsaaCoverageMode.AlphaToCoverage,
                adapter = adapter8x,
            )
        )

        val accepted = assertIs<GPUMsaaRoute.Accepted>(route)
        assertEquals(4, accepted.sampleCount)
        assertEquals(GPUMsaaCoverageMode.AlphaToCoverage, accepted.coverageMode)
    }

    @Test
    fun `alpha-to-coverage refused when adapter does not support it`() {
        val route = GPUMsaa.resolve(
            GPUMsaaRequest(
                requestedSampleCount = 4,
                coverageMode = GPUMsaaCoverageMode.AlphaToCoverage,
                adapter = adapter4x,
            )
        )

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        assertEquals("unsupported.msaa.alpha_to_coverage", refused.diagnostic.code)
        assertEquals("msaa.resolve", refused.diagnostic.stage)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `refused when adapter capability insufficient for requested sample count`() {
        val route = GPUMsaa.resolve8x(adapter4x)

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        assertEquals("unsupported.msaa.adapter_capability", refused.diagnostic.code)
        assertTrue(refused.diagnostic.message.contains("maxSampleCount 4 < requested 8"))
    }

    @Test
    fun `refused when no adapter capability evidence provided`() {
        val route = GPUMsaa.resolve(
            GPUMsaaRequest(
                requestedSampleCount = 4,
                coverageMode = GPUMsaaCoverageMode.Standard,
                adapter = null,
            )
        )

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        assertEquals("unsupported.msaa.webgpu_missing_adapter", refused.diagnostic.code)
    }

    @Test
    fun `refused when native resolve capability evidence is absent`() {
        val route = GPUMsaa.resolve(
            GPUMsaaRequest(
                requestedSampleCount = 4,
                coverageMode = GPUMsaaCoverageMode.Standard,
                adapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:no-resolve",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                ),
            )
        )

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        assertEquals("unsupported.msaa.native_resolve_unavailable", refused.diagnostic.code)
        assertEquals("msaa.resolve", refused.diagnostic.stage)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `refused when sample count is outside supported invariant`() {
        val route = GPUMsaa.resolve(
            GPUMsaaRequest(
                requestedSampleCount = 6,
                coverageMode = GPUMsaaCoverageMode.Standard,
                adapter = adapter8x,
            )
        )

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        assertEquals("unsupported.msaa.sample_count", refused.diagnostic.code)
        assertTrue(refused.diagnostic.message.contains("1, 4, or 8"))
    }

    @Test
    fun `one sample resolve is refused without throwing`() {
        val route = GPUMsaa.resolve(
            GPUMsaaRequest(
                requestedSampleCount = 1,
                coverageMode = GPUMsaaCoverageMode.Standard,
                adapter = adapter4x,
            )
        )

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        assertEquals("unsupported.msaa.sample_count", refused.diagnostic.code)
        assertTrue(refused.diagnostic.message.contains("4x or 8x"))
    }

    @Test
    fun `PSNR evidence computed for 4x MSAA quality measurement`() {
        val reference = FloatArray(64 * 64 * 4) { 0.5f }
        val resolved = FloatArray(64 * 64 * 4) { index ->
            0.5f + (index % 1000).toFloat() * 0.0001f
        }

        val route = GPUMsaa.resolve4x(
            adapter = adapter4x,
            referencePixels = reference,
            resolvedPixels = resolved,
        )

        val accepted = assertIs<GPUMsaaRoute.Accepted>(route)
        val psnr = accepted.resolved.psnrEvidence
        assertNotNull(psnr)
        assertEquals(4, psnr.sampleCount)
        assertTrue(psnr.psnrDb > 0.0)
        assertTrue(psnr.psnrDb.isFinite())
        assertEquals("reference-4x", psnr.referenceLabel)
    }

    @Test
    fun `8x resolve includes PSNR evidence when pixel arrays provided`() {
        val reference = FloatArray(32 * 32 * 4) { 1.0f }
        val resolved = FloatArray(32 * 32 * 4) { 1.0f }

        val route = GPUMsaa.resolve8x(
            adapter = adapter8x,
            referencePixels = reference,
            resolvedPixels = resolved,
        )

        val accepted = assertIs<GPUMsaaRoute.Accepted>(route)
        val psnr = accepted.resolved.psnrEvidence
        assertNotNull(psnr)
        assertEquals(8, psnr.sampleCount)
        assertTrue(psnr.psnrDb.isInfinite(), "identical pixels should yield infinite PSNR")
    }

    @Test
    fun `dump lines expose stable evidence without backend objects`() {
        val route = GPUMsaa.resolve4x(
            adapter = adapter8x,
            coverageMode = GPUMsaaCoverageMode.AlphaToCoverage,
        )

        val accepted = assertIs<GPUMsaaRoute.Accepted>(route)
        val lines = accepted.dumpLines()

        assertEquals(3, lines.size)
        assertEquals(
            "msaa.resolve sampleCount=4 coverageMode=AlphaToCoverage adapter=adapter:wgpu4k:test-device-8x " +
                "maxSampleCount=8 alphaToCoverage=true route=accepted",
            lines[0],
        )
        assertEquals(
            "msaa.resolve.adapter label=adapter:wgpu4k:test-device-8x maxSamples=8 alphaToCoverage=true",
            lines[1],
        )
        assertEquals(
            "msaa.resolve.psnr sampleCount=4 psnrDb=none reference=none",
            lines[2],
        )
    }

    @Test
    fun `dump lines for refused route contain diagnostic evidence`() {
        val route = GPUMsaa.resolve8x(adapter4x)

        val refused = assertIs<GPUMsaaRoute.Refused>(route)
        val lines = refused.dumpLines()

        assertEquals(2, lines.size)
        assertEquals(
            "msaa.resolve sampleCount=8 adapter=adapter:wgpu4k:test-device maxSampleCount=4 route=refused",
            lines[0],
        )
        assertEquals(
            "msaa.resolve.diagnostic code=unsupported.msaa.adapter_capability " +
                "message=Adapter maxSampleCount 4 < requested 8 stage=msaa.resolve terminal=true",
            lines[1],
        )
    }

    @Test
    fun `GPUMultisamplePlan validates sample count and serialises deterministically`() {
        val plan = GPUMultisamplePlan(
            sampleCount = 4,
            sampleMask = 0xFu,
            alphaToCoverageEnabled = false,
        )
        assertEquals(4, plan.sampleCount)
        assertEquals(0xFu, plan.sampleMask)
        assertContains(plan.dumpLines().first(), "msaa.plan sampleCount=4 sampleMask=15 alphaToCoverage=false")
    }

    @Test
    fun `GPUMultisampleResolvePlan captures strategy selection`() {
        val builtin = GPUMultisampleResolvePlan(GPUMultisampleResolveStrategy.WGPU_BUILTIN)
        assertEquals(GPUMultisampleResolveStrategy.WGPU_BUILTIN, builtin.strategy)
        assertContains(builtin.dumpLines().first(), "msaa.resolve-plan strategy=WGPU_BUILTIN")

        val compute = GPUMultisampleResolvePlan(GPUMultisampleResolveStrategy.COMPUTE_SHADER)
        assertEquals(GPUMultisampleResolveStrategy.COMPUTE_SHADER, compute.strategy)
    }

    @Test
    fun `GPUMultisampleTargetDescriptor bridges sample count and resolve strategy`() {
        val resolvePlan = GPUMultisampleResolvePlan(GPUMultisampleResolveStrategy.CUSTOM_WGSL)
        val target = GPUMultisampleTargetDescriptor(sampleCount = 4, resolvePlan = resolvePlan)
        assertEquals(4, target.sampleCount)
        assertEquals(GPUMultisampleResolveStrategy.CUSTOM_WGSL, target.resolvePlan.strategy)
        assertContains(target.dumpLines().first(), "msaa.target-desc sampleCount=4 resolveStrategy=CUSTOM_WGSL")
    }

    @Test
    fun `multisample resolve format refusal code is defined`() {
        assertEquals(
            "unsupported.target.multisample_resolve_format",
            GPUMsaa.Reason.MULTISAMPLE_RESOLVE_FORMAT,
        )
    }
}
