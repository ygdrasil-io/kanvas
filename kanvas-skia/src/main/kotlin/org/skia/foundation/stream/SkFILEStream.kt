package org.skia.foundation.stream

import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/**
 * File-backed [SkStreamAsset], implemented on top of
 * [java.io.RandomAccessFile] for `seek`/`move` support.
 * Mirrors Skia's
 * [`SkFILEStream`](https://github.com/google/skia/blob/main/include/core/SkStream.h)
 * — except Skia's variant carries a `(start, end, current)` slice
 * over the file ; the kanvas-skia port always covers the full
 * file (no sub-range construction).
 */
public class SkFILEStream(path: String) : SkStreamAsset() {

    private val file: RandomAccessFile = RandomAccessFile(path, "r")
    private val length: Long = file.length()
    private var closed: Boolean = false

    /** Returns true while the underlying file is still open. */
    public fun isValid(): Boolean = !closed

    /** Closes the underlying file. Subsequent reads return 0. */
    public fun close() {
        if (closed) return
        closed = true
        try {
            file.close()
        } catch (_: IOException) {
            // best effort
        }
    }

    override fun read(buffer: ByteArray, size: Int): Int {
        if (closed || size <= 0) return 0
        val n = file.read(buffer, 0, size)
        return if (n < 0) 0 else n
    }

    override fun isAtEnd(): Boolean =
        closed || file.filePointer >= length

    override fun hasLength(): Boolean = true

    override fun getLength(): Long = length

    override fun hasPosition(): Boolean = true

    override fun getPosition(): Long = if (closed) 0L else file.filePointer

    override fun rewind(): Boolean {
        if (closed) return false
        file.seek(0L)
        return true
    }

    override fun seek(position: Long): Boolean {
        if (closed) return false
        val clamped = position.coerceIn(0L, length)
        file.seek(clamped)
        return true
    }

    override fun move(offset: Long): Boolean {
        if (closed) return false
        val target = max(0L, min(length, file.filePointer + offset))
        file.seek(target)
        return true
    }
}
