package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

class GpuDeferredDisplayListTest {
    @Test
    fun `deferred display list captures recorded commands analysis layer plans and compatibility key`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001", "cmd-002", "cmd-003"),
            analysisHash = "analysis-hash-a1b2c3",
            layerPlanIds = listOf("layer-plan-root", "layer-plan-overlay"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 0x7a3f2b1cL,
                replayCompatibleFields = setOf("targetSurface"),
            ),
        )

        assertEquals("recording-main", dl.recordingId)
        assertEquals(listOf("cmd-001", "cmd-002", "cmd-003"), dl.recordedCommandIds)
        assertEquals("analysis-hash-a1b2c3", dl.analysisHash)
        assertEquals(listOf("layer-plan-root", "layer-plan-overlay"), dl.layerPlanIds)
        assertEquals("recording-main", dl.compatibilityKey.recordingId)
        assertEquals(0x7a3f2b1cL, dl.compatibilityKey.commandHash)
        assertEquals(setOf("targetSurface"), dl.compatibilityKey.replayCompatibleFields)
    }

    @Test
    fun `compatibility check rejects mismatched recording id`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-A",
            recordedCommandIds = listOf("cmd-001"),
            analysisHash = "hash-a",
            layerPlanIds = listOf("lp-1"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-A",
                commandHash = 100L,
                replayCompatibleFields = setOf("composedCtm"),
            ),
        )

        val replayKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-B",
            commandHash = 100L,
            replayCompatibleFields = setOf("composedCtm"),
        )

        val result = checkReplayCompatibility(dl, replayKey)
        val refused = assertIs<GpuDeferredDisplayListReplayResult.Refused>(result)
        assertEquals("unsupported.recording.deferred_incompatible_replay", refused.diagnostic.code)
        assertEquals("recording", refused.diagnostic.stage)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `compatibility check rejects mismatched command hash`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001"),
            analysisHash = "hash-a",
            layerPlanIds = listOf("lp-1"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 42L,
                replayCompatibleFields = setOf("composedCtm"),
            ),
        )

        val replayKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-main",
            commandHash = 99L,
            replayCompatibleFields = setOf("composedCtm"),
        )

        val result = checkReplayCompatibility(dl, replayKey)
        val refused = assertIs<GpuDeferredDisplayListReplayResult.Refused>(result)
        assertEquals("unsupported.recording.deferred_incompatible_replay", refused.diagnostic.code)
        assertEquals("recording", refused.diagnostic.stage)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `compatibility check rejects format change`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001"),
            analysisHash = "hash-a",
            layerPlanIds = listOf("lp-1"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 100L,
                replayCompatibleFields = setOf("composedCtm"),
                targetFormatClass = "rgba8unorm",
            ),
        )

        val replayKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-main",
            commandHash = 100L,
            replayCompatibleFields = setOf("composedCtm"),
            targetFormatClass = "bgra8unorm",
        )

        val result = checkReplayCompatibility(dl, replayKey)
        val refused = assertIs<GpuDeferredDisplayListReplayResult.Refused>(result)
        assertContains(refused.diagnostic.message, "targetFormatClass")
    }

    @Test
    fun `compatibility check rejects capability class change`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001"),
            analysisHash = "hash-a",
            layerPlanIds = listOf("lp-1"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 100L,
                replayCompatibleFields = setOf("composedCtm"),
                capabilityClass = "msaa-4x",
            ),
        )

        val replayKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-main",
            commandHash = 100L,
            replayCompatibleFields = setOf("composedCtm"),
            capabilityClass = "msaa-8x",
        )

        val result = checkReplayCompatibility(dl, replayKey)
        val refused = assertIs<GpuDeferredDisplayListReplayResult.Refused>(result)
        assertContains(refused.diagnostic.message, "capabilityClass")
    }

    @Test
    fun `compatibility check rejects device identity change`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001"),
            analysisHash = "hash-a",
            layerPlanIds = listOf("lp-1"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 100L,
                replayCompatibleFields = setOf("composedCtm"),
                deviceIdentity = "device-a",
            ),
        )

        val replayKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-main",
            commandHash = 100L,
            replayCompatibleFields = setOf("composedCtm"),
            deviceIdentity = "device-b",
        )

        val result = checkReplayCompatibility(dl, replayKey)
        val refused = assertIs<GpuDeferredDisplayListReplayResult.Refused>(result)
        assertContains(refused.diagnostic.message, "deviceIdentity")
    }

    @Test
    fun `compatibility check accepts matching replay with different ctm and clip`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001", "cmd-002"),
            analysisHash = "analysis-hash",
            layerPlanIds = listOf("lp-root"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 777L,
                replayCompatibleFields = setOf("composedCtm", "intersectionClip", "targetSurface"),
            ),
        )

        val replayKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-main",
            commandHash = 777L,
            replayCompatibleFields = setOf("composedCtm", "intersectionClip", "targetSurface"),
        )

        val result = checkReplayCompatibility(dl, replayKey)
        val accepted = assertIs<GpuDeferredDisplayListReplayResult.Accepted>(result)
        assertNotNull(accepted.replayPlan)
    }

    @Test
    fun `replay plan carries composed ctm clip and target surface identities`() {
        val replayPlan = GPUDeferredDisplayListReplayPlan(
            displayListId = "recording-main",
            composedCtmIdentity = "ctm-translate-100-200",
            intersectionClipIdentity = "clip-rect-0-0-512-512",
            targetSurfaceIdentity = "target-rgba8-1920x1080",
        )

        assertEquals("recording-main", replayPlan.displayListId)
        assertEquals("ctm-translate-100-200", replayPlan.composedCtmIdentity)
        assertEquals("clip-rect-0-0-512-512", replayPlan.intersectionClipIdentity)
        assertEquals("target-rgba8-1920x1080", replayPlan.targetSurfaceIdentity)
    }

    @Test
    fun `cache plan configures max entries and eviction policy with validation`() {
        val cachePlan = GPUDeferredDisplayListCachePlan(
            maxEntries = 64,
            evictionPolicy = GPUCacheEvictionPolicy.LRU,
        )

        assertEquals(64, cachePlan.maxEntries)
        assertEquals(GPUCacheEvictionPolicy.LRU, cachePlan.evictionPolicy)

        assertIllegalArgument("GPUDeferredDisplayListCachePlan.maxEntries must be positive") {
            GPUDeferredDisplayListCachePlan(maxEntries = 0, evictionPolicy = GPUCacheEvictionPolicy.LRU)
        }
    }

    @Test
    fun `cache plan fromPolicyLabel parses recognised labels`() {
        val lru = GPUDeferredDisplayListCachePlan.fromPolicyLabel(32, "lru")
        assertEquals(GPUCacheEvictionPolicy.LRU, lru.evictionPolicy)
        assertEquals(32, lru.maxEntries)

        val fifo = GPUDeferredDisplayListCachePlan.fromPolicyLabel(16, "FIFO")
        assertEquals(GPUCacheEvictionPolicy.FIFO, fifo.evictionPolicy)

        val clock = GPUDeferredDisplayListCachePlan.fromPolicyLabel(8, "CLOCK")
        assertEquals(GPUCacheEvictionPolicy.CLOCK, clock.evictionPolicy)

        assertIllegalArgument("Unrecognised eviction policy: unknown") {
            GPUDeferredDisplayListCachePlan.fromPolicyLabel(10, "unknown")
        }
    }

    @Test
    fun `compatibility key validates required fields`() {
        assertIllegalArgument("GPUDeferredDisplayListCompatibilityKey.recordingId must not be blank") {
            GPUDeferredDisplayListCompatibilityKey(
                recordingId = "",
                commandHash = 1L,
                replayCompatibleFields = emptySet(),
            )
        }
    }

    @Test
    fun `deferred display list validates required fields`() {
        assertIllegalArgument("GPUDeferredDisplayList.recordingId must not be blank") {
            GPUDeferredDisplayList(
                recordingId = "",
                recordedCommandIds = listOf("cmd-001"),
                analysisHash = "hash",
                layerPlanIds = listOf("lp-1"),
                compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                    recordingId = "recording-main",
                    commandHash = 1L,
                    replayCompatibleFields = emptySet(),
                ),
            )
        }

        assertIllegalArgument("GPUDeferredDisplayList.analysisHash must not be blank") {
            GPUDeferredDisplayList(
                recordingId = "recording-main",
                recordedCommandIds = listOf("cmd-001"),
                analysisHash = "",
                layerPlanIds = listOf("lp-1"),
                compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                    recordingId = "recording-main",
                    commandHash = 1L,
                    replayCompatibleFields = emptySet(),
                ),
            )
        }

        assertIllegalArgument("GPUDeferredDisplayList.compatibilityKey recordingId recording-main does not match displayList recordingId mismatch") {
            GPUDeferredDisplayList(
                recordingId = "mismatch",
                recordedCommandIds = listOf("cmd-001"),
                analysisHash = "hash",
                layerPlanIds = listOf("lp-1"),
                compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                    recordingId = "recording-main",
                    commandHash = 1L,
                    replayCompatibleFields = emptySet(),
                ),
            )
        }
    }

    @Test
    fun `deferred display list dump lines produce deterministic evidence for replay cache and plan`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001", "cmd-002"),
            analysisHash = "analysis-hash",
            layerPlanIds = listOf("lp-root"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 0xfeedL,
                replayCompatibleFields = setOf("composedCtm", "intersectionClip"),
                targetFormatClass = "rgba8unorm",
                capabilityClass = "msaa-4x",
            ),
        )

        val dlLines = dl.dumpLines()
        assertContains(dlLines.first(), "passes.deferred-dl id=recording-main")
        assertContains(dlLines.first(), "commands=cmd-001,cmd-002")
        assertContains(dlLines.first(), "analysis=analysis-hash")
        assertContains(dlLines.first(), "layers=lp-root")
        assertFalse(dlLines.joinToString("\n").contains("WGPU"))

        val keyLines = dl.compatibilityKey.dumpLines()
        assertContains(keyLines.first(), "passes.deferred-dl-compat-key recording=recording-main")
        assertContains(keyLines.first(), "commandHash=65261")
        assertContains(keyLines.first(), "format=rgba8unorm")
        assertContains(keyLines.first(), "capability=msaa-4x")
        assertContains(keyLines.first(), "device=none")
        assertContains(keyLines.first(), "compatibleFields=composedCtm,intersectionClip")

        val cachePlan = GPUDeferredDisplayListCachePlan(maxEntries = 32, evictionPolicy = GPUCacheEvictionPolicy.LRU)
        val cacheLines = cachePlan.dumpLines()
        assertContains(cacheLines.first(), "passes.deferred-dl-cache maxEntries=32")
        assertContains(cacheLines.first(), "policy=LRU")
    }

    @Test
    fun `replay with mismatched recording id produces refusal in replay result`() {
        val dl = GPUDeferredDisplayList(
            recordingId = "recording-main",
            recordedCommandIds = listOf("cmd-001"),
            analysisHash = "hash-a",
            layerPlanIds = listOf("lp-1"),
            compatibilityKey = GPUDeferredDisplayListCompatibilityKey(
                recordingId = "recording-main",
                commandHash = 100L,
                replayCompatibleFields = setOf("composedCtm"),
            ),
        )

        val rejectKey = GPUDeferredDisplayListCompatibilityKey(
            recordingId = "recording-foreign",
            commandHash = 100L,
            replayCompatibleFields = setOf("composedCtm"),
        )

        val replayResult = replayDeferred(dl, rejectKey)
        val refused = assertIs<GpuDeferredDisplayListReplayResult.Refused>(replayResult)
        assertEquals("unsupported.recording.deferred_incompatible_replay", refused.diagnostic.code)
        assertEquals("recording", refused.diagnostic.stage)
        assertTrue(refused.diagnostic.terminal)
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
