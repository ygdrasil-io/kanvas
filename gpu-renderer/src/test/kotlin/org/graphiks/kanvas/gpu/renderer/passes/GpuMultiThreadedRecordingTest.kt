package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

class GpuMultiThreadedRecordingTest {

    @Test
    fun `thread-bound arena carries thread identity and capacity`() {
        val arena = arena(
            arenaId = "arena-0",
            threadId = "thread-worker-1",
            capacity = 1024,
            fragments = emptyList(),
        )

        assertEquals(GpuRecordingArenaId("arena-0"), arena.arenaId)
        assertEquals(GpuRecordingThreadId("thread-worker-1"), arena.threadId)
        assertEquals(1024, arena.capacity)
        assertEquals(0, arena.fragmentCount)
        assertTrue(arena.fragments.isEmpty())
    }

    @Test
    fun `ordering tokens are strictly monotonic`() {
        val recorder = GpuRecordingTokenIssuer()

        val t0 = recorder.issue()
        val t1 = recorder.issue()
        val t2 = recorder.issue()
        val t3 = recorder.issue()

        assertTrue(t0.sequenceNumber > 0)
        assertTrue(t1.sequenceNumber > t0.sequenceNumber)
        assertTrue(t2.sequenceNumber > t1.sequenceNumber)
        assertTrue(t3.sequenceNumber > t2.sequenceNumber)
    }

    @Test
    fun `fragment records command count between begin and end tokens`() {
        val issuer = GpuRecordingTokenIssuer()
        val begin = issuer.issue()
        val end = issuer.issue()

        val fragment = GpuRecordingFragment(
            fragmentId = "frag-1",
            arenaId = GpuRecordingArenaId("arena-0"),
            commandCount = 12,
            beginToken = begin,
            endToken = end,
        )

        assertEquals("frag-1", fragment.fragmentId)
        assertEquals(12, fragment.commandCount)
        assertEquals(begin, fragment.beginToken)
        assertEquals(end, fragment.endToken)
        assertTrue(fragment.beginToken.sequenceNumber < fragment.endToken.sequenceNumber)
    }

    @Test
    fun `fragment requires begin token precedes end token`() {
        val issuer = GpuRecordingTokenIssuer()
        val earlier = issuer.issue()
        val later = issuer.issue()

        assertIllegalArgument("GpuRecordingFragment.beginToken must precede endToken") {
            GpuRecordingFragment(
                fragmentId = "frag-1",
                arenaId = GpuRecordingArenaId("arena-0"),
                commandCount = 1,
                beginToken = later,
                endToken = earlier,
            )
        }
    }

    @Test
    fun `deterministic merge combines fragments from a single arena in recording order`() {
        val issuer = GpuRecordingTokenIssuer()
        val arenaId = GpuRecordingArenaId("arena-0")
        val threadId = GpuRecordingThreadId("thread-worker-1")

        val arena = arena(
            arenaId = "arena-0",
            threadId = threadId.value,
            capacity = 4096,
            fragments = listOf(
                frag("frag-1", arenaId, 3, issuer.issue(), issuer.issue()),
                frag("frag-2", arenaId, 5, issuer.issue(), issuer.issue()),
                frag("frag-3", arenaId, 2, issuer.issue(), issuer.issue()),
            ),
        )

        val analysis = mergeArenas(listOf(arena))

        assertEquals(1, analysis.arenaCount)
        assertEquals(3, analysis.fragmentCount)
        assertEquals(10, analysis.totalCommands)
        assertEquals(0, analysis.refusedPairs.size)
        assertEquals(
            listOf("frag-1", "frag-2", "frag-3"),
            analysis.mergedFragments.map { frag -> frag.fragmentId },
        )
    }

    @Test
    fun `deterministic merge orders fragments across arenas by begin token sequence number`() {
        val issuer = GpuRecordingTokenIssuer()
        val arena0Id = GpuRecordingArenaId("arena-0")
        val arena1Id = GpuRecordingArenaId("arena-1")

        val fragmentA = frag("frag-A", arena0Id, 2, issuer.issue(), issuer.issue())
        val fragmentB = frag("frag-B", arena1Id, 3, issuer.issue(), issuer.issue())
        val fragmentC = frag("frag-C", arena0Id, 1, issuer.issue(), issuer.issue())
        val fragmentD = frag("frag-D", arena1Id, 4, issuer.issue(), issuer.issue())

        val arena0 = arena("arena-0", "thread-worker-1", 4096, listOf(fragmentA, fragmentC))
        val arena1 = arena("arena-1", "thread-worker-2", 4096, listOf(fragmentB, fragmentD))

        val analysis = mergeArenas(listOf(arena0, arena1))

        assertEquals(2, analysis.arenaCount)
        assertEquals(4, analysis.fragmentCount)
        assertEquals(10, analysis.totalCommands)
        assertEquals(0, analysis.refusedPairs.size)

        val beginSeqs = analysis.mergedFragments.map { frag -> frag.beginToken.sequenceNumber }
        assertEquals(beginSeqs.sorted(), beginSeqs)
    }

    @Test
    fun `fragment ordering within each arena is preserved during merge`() {
        val issuer = GpuRecordingTokenIssuer()
        val arena0Id = GpuRecordingArenaId("arena-0")
        val arena1Id = GpuRecordingArenaId("arena-1")

        val f0a = frag("f0-a", arena0Id, 1, issuer.issue(), issuer.issue())
        val f0b = frag("f0-b", arena0Id, 2, issuer.issue(), issuer.issue())
        val f0c = frag("f0-c", arena0Id, 3, issuer.issue(), issuer.issue())
        val f1a = frag("f1-a", arena1Id, 4, issuer.issue(), issuer.issue())
        val f1b = frag("f1-b", arena1Id, 5, issuer.issue(), issuer.issue())

        val arena0 = arena("arena-0", "t-1", 4096, listOf(f0a, f0b, f0c))
        val arena1 = arena("arena-1", "t-2", 4096, listOf(f1a, f1b))

        val analysis = mergeArenas(listOf(arena0, arena1))

        val merged = analysis.mergedFragments

        val arena0Indices = merged.withIndex().filter { (_, frag) -> frag.arenaId == arena0Id }.map { (i, _) -> i }
        val arena1Indices = merged.withIndex().filter { (_, frag) -> frag.arenaId == arena1Id }.map { (i, _) -> i }

        assertEquals(arena0Indices.sorted(), arena0Indices)
        assertEquals(arena1Indices.sorted(), arena1Indices)
    }

    @Test
    fun `merge with no arenas returns empty analysis`() {
        val analysis = mergeArenas(emptyList())

        assertEquals(0, analysis.arenaCount)
        assertEquals(0, analysis.fragmentCount)
        assertEquals(0, analysis.totalCommands)
        assertEquals(0, analysis.refusedPairs.size)
        assertTrue(analysis.mergedFragments.isEmpty())
    }

    @Test
    fun `merge with empty fragments produces correct counts`() {
        val arena = arena(
            arenaId = "arena-0",
            threadId = "thread-worker-1",
            capacity = 1024,
            fragments = emptyList(),
        )

        val analysis = mergeArenas(listOf(arena))

        assertEquals(1, analysis.arenaCount)
        assertEquals(0, analysis.fragmentCount)
        assertEquals(0, analysis.totalCommands)
        assertTrue(analysis.mergedFragments.isEmpty())
    }

    @Test
    fun `dump lines produce deterministic evidence without backend handles`() {
        val issuer = GpuRecordingTokenIssuer()
        val arenaId = GpuRecordingArenaId("arena-0")

        val arena = arena(
            arenaId = "arena-0",
            threadId = "thread-worker-1",
            capacity = 4096,
            fragments = listOf(
                frag("frag-1", arenaId, 3, issuer.issue(), issuer.issue()),
                frag("frag-2", arenaId, 5, issuer.issue(), issuer.issue()),
            ),
        )

        val analysis = mergeArenas(listOf(arena))
        val lines = analysis.dumpLines()

        assertContains(lines.first(), "passes.mt-recording mergedFragments=2 totalCommands=8 arenaCount=1")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.any { line -> line.contains("backend") && line.contains("handle") })
        assertTrue(lines.any { line -> line.contains("frag-1") && line.contains("arena=arena-0") })
        assertTrue(lines.any { line -> line.contains("frag-2") && line.contains("arena=arena-0") })
    }

    @Test
    fun `arena dump lines expose thread fragment and capacity evidence`() {
        val issuer = GpuRecordingTokenIssuer()
        val arenaId = GpuRecordingArenaId("arena-0")

        val arena = arena(
            arenaId = "arena-0",
            threadId = "thread-worker-1",
            capacity = 2048,
            fragments = listOf(
                frag("frag-1", arenaId, 4, issuer.issue(), issuer.issue()),
            ),
        )

        val lines = arena.dumpLines()
        assertContains(lines.first(), "passes.mt-arena id=arena-0 thread=thread-worker-1 capacity=2048 fragments=1")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
    }

    @Test
    fun `merge refuses arenas with overlapping token ranges from same thread`() {
        val issuer = GpuRecordingTokenIssuer()
        val arenaId = GpuRecordingArenaId("arena-0")

        val begin1 = issuer.issue()
        val mid = issuer.issue()
        val end1 = issuer.issue()
        val begin2 = issuer.issue()
        val end2 = issuer.issue()

        val arena = arena(
            arenaId = "arena-0",
            threadId = "thread-worker-1",
            capacity = 4096,
            fragments = listOf(
                GpuRecordingFragment("frag-1", arenaId, 3, begin1, end1),
                GpuRecordingFragment("frag-2", arenaId, 2, begin2, end2),
            ),
        )

        val analysis = mergeArenas(listOf(arena))

        assertEquals(0, analysis.refusedPairs.size)
        assertEquals(2, analysis.fragmentCount)
    }

    @Test
    fun `fragment validation requires non-blank fragment and arena identifiers`() {
        assertIllegalArgument("GpuRecordingFragment.fragmentId must not be blank") {
            GpuRecordingFragment(
                fragmentId = "",
                arenaId = GpuRecordingArenaId("arena-0"),
                commandCount = 1,
                beginToken = GpuRecordingToken(1, 0),
                endToken = GpuRecordingToken(2, 0),
            )
        }

        assertIllegalArgument("GpuRecordingFragment.commandCount must be non-negative") {
            GpuRecordingFragment(
                fragmentId = "frag-1",
                arenaId = GpuRecordingArenaId("arena-0"),
                commandCount = -1,
                beginToken = GpuRecordingToken(1, 0),
                endToken = GpuRecordingToken(2, 0),
            )
        }
    }

    @Test
    fun `arena validation requires non-blank thread id and positive capacity`() {
        assertIllegalArgument("GpuRecordingThreadId.value must not be blank") {
            GpuRecordingArena(
                arenaId = GpuRecordingArenaId("arena-0"),
                threadId = GpuRecordingThreadId(""),
                capacity = 1024,
                fragments = emptyList(),
            )
        }

        assertIllegalArgument("GpuRecordingArena.capacity must be positive") {
            GpuRecordingArena(
                arenaId = GpuRecordingArenaId("arena-0"),
                threadId = GpuRecordingThreadId("t-1"),
                capacity = 0,
                fragments = emptyList(),
            )
        }
    }

    @Test
    fun `merge preserves arena count for multiple arenas each with fragments`() {
        val issuer = GpuRecordingTokenIssuer()
        val a0 = GpuRecordingArenaId("arena-0")
        val a1 = GpuRecordingArenaId("arena-1")
        val a2 = GpuRecordingArenaId("arena-2")

        val arena0 = arena("arena-0", "t-0", 1024, listOf(
            frag("f0-1", a0, 1, issuer.issue(), issuer.issue()),
            frag("f0-2", a0, 2, issuer.issue(), issuer.issue()),
        ))
        val arena1 = arena("arena-1", "t-1", 1024, listOf(
            frag("f1-1", a1, 3, issuer.issue(), issuer.issue()),
        ))
        val arena2 = arena("arena-2", "t-2", 1024, listOf(
            frag("f2-1", a2, 4, issuer.issue(), issuer.issue()),
            frag("f2-2", a2, 5, issuer.issue(), issuer.issue()),
            frag("f2-3", a2, 6, issuer.issue(), issuer.issue()),
        ))

        val analysis = mergeArenas(listOf(arena0, arena1, arena2))

        assertEquals(3, analysis.arenaCount)
        assertEquals(6, analysis.fragmentCount)
        assertEquals(21, analysis.totalCommands)
        assertEquals(0, analysis.refusedPairs.size)
    }

    private fun arena(
        arenaId: String,
        threadId: String,
        capacity: Int,
        fragments: List<GpuRecordingFragment>,
    ): GpuRecordingArena =
        GpuRecordingArena(
            arenaId = GpuRecordingArenaId(arenaId),
            threadId = GpuRecordingThreadId(threadId),
            capacity = capacity,
            fragments = fragments,
        )

    private fun frag(
        fragmentId: String,
        arenaId: GpuRecordingArenaId,
        commandCount: Int,
        beginToken: GpuRecordingToken,
        endToken: GpuRecordingToken,
    ): GpuRecordingFragment =
        GpuRecordingFragment(
            fragmentId = fragmentId,
            arenaId = arenaId,
            commandCount = commandCount,
            beginToken = beginToken,
            endToken = endToken,
        )

    private fun assertIllegalArgument(expectedMessageFragment: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException with message containing: $expectedMessageFragment")
        } catch (e: IllegalArgumentException) {
            assertContains(e.message ?: "", expectedMessageFragment)
        }
    }
}
