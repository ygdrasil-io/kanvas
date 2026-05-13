package org.skia.foundation.stream

import org.skia.foundation.SkData
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Growable in-memory destination, mirroring Skia's
 * [`SkDynamicMemoryWStream`](https://github.com/google/skia/blob/main/include/core/SkStream.h).
 *
 * Backed by a [java.io.ByteArrayOutputStream], which handles the
 * doubling-growth strategy internally. Bytes can later be drained
 * via [detachAsData], [copyTo], or snapshotted via [toByteArray].
 */
public class SkDynamicMemoryWStream : SkWStream() {

    private var buffer = ByteArrayOutputStream()

    override fun write(buffer: ByteArray, size: Int): Boolean {
        if (size <= 0) return true
        this.buffer.write(buffer, 0, size)
        return true
    }

    override fun bytesWritten(): Long = buffer.size().toLong()

    override fun flush() { /* no-op */ }

    override fun fSize(): Long = bytesWritten()

    /**
     * Snapshot the current bytes into [dst] starting at index 0.
     * Equivalent to `read(0, dst, bytesWritten())` but skips the
     * range check on offset.
     */
    public fun copyTo(dst: ByteArray) {
        val n = min(dst.size, buffer.size())
        val src = buffer.toByteArray()
        System.arraycopy(src, 0, dst, 0, n)
    }

    /**
     * Read [size] bytes starting at [offset] into [buffer].
     * Returns the number of bytes copied. Mirrors Skia's
     * `SkDynamicMemoryWStream::read(buf, offset, size)`.
     */
    public fun read(offset: Long, buffer: ByteArray, size: Int): Int {
        if (size <= 0 || offset < 0L) return 0
        val totalSize = this.buffer.size()
        if (offset >= totalSize) return 0
        val available = totalSize - offset.toInt()
        val n = min(size, available)
        val src = this.buffer.toByteArray()
        System.arraycopy(src, offset.toInt(), buffer, 0, n)
        return n
    }

    /**
     * Freeze the current contents as an [SkData] and reset the
     * stream to empty. Mirrors Skia's `detachAsData()`.
     */
    public fun detachAsData(): SkData {
        val bytes = buffer.toByteArray()
        reset()
        return SkData.MakeWithCopy(bytes)
    }

    /**
     * Take a snapshot of the current contents as a [ByteArray].
     * Does NOT reset the stream — for that, use [detachAsData].
     */
    public fun toByteArray(): ByteArray = buffer.toByteArray()

    /** Drop all written bytes and return to the empty state. */
    public fun reset() {
        buffer = ByteArrayOutputStream()
    }
}
