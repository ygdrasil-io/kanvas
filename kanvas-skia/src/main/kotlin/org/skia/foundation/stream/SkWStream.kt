package org.skia.foundation.stream

/**
 * Abstraction for a destination of bytes, mirroring Skia's
 * [`SkWStream`](https://github.com/google/skia/blob/main/include/core/SkStream.h).
 *
 * Concrete subclasses include [SkDynamicMemoryWStream] (writes
 * into a growable in-memory buffer) and [SkFILEWStream] (writes
 * into a file via [java.io.FileOutputStream]).
 */
public abstract class SkWStream {

    /**
     * Write [size] bytes from [buffer] starting at index 0.
     * Returns true on success.
     */
    public abstract fun write(buffer: ByteArray, size: Int): Boolean

    /** Returns the total number of bytes written so far. */
    public abstract fun bytesWritten(): Long

    /** Flushes any buffered bytes. Default is a no-op. */
    public open fun flush() { /* no-op */ }

    /**
     * Returns the logical size of the destination. For most write
     * streams this is identical to [bytesWritten] ; file streams
     * may report the underlying file size instead.
     */
    public open fun fSize(): Long = bytesWritten()

    /** UTF-8 encodes [text] and writes it as raw bytes. */
    public open fun writeText(text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return write(bytes, bytes.size)
    }

    /** Writes a single `\n` byte. */
    public open fun newline(): Boolean = writeText("\n")

    /** Writes one byte. */
    public open fun write8(v: Byte): Boolean =
        write(byteArrayOf(v), 1)

    /** Writes a little-endian 16-bit value (2 bytes). */
    public open fun write16(v: Short): Boolean {
        val buf = ByteArray(2)
        val i = v.toInt() and 0xFFFF
        buf[0] = (i and 0xFF).toByte()
        buf[1] = ((i ushr 8) and 0xFF).toByte()
        return write(buf, 2)
    }

    /** Writes a little-endian 32-bit value (4 bytes). */
    public open fun write32(v: Int): Boolean {
        val buf = ByteArray(4)
        buf[0] = (v and 0xFF).toByte()
        buf[1] = ((v ushr 8) and 0xFF).toByte()
        buf[2] = ((v ushr 16) and 0xFF).toByte()
        buf[3] = ((v ushr 24) and 0xFF).toByte()
        return write(buf, 4)
    }

    /**
     * Writes [d] formatted as a decimal text representation with
     * at most [prec] fractional digits. Unlike Skia's
     * `writeBigDecAsText` (which writes integers with optional
     * zero-padding), the Kotlin port targets floating-point
     * formatting since SkScalar serialization is the primary use.
     */
    public open fun writeBigDecAsText(d: Double, prec: Int = 6): Boolean {
        val text = "%.${prec}f".format(d)
        return writeText(text)
    }

    /**
     * Variable-length packed encoding of an unsigned integer.
     * Matches upstream Skia's wire format exactly :
     *  - values 0..0xFD encode as a single byte
     *  - values 0xFE..0xFFFF encode as `0xFE` + little-endian u16
     *  - all other values encode as `0xFF` + little-endian u32
     *
     * @throws IllegalArgumentException if [n] is negative or
     *   exceeds `0xFFFFFFFF`.
     */
    public open fun writePackedUInt(n: Long): Boolean {
        require(n in 0L..0xFFFFFFFFL) { "writePackedUInt out of range : $n" }
        return when {
            n <= 0xFDL -> {
                write(byteArrayOf(n.toByte()), 1)
            }
            n <= 0xFFFFL -> {
                val buf = ByteArray(3)
                buf[0] = SENTINEL_U16
                buf[1] = (n and 0xFF).toByte()
                buf[2] = ((n ushr 8) and 0xFF).toByte()
                write(buf, 3)
            }
            else -> {
                val buf = ByteArray(5)
                buf[0] = SENTINEL_U32
                buf[1] = (n and 0xFF).toByte()
                buf[2] = ((n ushr 8) and 0xFF).toByte()
                buf[3] = ((n ushr 16) and 0xFF).toByte()
                buf[4] = ((n ushr 24) and 0xFF).toByte()
                write(buf, 5)
            }
        }
    }

    /**
     * Reads [length] bytes from [input] and writes them into this
     * stream, in 1024-byte chunks (matching upstream).
     */
    public open fun writeStream(input: SkStream, length: Long): Boolean {
        if (length <= 0L) return true
        val scratch = ByteArray(SCRATCH_SIZE)
        var remaining = length
        while (remaining > 0L) {
            val chunk = if (remaining > SCRATCH_SIZE) SCRATCH_SIZE else remaining.toInt()
            val n = input.read(scratch, chunk)
            if (n <= 0) return false
            if (!write(scratch, n)) return false
            remaining -= n
        }
        return true
    }

    public companion object {
        /** Sentinel byte introducing a 16-bit packed-uint payload. */
        public const val SENTINEL_U16: Byte = 0xFE.toByte()

        /** Sentinel byte introducing a 32-bit packed-uint payload. */
        public const val SENTINEL_U32: Byte = 0xFF.toByte()

        private const val SCRATCH_SIZE = 1024

        /**
         * Returns the number of bytes [writePackedUInt] would emit
         * for [value]. Mirrors `SkWStream::SizeOfPackedUInt`.
         */
        public fun sizeOfPackedUInt(value: Long): Int = when {
            value <= 0xFDL -> 1
            value <= 0xFFFFL -> 3
            else -> 5
        }
    }
}
