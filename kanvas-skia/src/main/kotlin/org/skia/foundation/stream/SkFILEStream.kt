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

    private val path: String = path
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

    /**
     * Peek without advancing the cursor : capture position,
     * [read], seek back. Mirrors Skia's
     * `SkFILEStream::peek` default seekable path.
     */
    override fun peek(buffer: ByteArray, size: Int): Int {
        if (closed || size <= 0) return 0
        val pos = file.filePointer
        val n = read(buffer, size)
        // Restore cursor — best-effort, swallow IO errors and treat
        // as a failed peek (returns 0) to match upstream.
        try {
            file.seek(pos)
        } catch (_: IOException) {
            return 0
        }
        return n
    }

    /**
     * Re-open the same path with cursor at 0. Mirrors upstream's
     * `SkFILEStream::fork` (which dup's the FILE handle and seeks
     * to the current cursor) — kanvas-skia simplifies by re-opening
     * and *rewinding* the duplicate (matches `duplicate()` upstream;
     * see note below).
     */
    override fun fork(): SkFILEStream {
        // Re-open at position 0. Upstream Skia carries the seek
        // position of the original; here we trade that for the
        // simpler "re-open from path" model (and a clean cursor),
        // which is sufficient for the kanvas-skia API surface
        // (no caller depends on fork() preserving the cursor).
        return SkFILEStream(path)
    }

    /**
     * Re-open the same path with cursor at 0. Equivalent to [fork]
     * for [SkFILEStream] (both yield a fresh stream rewound to the
     * file start). Mirrors `SkFILEStream::duplicate`.
     */
    override fun duplicate(): SkFILEStream = SkFILEStream(path)
}
