package org.skia.foundation

/**
 * Iso-aligned port of Skia's `SkGraphics`
 * ([include/core/SkGraphics.h](https://github.com/google/skia/blob/main/include/core/SkGraphics.h)).
 *
 * Process-wide knobs for Skia's global caches (font, resource,
 * typeface) and runtime hooks (image-generator factory, OpenType
 * SVG decoder factory).
 *
 * **R-suivi.6 status — minimal resource cache.** The resource cache
 * is now a small in-memory key→bytes map with an enforced byte
 * limit (default 128 MiB). [PurgeResourceCache] clears it,
 * [SetResourceCacheTotalByteLimit] resizes it (and evicts if
 * over-limit), [GetResourceCacheTotalBytesUsed] reports actual
 * usage. Eviction is FIFO insertion order. Existing per-class
 * caches (e.g. font glyph caches in [SkFont]) are unchanged — they
 * remain their own data path; this object cache is the foundation
 * for future migration. The font cache fields remain stubs.
 *
 *  * [Init] / [Term] do nothing.
 *  * Font cache getters return `0` and the limit setters round-trip
 *    the value (no enforced eviction).
 *  * [DumpMemoryStatistics] now reports the resource-cache bytes
 *    used / limit through the dumper.
 */
public object SkGraphics {

    private var fontCacheLimit: Long = Long.MAX_VALUE
    private var fontCacheCountLimit: Int = Int.MAX_VALUE
    private var typefaceCacheCountLimit: Int = Int.MAX_VALUE
    private var resourceCacheSingleAllocationByteLimit: Long = 0L
    private var flags: Int = 0

    // --- Resource cache (R-suivi.6) ---------------------------------------
    // FIFO-evicted in-memory key→bytes map. Keys are arbitrary strings ;
    // callers compose them however they like (e.g. SHA-1 of a scaled
    // bitmap descriptor). The cache is guarded by its own monitor for
    // thread safety, since SkGraphics is process-wide.
    private val resourceCacheLock = Any()
    private val resourceCache: LinkedHashMap<String, ByteArray> = LinkedHashMap()
    private var resourceCacheBytes: Long = 0L
    private var resourceCacheByteLimit: Long = 128L * 1024L * 1024L // 128 MiB

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

    /** Bytes currently held by the resource cache. */
    public fun GetResourceCacheTotalBytesUsed(): Long =
        synchronized(resourceCacheLock) { resourceCacheBytes }

    /** Current resource-cache byte limit (default 128 MiB). */
    public fun GetResourceCacheTotalByteLimit(): Long =
        synchronized(resourceCacheLock) { resourceCacheByteLimit }

    /**
     * Set the resource-cache byte limit. Returns the previous value.
     * If [newLimit] is lower than the current usage, oldest entries
     * are evicted (FIFO insertion order) until the cache fits.
     */
    public fun SetResourceCacheTotalByteLimit(newLimit: Long): Long =
        synchronized(resourceCacheLock) {
            val prev = resourceCacheByteLimit
            resourceCacheByteLimit = newLimit.coerceAtLeast(0L)
            purgeOverLimitLocked()
            prev
        }

    /** Drop every entry in the resource cache. */
    public fun PurgeResourceCache() {
        synchronized(resourceCacheLock) {
            resourceCache.clear()
            resourceCacheBytes = 0L
        }
    }

    public fun GetResourceCacheSingleAllocationByteLimit(): Long =
        resourceCacheSingleAllocationByteLimit
    public fun SetResourceCacheSingleAllocationByteLimit(newLimit: Long): Long {
        val prev = resourceCacheSingleAllocationByteLimit
        resourceCacheSingleAllocationByteLimit = newLimit
        return prev
    }

    /** Minimal sink interface used by [DumpMemoryStatistics]. */
    public interface SkTraceMemoryDump {
        public fun dumpNumericValue(dumpName: String, valueName: String, units: String, value: Long)
    }

    /** Dump cache memory stats to [dump]. */
    public fun DumpMemoryStatistics(dump: SkTraceMemoryDump) {
        synchronized(resourceCacheLock) {
            dump.dumpNumericValue("skia/sk_resource_cache", "size", "bytes", resourceCacheBytes)
            dump.dumpNumericValue("skia/sk_resource_cache", "budget", "bytes", resourceCacheByteLimit)
            dump.dumpNumericValue("skia/sk_resource_cache", "entries", "objects", resourceCache.size.toLong())
        }
    }

    /** Free as much globally cached memory as possible. R-suivi.6 — purges the resource cache. */
    public fun PurgeAllCaches() {
        PurgeResourceCache()
    }

    // --- Internal resource-cache API (used by future migrations) ---------

    /**
     * Insert [value] under [key]. If [key] is already present its value
     * is replaced. If the insertion (or replacement) would exceed the
     * byte limit, oldest entries are evicted FIFO until it fits, with
     * one exception : if a single value is larger than the byte limit
     * it is rejected and the cache is left untouched.
     */
    internal fun resourceCachePut(key: String, value: ByteArray) {
        synchronized(resourceCacheLock) {
            // Single-allocation guard.
            if (resourceCacheByteLimit > 0L && value.size.toLong() > resourceCacheByteLimit) {
                return
            }
            val prev = resourceCache.remove(key)
            if (prev != null) resourceCacheBytes -= prev.size.toLong()
            resourceCache[key] = value
            resourceCacheBytes += value.size.toLong()
            purgeOverLimitLocked()
        }
    }

    /** Fetch the value under [key] or `null` if not cached. */
    internal fun resourceCacheGet(key: String): ByteArray? =
        synchronized(resourceCacheLock) { resourceCache[key] }

    /** Number of entries currently held — exposed for testing. */
    internal fun resourceCacheEntryCount(): Int =
        synchronized(resourceCacheLock) { resourceCache.size }

    private fun purgeOverLimitLocked() {
        if (resourceCacheBytes <= resourceCacheByteLimit) return
        val it = resourceCache.entries.iterator()
        while (it.hasNext() && resourceCacheBytes > resourceCacheByteLimit) {
            val e = it.next()
            resourceCacheBytes -= e.value.size.toLong()
            it.remove()
        }
        // Defensive clamp — should never trigger with correct accounting.
        if (resourceCacheBytes < 0L) resourceCacheBytes = 0L
    }
}
