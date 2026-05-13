package org.skia.foundation.stream

/**
 * Abstraction for a source of bytes, mirroring Skia's
 * [`SkStream`](https://github.com/google/skia/blob/main/include/core/SkStream.h).
 *
 * Subclasses can be backed by memory, a file, or any other byte
 * source. Concrete capabilities are layered onto the type
 * hierarchy : [SkStreamRewindable] adds a guaranteed [rewind],
 * [SkStreamSeekable] adds random access via [seek] / [move], and
 * [SkStreamAsset] additionally guarantees [getLength].
 *
 * The Kotlin port deliberately omits Skia's `peek` and `duplicate`
 * methods — they are not used by the kanvas-skia API surface and
 * would add implementation complexity for no current consumer.
 */
public abstract class SkStream {

    /**
     * Read up to [size] bytes into [buffer], starting at index 0.
     * Returns the number of bytes actually read (may be less than
     * [size] near end-of-stream).
     */
    public abstract fun read(buffer: ByteArray, size: Int): Int

    /**
     * Returns true when all bytes have been read.
     *
     * As SkStream represents synchronous I/O, this may return
     * false even when no more bytes are immediately available
     * (e.g. for length-unknown streams).
     */
    public abstract fun isAtEnd(): Boolean

    /** Returns true if this stream can report its total length. */
    public abstract fun hasLength(): Boolean

    /** Returns the total length of the stream, or 0 if unknown. */
    public abstract fun getLength(): Long

    /** Returns true if this stream can report its current position. */
    public abstract fun hasPosition(): Boolean

    /** Returns the current position in the stream, or 0 if unknown. */
    public abstract fun getPosition(): Long

    /** Attempts to rewind to the start of the stream. */
    public abstract fun rewind(): Boolean

    /**
     * Skip up to [size] bytes. Default implementation reads into a
     * scratch buffer and discards. Returns the number of bytes
     * actually skipped.
     */
    public open fun skip(size: Long): Long {
        if (size <= 0L) return 0L
        val scratch = ByteArray(SKIP_SCRATCH_SIZE)
        var remaining = size
        var skipped = 0L
        while (remaining > 0L) {
            val chunk = if (remaining > SKIP_SCRATCH_SIZE) SKIP_SCRATCH_SIZE else remaining.toInt()
            val n = read(scratch, chunk)
            if (n <= 0) break
            skipped += n
            remaining -= n
        }
        return skipped
    }

    /**
     * Returns a fresh stream over the same content, positioned the
     * same as this one. Throws on subclasses that cannot fork
     * (e.g. forward-only streams).
     */
    public open fun fork(): SkStream =
        throw UnsupportedOperationException("fork() not supported by ${this::class.simpleName}")

    private companion object {
        private const val SKIP_SCRATCH_SIZE = 4096
    }
}

/**
 * A stream that is required to support [rewind].
 * Mirrors Skia's `SkStreamRewindable`.
 */
public abstract class SkStreamRewindable : SkStream() {
    abstract override fun rewind(): Boolean
}

/**
 * A rewindable stream that additionally supports random-access
 * positioning via [seek] / [move].
 * Mirrors Skia's `SkStreamSeekable`.
 */
public abstract class SkStreamSeekable : SkStreamRewindable() {

    /** Seek to an absolute position in the stream. */
    public abstract fun seek(position: Long): Boolean

    /** Move by [offset] bytes from the current position. */
    public abstract fun move(offset: Long): Boolean

    override fun hasPosition(): Boolean = true
}

/**
 * A seekable stream that additionally reports its total length.
 * Mirrors Skia's `SkStreamAsset`.
 */
public abstract class SkStreamAsset : SkStreamSeekable() {

    override fun hasLength(): Boolean = true

    abstract override fun getLength(): Long

    /**
     * Returns a direct view into the underlying memory if the
     * stream is memory-backed, or null otherwise. The returned
     * array MUST NOT be mutated — it is shared with the stream.
     */
    public open fun getMemoryBase(): ByteArray? = null
}
