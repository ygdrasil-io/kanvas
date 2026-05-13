package org.skia.foundation

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mirrors Skia's
 * [`SkImageGenerator`](https://github.com/google/skia/blob/main/include/core/SkImageGenerator.h)
 * — the abstract base for a deferred image producer.
 *
 * A generator describes a virtual image via its [SkImageInfo] and, on
 * demand, decodes its pixels into a caller-provided byte buffer
 * ([getPixels]). The pixel-production strategy is the subclass's
 * responsibility (codec, procedural, copy from another image) — the
 * base class only provides the public surface and a unique id.
 *
 * **Subclassing** : override [onGetPixels] to fill the destination
 * buffer ; the base implementation returns `false`.
 */
public abstract class SkImageGenerator protected constructor(
    private val info: SkImageInfo,
) {
    private val id: Int = NextUniqueID()

    /** Mirrors `SkImageGenerator::getInfo()`. */
    public fun getInfo(): SkImageInfo = info

    /** Mirrors `SkImageGenerator::uniqueID()`. */
    public fun uniqueID(): Int = id

    /**
     * Mirrors `SkImageGenerator::isValid(SkRecorder*)` with the recorder
     * argument elided — kanvas-skia doesn't model GPU recording, so the
     * single-arg form covers every call site. Subclasses can override
     * this when they have a meaningful "is this generator usable?"
     * notion.
     */
    public open fun isValid(): Boolean = true

    /**
     * Mirrors `SkImageGenerator::getPixels(const SkImageInfo&, void*, size_t)`.
     *
     * Delegates to [onGetPixels]. Returns `false` if the destination
     * info is empty or the row-byte stride is insufficient.
     */
    public fun getPixels(info: SkImageInfo, pixels: ByteBuffer, rowBytes: Int): Boolean {
        if (info.isEmpty()) return false
        if (rowBytes < info.minRowBytes()) return false
        return onGetPixels(info, pixels, rowBytes)
    }

    /** Mirrors `SkImageGenerator::getPixels(const SkPixmap&)`. */
    public fun getPixels(pixmap: SkPixmap): Boolean =
        getPixels(pixmap.info(), pixmap.addr(), pixmap.rowBytes())

    /**
     * Hook for subclasses. The destination buffer is positioned at 0;
     * subclasses should write `info.computeByteSize` bytes following the
     * row-major, `rowBytes`-strided layout. Return `true` on success.
     */
    protected abstract fun onGetPixels(info: SkImageInfo, pixels: ByteBuffer, rowBytes: Int): Boolean

    public companion object {
        private val UNIQUE_ID_SOURCE: AtomicInteger = AtomicInteger(0)

        /** Strictly-positive id source — 0 mirrors Skia's
         *  `kNeedNewImageUniqueID` sentinel. */
        private fun NextUniqueID(): Int {
            while (true) {
                val next = UNIQUE_ID_SOURCE.incrementAndGet()
                if (next != 0) return next
            }
        }
    }
}
