package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GPUSolidRectSessionNativeCacheTest {
    @Test
    fun `two compatible frames reuse one invariant allocation`() {
        val handles = ClosingHandle()
        var creations = 0
        val cache = GPUSolidRectSessionNativeCache<DescriptorKey, ClosingHandle> {
            creations += 1
            handles
        }
        val key = DescriptorKey(format = "rgba8unorm", sampleCount = 1)

        assertSame(handles, cache.acquire(key))
        assertSame(handles, cache.acquire(key.copy()))

        assertEquals(1, creations)
        assertEquals(
            GPUSolidRectNativeCacheCounters(
                invariantCreations = 1,
                invariantReuses = 1,
                invariantInvalidations = 0,
            ),
            cache.counters(),
        )

        cache.close()
        assertEquals(1, handles.closeCount)
    }

    @Test
    fun `real descriptor change replaces only the incompatible allocation`() {
        val handles = mutableListOf<ClosingHandle>()
        val cache = GPUSolidRectSessionNativeCache<DescriptorKey, ClosingHandle> {
            ClosingHandle().also(handles::add)
        }

        val first = cache.acquire(DescriptorKey(format = "rgba8unorm", sampleCount = 1))
        val second = cache.acquire(DescriptorKey(format = "rgba8unorm", sampleCount = 4))

        assertSame(handles[0], first)
        assertSame(handles[1], second)
        assertEquals(1, handles[0].closeCount)
        assertEquals(0, handles[1].closeCount)
        assertEquals(1, cache.counters().invariantInvalidations)

        cache.close()
        assertEquals(1, handles[0].closeCount)
        assertEquals(1, handles[1].closeCount)
    }

    private data class DescriptorKey(val format: String, val sampleCount: Int)

    private class ClosingHandle : AutoCloseable {
        var closeCount = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }
}
