package org.skia.foundation.stream

public abstract class SkWStream {
    public abstract fun write(buffer: ByteArray, size: Int): Boolean
    public abstract fun bytesWritten(): Long
    public open fun flush() {}
    public open fun fSize(): Long = bytesWritten()
}

public abstract class SkStream

