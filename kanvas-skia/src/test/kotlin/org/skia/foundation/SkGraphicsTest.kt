package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * R1 stub-coverage tests for [SkGraphics]. All caches report
 * 0 used / unbounded limit, mutating setters round-trip the
 * previous value.
 */
class SkGraphicsTest {

    @Test
    fun `Init and Term are no-ops and don't throw`() {
        SkGraphics.Init()
        SkGraphics.Term()
    }

    @Test
    fun `font cache reports zero used`() {
        assertEquals(0L, SkGraphics.GetFontCacheUsed())
        assertEquals(0, SkGraphics.GetFontCacheCountUsed())
    }

    @Test
    fun `font cache limit setter returns previous value`() {
        val prev = SkGraphics.GetFontCacheLimit()
        val returned = SkGraphics.SetFontCacheLimit(1024L)
        assertEquals(prev, returned)
        assertEquals(1024L, SkGraphics.GetFontCacheLimit())
        // Restore for test isolation
        SkGraphics.SetFontCacheLimit(prev)
    }

    @Test
    fun `resource cache reports zero used`() {
        assertEquals(0L, SkGraphics.GetResourceCacheTotalBytesUsed())
    }

    @Test
    fun `DumpMemoryStatistics writes nothing`() {
        var calls = 0
        val sink = object : SkGraphics.SkTraceMemoryDump {
            override fun dumpNumericValue(dumpName: String, valueName: String, units: String, value: Long) {
                calls++
            }
        }
        SkGraphics.DumpMemoryStatistics(sink)
        assertEquals(0, calls)
    }

    @Test
    fun `PurgeAllCaches and PurgeFontCache are no-ops`() {
        SkGraphics.PurgeAllCaches()
        SkGraphics.PurgeFontCache()
        SkGraphics.PurgePinnedFontCache()
        SkGraphics.PurgeResourceCache()
    }

    @Test
    fun `SetFlags accepts arbitrary bits`() {
        SkGraphics.SetFlags(0)
        SkGraphics.SetFlags(0x1F)
    }
}
