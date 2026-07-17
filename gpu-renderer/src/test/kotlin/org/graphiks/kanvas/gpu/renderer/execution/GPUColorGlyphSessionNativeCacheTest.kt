package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class GPUColorGlyphSessionNativeCacheTest {
    @Test
    fun `two identical frames reuse invariants and atlas`() {
        val invariant = ClosingHandle("invariant")
        val atlas = ClosingHandle("atlas")
        var invariantCreations = 0
        var atlasCreations = 0
        val cache = GPUColorGlyphSessionNativeCache(
            keyOf = { source: AtlasSource -> source.key },
            invariantFactory = { invariantCreations += 1; invariant },
            atlasFactory = { _: AtlasSource -> atlasCreations += 1; atlas },
        )
        val source = AtlasSource(key())

        val first = cache.acquire(source)
        val second = cache.acquire(source.copy())

        assertSame(invariant, first.invariants)
        assertSame(invariant, second.invariants)
        assertSame(atlas, first.atlas)
        assertSame(atlas, second.atlas)
        assertEquals(1, invariantCreations)
        assertEquals(1, atlasCreations)
        assertEquals(
            GPUColorGlyphNativeCacheCounters(
                invariantCreations = 1,
                atlasCreations = 1,
                atlasUploads = 1,
                atlasReuses = 1,
                atlasInvalidations = 0,
                currentAtlasBytes = 2,
                atlasPeakResidentBytes = 2,
            ),
            cache.counters(),
        )
    }

    @Test
    fun `changed atlas generation replaces and closes only the old atlas`() {
        val invariant = ClosingHandle("invariant")
        val atlases = mutableListOf<ClosingHandle>()
        val cache = GPUColorGlyphSessionNativeCache(
            keyOf = { source: AtlasSource -> source.key },
            invariantFactory = { invariant },
            atlasFactory = { source: AtlasSource ->
                ClosingHandle("atlas-${source.key.generation}").also(atlases::add)
            },
        )

        val first = cache.acquire(AtlasSource(key(generation = 2)))
        val second = cache.acquire(AtlasSource(key(generation = 3)))

        assertSame(atlases[0], first.atlas)
        assertSame(atlases[1], second.atlas)
        assertEquals(1, atlases[0].closeCount)
        assertEquals(0, atlases[1].closeCount)
        assertEquals(0, invariant.closeCount)
        assertEquals(1, cache.counters().atlasInvalidations)

        cache.close()

        assertEquals(1, atlases[0].closeCount)
        assertEquals(1, atlases[1].closeCount)
        assertEquals(1, invariant.closeCount)
    }

    @Test
    fun `same caller artifact identity with different exact bytes digest invalidates atlas`() {
        val atlases = mutableListOf<ClosingHandle>()
        val cache = GPUColorGlyphSessionNativeCache(
            keyOf = { source: AtlasSource -> source.key },
            invariantFactory = { ClosingHandle("invariant") },
            atlasFactory = { source: AtlasSource ->
                ClosingHandle("atlas-${source.key.atlasBytesSha256.take(8)}").also(atlases::add)
            },
        )
        val firstKey = key(
            atlasBytesSha256 = "00".repeat(32),
            byteSize = 2,
        )
        val changedBytesKey = firstKey.copy(atlasBytesSha256 = "ff".repeat(32))

        val first = cache.acquire(AtlasSource(firstKey))
        val second = cache.acquire(AtlasSource(changedBytesKey))

        assertNotSame(first.atlas, second.atlas)
        assertEquals(1, atlases[0].closeCount)
        assertEquals(0, atlases[1].closeCount)
        assertEquals(1L, cache.counters().atlasInvalidations)
        assertEquals(2L, cache.counters().currentAtlasBytes)
        assertEquals(4L, cache.counters().atlasPeakResidentBytes)

        cache.close()
    }

    @Test
    fun `replacement peak byte accounting refuses signed overflow before allocation`() {
        var atlasCreations = 0
        val cache = GPUColorGlyphSessionNativeCache(
            keyOf = { source: AtlasSource -> source.key },
            invariantFactory = { ClosingHandle("invariant") },
            atlasFactory = { _: AtlasSource ->
                atlasCreations += 1
                ClosingHandle("atlas-$atlasCreations")
            },
        )
        cache.acquire(AtlasSource(key(byteSize = Long.MAX_VALUE)))

        kotlin.test.assertFailsWith<ArithmeticException> {
            cache.acquire(
                AtlasSource(
                    key(
                        atlasBytesSha256 = "11".repeat(32),
                        byteSize = 1,
                    ),
                ),
            )
        }

        assertEquals(1, atlasCreations)
        assertEquals(0L, cache.counters().atlasInvalidations)
        cache.close()
    }

    private data class AtlasSource(val key: GPUColorGlyphNativeAtlasCacheKey)

    private class ClosingHandle(val label: String) : AutoCloseable {
        var closeCount = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }

    private fun key(
        generation: Int = 2,
        atlasBytesSha256: String = "00".repeat(32),
        byteSize: Long = 2,
    ) = GPUColorGlyphNativeAtlasCacheKey(
        artifactId = "550e8400-e29b-41d4-a716-446655440073",
        generation = generation,
        contentFingerprint = "atlas-content",
        atlasBytesSha256 = atlasBytesSha256,
        byteSize = byteSize,
        width = 64,
        height = 32,
        format = "r8unorm",
    )
}
