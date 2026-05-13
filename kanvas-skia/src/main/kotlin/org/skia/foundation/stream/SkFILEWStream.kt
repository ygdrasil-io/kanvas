package org.skia.foundation.stream

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * File-backed [SkWStream], implemented on top of
 * [java.io.FileOutputStream] wrapped in a
 * [java.io.BufferedOutputStream]. Mirrors Skia's
 * [`SkFILEWStream`](https://github.com/google/skia/blob/main/include/core/SkStream.h).
 */
public class SkFILEWStream(path: String) : SkWStream() {

    private val file: File = File(path)
    private val output: BufferedOutputStream =
        BufferedOutputStream(FileOutputStream(file))
    private var bytes: Long = 0L
    private var closed: Boolean = false

    /** Returns true while the underlying file is still open. */
    public fun isValid(): Boolean = !closed

    override fun write(buffer: ByteArray, size: Int): Boolean {
        if (closed || size <= 0) return false
        return try {
            output.write(buffer, 0, size)
            bytes += size
            true
        } catch (_: IOException) {
            false
        }
    }

    override fun bytesWritten(): Long = bytes

    override fun flush() {
        if (closed) return
        try {
            output.flush()
        } catch (_: IOException) {
            // best effort
        }
    }

    override fun fSize(): Long = if (closed) bytes else file.length()

    /** Flushes and closes the underlying file. */
    public fun close() {
        if (closed) return
        closed = true
        try {
            output.flush()
        } catch (_: IOException) {
            // ignore
        }
        try {
            output.close()
        } catch (_: IOException) {
            // ignore
        }
    }
}
