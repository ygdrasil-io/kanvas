package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GPUPreparedTextFrameCountersTest {
    @Test
    fun `text counters retain A8 COLRv0 and path stroke as disjoint lanes`() {
        val counters = GPUPreparedTextFrameCounters(
            a8Instances = 11,
            colorGlyphInstances = 7,
            pathStrokeDraws = 3,
            pageCount = 2,
            pageBytes = 8_192,
            subRuns = 5,
            draws = 8,
            bindGroups = 5,
            submits = 1,
        )

        assertEquals(11, counters.a8Instances)
        assertEquals(7, counters.colorGlyphInstances)
        assertEquals(3, counters.pathStrokeDraws)
        assertEquals(18, counters.atlasInstances)
        assertEquals(2, counters.pageCount)
        assertEquals(8_192, counters.pageBytes)
        assertEquals(5, counters.subRuns)
        assertEquals(8, counters.draws)
        assertEquals(5, counters.bindGroups)
        assertEquals(1, counters.submits)
    }

    @Test
    fun `text counters reject negative or multi submit evidence`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedTextFrameCounters(a8Instances = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedTextFrameCounters(submits = 2)
        }
    }

    @Test
    fun `cold frame samples keep every sample and use the specified nearest rank indices`() {
        val raw = listOf(
            31L, 2L, 29L, 4L, 27L, 6L, 25L, 8L, 23L, 10L,
            21L, 12L, 19L, 14L, 17L, 16L, 15L, 18L, 13L, 20L,
            11L, 22L, 9L, 24L, 7L, 26L, 5L, 28L, 3L, 30L,
        )

        val evidence = GPUPreparedTextColdFrameSamples.from(raw)

        assertEquals((2L..31L).toList(), evidence.sortedNanoseconds)
        assertEquals(16L, evidence.p50Nanoseconds)
        assertEquals(29L, evidence.p95Nanoseconds)
        assertEquals(14, evidence.p50Index)
        assertEquals(27, evidence.p95Index)
        assertEquals(30, evidence.sampleCount)
    }

    @Test
    fun `cold frame evidence refuses fewer than thirty independent samples`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedTextColdFrameSamples.from(List(29) { index -> index.toLong() + 1L })
        }
    }
}
