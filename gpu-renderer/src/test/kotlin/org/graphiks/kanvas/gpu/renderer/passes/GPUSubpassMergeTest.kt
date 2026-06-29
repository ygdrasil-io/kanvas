package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

class GPUSubpassMergeTest {

    private val capableAdapter = GPUSubpassMergeAdapterCapability(
        supportsInputAttachment = true,
        maxColorAttachments = 4,
    )
    private val incapableAdapter = GPUSubpassMergeAdapterCapability(
        supportsInputAttachment = false,
        maxColorAttachments = 4,
    )

    private val blurH = GPURenderPassHandle("pass:blur-horizontal")
    private val blurV = GPURenderPassHandle("pass:blur-vertical")
    private val sharpen = GPURenderPassHandle("pass:sharpen")

    private fun candidate(
        pass: GPURenderPassHandle,
        scopeId: String = "scope-main",
        colorAttachmentFormat: String = "rgba8unorm",
        sampleCount: Int = 1,
        hasInterveningBarrier: Boolean = false,
        hasInterveningCopy: Boolean = false,
        hasInterveningUpload: Boolean = false,
        hasInterveningReadback: Boolean = false,
        hasInterveningDispatch: Boolean = false,
    ): GPUSubpassMergeCandidate = GPUSubpassMergeCandidate(
        pass = pass,
        scopeId = scopeId,
        colorAttachmentFormat = colorAttachmentFormat,
        sampleCount = sampleCount,
        hasInterveningBarrier = hasInterveningBarrier,
        hasInterveningCopy = hasInterveningCopy,
        hasInterveningUpload = hasInterveningUpload,
        hasInterveningReadback = hasInterveningReadback,
        hasInterveningDispatch = hasInterveningDispatch,
    )

    @Test
    fun `horizontal blur feeding vertical blur is merged into single subpass`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(1, analysis.eligiblePairs.size)
        assertEquals(0, analysis.refusedPairs.size)

        val plan = analysis.eligiblePairs[0]
        assertEquals(blurH, plan.producerPass)
        assertEquals(blurV, plan.consumerPass)
        assertEquals(0, plan.inputAttachmentIndex)
        assertEquals(0, plan.colorAttachmentIndex)
    }

    @Test
    fun `adapter without inputAttachment refuses all pairs`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV),
        )

        val analysis = analyzeSubpassMerge(passes, incapableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)

        val refusal = analysis.refusedPairs[0]
        assertEquals(blurH, refusal.producerPass)
        assertEquals(blurV, refusal.consumerPass)
        assertEquals("unsupported.recording.subpass_merge_no_input_attachment", refusal.diagnostic.code)
        assertTrue(refusal.diagnostic.terminal)
    }

    @Test
    fun `passes in different scopes are refused`() {
        val passes = listOf(
            candidate(blurH, scopeId = "scope-layer-A"),
            candidate(blurV, scopeId = "scope-layer-B"),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)
        assertEquals("unsupported.recording.subpass_merge_incompatible", analysis.refusedPairs[0].diagnostic.code)
        assertContains(analysis.refusedPairs[0].diagnostic.message, "scope")
    }

    @Test
    fun `intervening barrier breaks mergeability`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV, hasInterveningBarrier = true),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)
        assertEquals("unsupported.recording.subpass_merge_incompatible", analysis.refusedPairs[0].diagnostic.code)
        assertContains(analysis.refusedPairs[0].diagnostic.message, "barrier")
    }

    @Test
    fun `intervening copy prevents merge`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV, hasInterveningCopy = true),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)
        assertContains(analysis.refusedPairs[0].diagnostic.message, "copy")
    }

    @Test
    fun `incompatible color attachment formats prevent merge`() {
        val passes = listOf(
            candidate(blurH, colorAttachmentFormat = "rgba16float"),
            candidate(blurV, colorAttachmentFormat = "rgba8unorm"),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)
        assertContains(analysis.refusedPairs[0].diagnostic.message, "format")
    }

    @Test
    fun `different sample counts prevent merge`() {
        val passes = listOf(
            candidate(blurH, sampleCount = 4),
            candidate(blurV, sampleCount = 1),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)
        assertContains(analysis.refusedPairs[0].diagnostic.message, "sample")
    }

    @Test
    fun `three passes yield two eligible adjacent pairs`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV),
            candidate(sharpen),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(2, analysis.eligiblePairs.size)
        assertEquals(0, analysis.refusedPairs.size)

        assertEquals(blurH, analysis.eligiblePairs[0].producerPass)
        assertEquals(blurV, analysis.eligiblePairs[0].consumerPass)
        assertEquals(blurV, analysis.eligiblePairs[1].producerPass)
        assertEquals(sharpen, analysis.eligiblePairs[1].consumerPass)
    }

    @Test
    fun `intervening dispatch breaks mergeability`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV, hasInterveningDispatch = true),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(1, analysis.refusedPairs.size)
        assertContains(analysis.refusedPairs[0].diagnostic.message, "dispatch")
    }

    @Test
    fun `dump lines produce deterministic evidence without backend handles`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV, hasInterveningBarrier = true),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        val lines = analysis.dumpLines()
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.any { line -> line.contains("backend") && line.contains("handle") })
        assertContains(lines.first(), "passes.subpass-merge eligible=0 refused=1")
        assertTrue(lines.any { line -> line.contains("code=unsupported.recording.subpass_merge_incompatible") })
        assertTrue(lines.any { line -> line.contains("terminal=true") })
    }

    @Test
    fun `dump lines for accepted merge contain attachment indices`() {
        val passes = listOf(
            candidate(blurH),
            candidate(blurV),
        )

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        val lines = analysis.dumpLines()
        assertContains(lines.first(), "eligible=1 refused=0")
        assertTrue(lines.any { line ->
            line.contains("mergable producer=pass:blur-horizontal consumer=pass:blur-vertical") &&
                line.contains("inputAttachmentIndex=0") &&
                line.contains("colorAttachmentIndex=0")
        })
    }

    @Test
    fun `single pass yields empty analysis`() {
        val passes = listOf(candidate(blurH))

        val analysis = analyzeSubpassMerge(passes, capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(0, analysis.refusedPairs.size)
    }

    @Test
    fun `empty pass list yields empty analysis`() {
        val analysis = analyzeSubpassMerge(emptyList(), capableAdapter)

        assertEquals(0, analysis.eligiblePairs.size)
        assertEquals(0, analysis.refusedPairs.size)
    }

    @Test
    fun `GPURenderPassHandle validates non-blank value`() {
        assertIllegalArgument("GPURenderPassHandle.value must not be blank") {
            GPURenderPassHandle("")
        }

        assertIllegalArgument("GPURenderPassHandle.value must not be blank") {
            GPURenderPassHandle("   ")
        }
    }

    @Test
    fun `GPUSubpassMergePlan validates attachment indices`() {
        assertIllegalArgument("inputAttachmentIndex must be non-negative") {
            GPUSubpassMergePlan(
                producerPass = blurH,
                consumerPass = blurV,
                inputAttachmentIndex = -1,
                colorAttachmentIndex = 0,
            )
        }

        assertIllegalArgument("colorAttachmentIndex must be non-negative") {
            GPUSubpassMergePlan(
                producerPass = blurH,
                consumerPass = blurV,
                inputAttachmentIndex = 0,
                colorAttachmentIndex = -1,
            )
        }
    }

    @Test
    fun `GPUSubpassMergeCandidate validates required fields`() {
        assertIllegalArgument("GPUSubpassMergeCandidate.scopeId must not be blank") {
            GPUSubpassMergeCandidate(
                pass = blurH,
                scopeId = "",
                colorAttachmentFormat = "rgba8unorm",
                sampleCount = 1,
            )
        }
        assertIllegalArgument("GPUSubpassMergeCandidate.colorAttachmentFormat must not be blank") {
            GPUSubpassMergeCandidate(
                pass = blurH,
                scopeId = "scope-main",
                colorAttachmentFormat = "",
                sampleCount = 1,
            )
        }
        assertIllegalArgument("GPUSubpassMergeCandidate.sampleCount must be positive") {
            GPUSubpassMergeCandidate(
                pass = blurH,
                scopeId = "scope-main",
                colorAttachmentFormat = "rgba8unorm",
                sampleCount = 0,
            )
        }
    }

    @Test
    fun `GPUSubpassMergeAdapterCapability validates required fields`() {
        assertIllegalArgument("GPUSubpassMergeAdapterCapability.maxColorAttachments must be positive") {
            GPUSubpassMergeAdapterCapability(supportsInputAttachment = true, maxColorAttachments = 0)
        }
    }

    private fun assertIllegalArgument(expectedMessageFragment: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException with message containing: $expectedMessageFragment")
        } catch (e: IllegalArgumentException) {
            assertContains(e.message ?: "", expectedMessageFragment)
        }
    }
}
