package org.skia.foundation

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * R-suivi.6 — round-trip the [SkGraphics] resource cache : put / get
 * / size / byte-limit eviction / purge.
 *
 * The cache is process-wide so each test purges before and after to
 * isolate state.
 */
class SkGraphicsResourceCacheTest {

    private var savedLimit: Long = 0L

    @BeforeEach
    fun setUp() {
        savedLimit = SkGraphics.GetResourceCacheTotalByteLimit()
        SkGraphics.PurgeResourceCache()
    }

    @AfterEach
    fun tearDown() {
        SkGraphics.PurgeResourceCache()
        SkGraphics.SetResourceCacheTotalByteLimit(savedLimit)
    }

    @Test
    fun `default byte limit is 128 MiB`() {
        assertEquals(128L * 1024L * 1024L, savedLimit)
    }

    @Test
    fun `cache starts empty`() {
        assertEquals(0L, SkGraphics.GetResourceCacheTotalBytesUsed())
    }

    @Test
    fun `put then get round-trips bytes`() {
        val k = "key-1"
        val v = ByteArray(32) { it.toByte() }
        invokePut(k, v)
        val got = invokeGet(k)
        assertNotNull(got)
        assertEquals(32, got!!.size)
        assertEquals(0.toByte(), got[0])
        assertEquals(31.toByte(), got[31])
        assertEquals(32L, SkGraphics.GetResourceCacheTotalBytesUsed())
    }

    @Test
    fun `get returns null for unknown key`() {
        assertNull(invokeGet("never-inserted"))
    }

    @Test
    fun `put replaces existing key without double-counting bytes`() {
        invokePut("key", ByteArray(10))
        assertEquals(10L, SkGraphics.GetResourceCacheTotalBytesUsed())
        invokePut("key", ByteArray(5))
        assertEquals(5L, SkGraphics.GetResourceCacheTotalBytesUsed())
        assertEquals(5, invokeGet("key")!!.size)
    }

    @Test
    fun `PurgeResourceCache empties the cache`() {
        invokePut("a", ByteArray(100))
        invokePut("b", ByteArray(200))
        assertTrue(SkGraphics.GetResourceCacheTotalBytesUsed() > 0L)
        SkGraphics.PurgeResourceCache()
        assertEquals(0L, SkGraphics.GetResourceCacheTotalBytesUsed())
        assertNull(invokeGet("a"))
        assertNull(invokeGet("b"))
    }

    @Test
    fun `SetResourceCacheTotalByteLimit returns previous and evicts FIFO when shrunk`() {
        SkGraphics.SetResourceCacheTotalByteLimit(1024L)
        invokePut("a", ByteArray(400))
        invokePut("b", ByteArray(400))
        invokePut("c", ByteArray(100))
        assertEquals(900L, SkGraphics.GetResourceCacheTotalBytesUsed())

        // Shrink to 600 — should evict 'a' (oldest), keep 'b' and 'c'.
        val prev = SkGraphics.SetResourceCacheTotalByteLimit(600L)
        assertEquals(1024L, prev)
        assertTrue(SkGraphics.GetResourceCacheTotalBytesUsed() <= 600L)
        assertNull(invokeGet("a"))
        assertNotNull(invokeGet("b"))
        assertNotNull(invokeGet("c"))
    }

    @Test
    fun `put evicts oldest entries when over byte limit`() {
        SkGraphics.SetResourceCacheTotalByteLimit(500L)
        invokePut("a", ByteArray(300))
        invokePut("b", ByteArray(300)) // 600 > 500, evict 'a'.
        assertNull(invokeGet("a"))
        assertNotNull(invokeGet("b"))
        assertEquals(300L, SkGraphics.GetResourceCacheTotalBytesUsed())
    }

    @Test
    fun `put rejects single entries larger than the byte limit`() {
        SkGraphics.SetResourceCacheTotalByteLimit(100L)
        invokePut("oversized", ByteArray(200))
        assertNull(invokeGet("oversized"))
        assertEquals(0L, SkGraphics.GetResourceCacheTotalBytesUsed())
    }

    @Test
    fun `DumpMemoryStatistics emits size, budget, and entry count`() {
        invokePut("a", ByteArray(64))
        val records = mutableListOf<Triple<String, String, Long>>()
        SkGraphics.DumpMemoryStatistics(object : SkGraphics.SkTraceMemoryDump {
            override fun dumpNumericValue(dumpName: String, valueName: String, units: String, value: Long) {
                records += Triple(dumpName, valueName, value)
            }
        })
        assertEquals(3, records.size)
        val byName = records.associate { it.second to it.third }
        assertEquals(64L, byName["size"])
        assertEquals(SkGraphics.GetResourceCacheTotalByteLimit(), byName["budget"])
        assertEquals(1L, byName["entries"])
    }

    // The `resourceCachePut` / `resourceCacheGet` helpers are `internal`
    // because the public Skia surface only exposes lifecycle methods on
    // the cache, not insertion. Tests live in the same Gradle module, so
    // they call the internal helpers directly.

    private fun invokePut(key: String, value: ByteArray) {
        SkGraphics.resourceCachePut(key, value)
    }

    private fun invokeGet(key: String): ByteArray? =
        SkGraphics.resourceCacheGet(key)
}
