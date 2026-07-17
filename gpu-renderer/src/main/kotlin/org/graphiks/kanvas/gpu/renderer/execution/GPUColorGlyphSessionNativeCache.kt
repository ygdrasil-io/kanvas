package org.graphiks.kanvas.gpu.renderer.execution

/** Exact identity of one reusable native ColorGlyph atlas allocation. */
internal data class GPUColorGlyphNativeAtlasCacheKey(
    val artifactId: String,
    val generation: Int,
    val contentFingerprint: String,
    val atlasBytesSha256: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val format: String,
) {
    init {
        require(artifactId.isNotBlank())
        require(generation >= 0)
        require(contentFingerprint.isNotBlank())
        require(atlasBytesSha256.length == 64 && atlasBytesSha256.all { it in '0'..'9' || it in 'a'..'f' })
        require(byteSize > 0L)
        require(width > 0 && height > 0)
        require(format.isNotBlank())
    }
}

/** Session-local evidence for cache reuse and invalidation. */
internal data class GPUColorGlyphNativeCacheCounters(
    val invariantCreations: Long = 0L,
    val atlasCreations: Long = 0L,
    val atlasUploads: Long = 0L,
    val atlasReuses: Long = 0L,
    val atlasInvalidations: Long = 0L,
    val currentAtlasBytes: Long = 0L,
    val atlasPeakResidentBytes: Long = 0L,
)

/** One borrowed view of the session-owned invariant and atlas handles. */
internal data class GPUColorGlyphSessionNativeCacheLease<I : AutoCloseable, A : AutoCloseable>(
    val invariants: I,
    val atlas: A,
)

/**
 * Generic, synchronized lifetime core for the native ColorGlyph session cache.
 *
 * A changed atlas is created before the old allocation is retired. Failed closes remain owned and
 * are retried when the session cache closes; successful handles are never closed twice.
 */
internal class GPUColorGlyphSessionNativeCache<S, I : AutoCloseable, A : AutoCloseable>(
    private val keyOf: (S) -> GPUColorGlyphNativeAtlasCacheKey,
    private val invariantFactory: () -> I,
    private val atlasFactory: (S) -> A,
) : AutoCloseable {
    private data class AtlasEntry<A : AutoCloseable>(
        val key: GPUColorGlyphNativeAtlasCacheKey,
        val value: A,
    )

    private var invariants: I? = null
    private var currentAtlas: AtlasEntry<A>? = null
    private val retiredAtlases = mutableListOf<A>()
    private var closed = false
    private var invariantCreations = 0L
    private var atlasCreations = 0L
    private var atlasReuses = 0L
    private var atlasInvalidations = 0L
    private var atlasPeakResidentBytes = 0L

    @Synchronized
    fun acquire(source: S): GPUColorGlyphSessionNativeCacheLease<I, A> {
        check(!closed) { "The ColorGlyph native session cache is closed" }
        val invariantHandles = invariants ?: invariantFactory().also {
            invariants = it
            invariantCreations += 1L
        }
        val key = keyOf(source)
        val existing = currentAtlas
        if (existing != null && existing.key == key) {
            atlasReuses += 1L
            return GPUColorGlyphSessionNativeCacheLease(invariantHandles, existing.value)
        }

        val replacementPeakBytes = if (existing == null) {
            key.byteSize
        } else {
            Math.addExact(existing.key.byteSize, key.byteSize)
        }
        val replacement = atlasFactory(source)
        require(existing == null || replacement !== existing.value) {
            "A changed ColorGlyph atlas key requires a fresh native allocation"
        }
        atlasCreations += 1L
        atlasPeakResidentBytes = maxOf(atlasPeakResidentBytes, replacementPeakBytes)
        currentAtlas = AtlasEntry(key, replacement)
        if (existing != null) {
            atlasInvalidations += 1L
            retiredAtlases += existing.value
            closeRetiredAtlases()
        }
        return GPUColorGlyphSessionNativeCacheLease(invariantHandles, replacement)
    }

    @Synchronized
    fun counters(): GPUColorGlyphNativeCacheCounters = GPUColorGlyphNativeCacheCounters(
        invariantCreations = invariantCreations,
        atlasCreations = atlasCreations,
        atlasUploads = atlasCreations,
        atlasReuses = atlasReuses,
        atlasInvalidations = atlasInvalidations,
        currentAtlasBytes = currentAtlas?.key?.byteSize ?: 0L,
        atlasPeakResidentBytes = atlasPeakResidentBytes,
    )

    @Synchronized
    override fun close() {
        if (closed && currentAtlas == null && retiredAtlases.isEmpty() && invariants == null) return
        closed = true
        currentAtlas?.let { entry ->
            retiredAtlases += entry.value
            currentAtlas = null
        }
        closeRetiredAtlases()
        val invariantHandles = invariants
        if (invariantHandles != null) {
            try {
                invariantHandles.close()
                invariants = null
            } catch (failure: Throwable) {
                throw IllegalStateException("ColorGlyph native cache retained invariant handles", failure)
            }
        }
        if (retiredAtlases.isNotEmpty()) {
            error("ColorGlyph native cache retained ${retiredAtlases.size} atlas allocation(s)")
        }
    }

    private fun closeRetiredAtlases() {
        val iterator = retiredAtlases.iterator()
        while (iterator.hasNext()) {
            val atlas = iterator.next()
            try {
                atlas.close()
                iterator.remove()
            } catch (_: Throwable) {
                // Retain the exact allocation for the next session-close retry.
            }
        }
    }
}
