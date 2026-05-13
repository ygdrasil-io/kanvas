package org.skia.foundation

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mirrors Skia's
 * [`SkPixelRef`](https://github.com/google/skia/blob/main/include/core/SkPixelRef.h)
 * — the owning container for a pixel buffer used by [SkBitmap].
 *
 * **Lifecycle versus Skia** : upstream `SkPixelRef` derives from
 * `SkRefCnt` because C++ needs explicit reference counting. The Kotlin
 * port relies on the JVM's tracing GC — no `ref` / `unref` machinery is
 * exposed.
 *
 * **Generation id** : every time the pixel bytes are mutated, the
 * client must call [notifyPixelsChanged] so the [generationID] bumps
 * to a fresh value. Downstream caches keyed by `generationID` use this
 * to invalidate themselves.
 *
 * **Immutability** : once [setImmutable] is called, the ref's pixels
 * are pinned for the rest of the ref's lifetime — Skia clients use this
 * to flag "this image is safe to share without further mutation".
 */
public class SkPixelRef(
    private val width: Int,
    private val height: Int,
    private val pixels: ByteBuffer,
    private val rowBytes: Int,
) {
    /**
     * Process-wide monotonically-increasing generation id source. Skia's
     * upstream uses a 32-bit tagged atomic — we use the same width
     * (Kotlin [Int]) but expose the raw value (no tag bit) because the
     * kanvas-skia surface doesn't model the "uniquely-owned" fast path
     * that the tag bit gates.
     */
    private var genID: Int = NextGenID()

    @Volatile
    private var immutable: Boolean = false

    public fun width(): Int = width
    public fun height(): Int = height
    public fun pixels(): ByteBuffer = pixels
    public fun rowBytes(): Int = rowBytes

    /** Mirrors `SkPixelRef::getGenerationID()`. */
    public fun generationID(): Int = genID

    /**
     * Mirrors `SkPixelRef::notifyPixelsChanged()`. Bumps the generation
     * id so caches keyed by the old id can detect the mutation. Calling
     * this on an immutable ref is a no-op (matches Skia's behaviour
     * where the ref can't acquire a new gen id once frozen — upstream
     * `notifyPixelsChanged` is implicitly inert because no caller should
     * be mutating an immutable ref's pixels).
     */
    public fun notifyPixelsChanged() {
        if (!immutable) genID = NextGenID()
    }

    /** Mirrors `SkPixelRef::isImmutable()`. */
    public fun isImmutable(): Boolean = immutable

    /**
     * Mirrors `SkPixelRef::setImmutable()`. Idempotent — once set, the
     * flag never clears.
     */
    public fun setImmutable() {
        immutable = true
    }

    public companion object {
        private val GEN_ID_SOURCE: AtomicInteger = AtomicInteger(0)

        /**
         * Mirrors Skia's `SkNextID::ImageID()` for pixel-ref gen ids.
         * Returns a strictly positive id — 0 is reserved (Skia uses 0
         * to mean "no generation id assigned yet").
         */
        private fun NextGenID(): Int {
            // CAS-loop to skip 0 if we ever wrap past Int.MAX_VALUE.
            while (true) {
                val next = GEN_ID_SOURCE.incrementAndGet()
                if (next != 0) return next
            }
        }
    }
}
