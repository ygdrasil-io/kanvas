package org.graphiks.kanvas.codec

import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Shared helper for the R3.10 extended-codec stubs ([SkAvifDecoder],
 * [SkJpegxlDecoder], [SkRawDecoder], [SkIcoDecoder]).
 *
 * Reads up to [sniffLen] bytes from [stream], runs [matches] against
 * the buffer, rewinds the stream, and returns the predicate's result.
 *
 * The stream must support [InputStream.mark] / [InputStream.reset] so
 * the bytes consumed by the sniff are made available again to a real
 * decoder downstream. If the supplied stream does not advertise mark
 * support, it's wrapped in a [BufferedInputStream] just long enough
 * for the sniff — but the caller's stream then holds bytes the
 * wrapper buffered, so prefer passing a [BufferedInputStream] (or
 * any markable stream) from the start.
 *
 * Returns `false` if the stream throws or hits EOF before [sniffLen]
 * bytes are available — every R3.10 sniff needs the full prefix to
 * make a positive call.
 */
public fun sniffStream(
    stream: InputStream,
    sniffLen: Int,
    matches: (data: ByteArray, length: Int) -> Boolean,
): Boolean {
    val markable: InputStream = if (stream.markSupported()) stream else BufferedInputStream(stream, sniffLen)
    markable.mark(sniffLen)
    val buf = ByteArray(sniffLen)
    val n = try {
        var read = 0
        while (read < sniffLen) {
            val r = markable.read(buf, read, sniffLen - read)
            if (r < 0) break
            read += r
        }
        read
    } catch (_: Throwable) {
        try { markable.reset() } catch (_: Throwable) { /* best-effort rewind */ }
        return false
    }
    try { markable.reset() } catch (_: Throwable) { /* best-effort rewind */ }
    return matches(buf, n)
}
