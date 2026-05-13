package org.skia.foundation

/**
 * Iso-aligned port of Skia's `SkGraphics`
 * ([include/core/SkGraphics.h](https://github.com/google/skia/blob/main/include/core/SkGraphics.h)).
 *
 * Process-wide knobs for Skia's global caches (font, resource,
 * typeface) and runtime hooks (image-generator factory, OpenType
 * SVG decoder factory).
 *
 * **R1 status — no-op / stub.** The JVM port has no equivalent of
 * upstream's static caches, so:
 *  * [Init] / [Term] do nothing.
 *  * The cache-size getters return `0` and the cache limits return
 *    [Long.MAX_VALUE] (i.e. "no enforced limit").
 *  * Purge methods are no-ops.
 *  * [DumpMemoryStatistics] accepts a dumper and writes nothing.
 *
 * The public surface is kept faithful so callers porting GMs that
 * touch `SkGraphics::SetFontCacheLimit` etc. compile unmodified.
 */
public object SkGraphics {

    private var fontCacheLimit: Long = Long.MAX_VALUE
    private var fontCacheCountLimit: Int = Int.MAX_VALUE
    private var typefaceCacheCountLimit: Int = Int.MAX_VALUE
    private var resourceCacheTotalByteLimit: Long = Long.MAX_VALUE
    private var resourceCacheSingleAllocationByteLimit: Long = 0L
    private var flags: Int = 0

    /**
     * Initialise Skia. R1 no-op — the JVM port has no static
     * tables to populate.
     */
    public fun Init() { /* no-op */ }

    /**
     * Tear down Skia. R1 no-op — present for API symmetry. Not in
     * upstream's header anymore but historically paired with [Init].
     */
    public fun Term() { /* no-op */ }

    /** Set debug / behaviour flags. Stored but otherwise unused. */
    public fun SetFlags(flags: Int) {
        this.flags = flags
    }

    public fun GetFontCacheLimit(): Long = fontCacheLimit
    public fun SetFontCacheLimit(bytes: Long): Long {
        val prev = fontCacheLimit
        fontCacheLimit = bytes
        return prev
    }
    public fun GetFontCacheUsed(): Long = 0L
    public fun GetFontCacheCountUsed(): Int = 0
    public fun GetFontCacheCountLimit(): Int = fontCacheCountLimit
    public fun SetFontCacheCountLimit(count: Int): Int {
        val prev = fontCacheCountLimit
        fontCacheCountLimit = count
        return prev
    }
    public fun GetTypefaceCacheCountLimit(): Int = typefaceCacheCountLimit
    public fun SetTypefaceCacheCountLimit(count: Int): Int {
        val prev = typefaceCacheCountLimit
        typefaceCacheCountLimit = count
        return prev
    }
    public fun PurgeFontCache() { /* no-op */ }
    public fun PurgePinnedFontCache() { /* no-op */ }

    public fun GetResourceCacheTotalBytesUsed(): Long = 0L
    public fun GetResourceCacheTotalByteLimit(): Long = resourceCacheTotalByteLimit
    public fun SetResourceCacheTotalByteLimit(newLimit: Long): Long {
        val prev = resourceCacheTotalByteLimit
        resourceCacheTotalByteLimit = newLimit
        return prev
    }
    public fun PurgeResourceCache() { /* no-op */ }

    public fun GetResourceCacheSingleAllocationByteLimit(): Long =
        resourceCacheSingleAllocationByteLimit
    public fun SetResourceCacheSingleAllocationByteLimit(newLimit: Long): Long {
        val prev = resourceCacheSingleAllocationByteLimit
        resourceCacheSingleAllocationByteLimit = newLimit
        return prev
    }

    /** Minimal sink interface used by [DumpMemoryStatistics]. R1 stub. */
    public interface SkTraceMemoryDump {
        public fun dumpNumericValue(dumpName: String, valueName: String, units: String, value: Long)
    }

    /** Dump cache memory stats to [dump]. R1 no-op — no caches are tracked. */
    @Suppress("UNUSED_PARAMETER")
    public fun DumpMemoryStatistics(dump: SkTraceMemoryDump) { /* no-op */ }

    /** Free as much globally cached memory as possible. R1 no-op. */
    public fun PurgeAllCaches() { /* no-op */ }
}
