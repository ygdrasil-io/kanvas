package org.skia.foundation.stream

import kotlin.math.max
import kotlin.math.min

/**
 * Random-access stream over an in-memory byte array.
 * Mirrors Skia's
 * [`SkMemoryStream`](https://github.com/google/skia/blob/main/include/core/SkStream.h).
 *
 * The underlying bytes are not copied on construction — the
 * caller is expected to treat the array as immutable for the
 * lifetime of the stream. Use `data.copyOf()` upstream if a
 * defensive copy is desired.
 */
public class SkMemoryStream(
    private val data: ByteArray,
) : SkStreamAsset() {

    private var offset: Int = 0

    override fun read(buffer: ByteArray, size: Int): Int {
        if (size <= 0) return 0
        val available = data.size - offset
        if (available <= 0) return 0
        val n = min(size, available)
        System.arraycopy(data, offset, buffer, 0, n)
        offset += n
        return n
    }

    override fun isAtEnd(): Boolean = offset >= data.size

    override fun hasLength(): Boolean = true

    override fun getLength(): Long = data.size.toLong()

    override fun hasPosition(): Boolean = true

    override fun getPosition(): Long = offset.toLong()

    override fun rewind(): Boolean {
        offset = 0
        return true
    }

    override fun seek(position: Long): Boolean {
        offset = position.coerceIn(0L, data.size.toLong()).toInt()
        return true
    }

    override fun move(offset: Long): Boolean {
        val target = this.offset.toLong() + offset
        this.offset = max(0L, min(data.size.toLong(), target)).toInt()
        return true
    }

    override fun skip(size: Long): Long {
        if (size <= 0L) return 0L
        val available = (data.size - offset).toLong()
        val n = min(size, available).toInt()
        offset += n
        return n.toLong()
    }

    override fun getMemoryBase(): ByteArray = data

    /**
     * Peek up to [size] bytes from the current offset **without
     * advancing the cursor**. Pure slice — no seek-back, no
     * intermediate buffer. Mirrors Skia's
     * `SkMemoryStream::peek` fast path.
     */
    override fun peek(buffer: ByteArray, size: Int): Int {
        if (size <= 0) return 0
        val available = data.size - offset
        if (available <= 0) return 0
        val n = min(size, available)
        System.arraycopy(data, offset, buffer, 0, n)
        return n
    }

    override fun fork(): SkMemoryStream {
        val copy = SkMemoryStream(data)
        copy.offset = offset
        return copy
    }

    /**
     * Returns a fresh [SkMemoryStream] over the same backing buffer,
     * **positioned at 0**. Differs from [fork] only in the starting
     * position : matches upstream's
     * `SkMemoryStream::duplicate` which rewinds the copy.
     */
    override fun duplicate(): SkMemoryStream {
        return SkMemoryStream(data)
    }
}
