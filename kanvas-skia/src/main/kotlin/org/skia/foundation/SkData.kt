package org.skia.foundation

/**
 * Immutable byte container, mirroring Skia's
 * [`SkData`](https://github.com/google/skia/blob/main/include/core/SkData.h).
 * Carries the bytes for runtime-effect uniform blocks, image
 * sources, and any future API that wants opaque blob storage.
 *
 * **Immutability** : [bytes] is a defensive copy of every input
 * passed through a factory. Once constructed, [SkData] cannot be
 * mutated — clients can read freely without worrying about
 * upstream mutation. To produce derived data, build a new
 * [SkData] via one of the factories.
 *
 * **No reference counting** : Skia ships [SkData] as
 * `sk_sp<SkData>` because C++ has no GC. The Kotlin port relies on
 * the JVM's tracing GC instead — the API surface stays minimal
 * (no `ref` / `unref` / `unique`).
 */
public class SkData private constructor(
    /** Internal byte storage. Never mutated post-construction. */
    private val bytes: ByteArray,
) {
    /** Number of bytes carried by this [SkData]. */
    public val size: Int get() = bytes.size

    /** Read a single byte. Mirrors `SkData::data()[i]`. */
    public fun byteAt(index: Int): Byte = bytes[index]

    /**
     * Return a defensive copy of the underlying byte array.
     * Mirrors Skia's `data()` accessor with explicit copy
     * semantics — callers cannot accidentally mutate the
     * SkData's interior.
     */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    /**
     * Internal accessor for hot paths that need direct read-only
     * access to the underlying buffer (e.g. the runtime-effect
     * binding's per-pixel `shade()` call needs to slice the
     * uniforms ByteBuffer without paying the [toByteArray] copy
     * cost). Callers must NOT mutate the returned array.
     */
    internal fun bytesUnsafe(): ByteArray = bytes

    override fun equals(other: Any?): Boolean =
        this === other || (other is SkData && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "SkData(size=$size)"

    public companion object {
        /** Empty (zero-byte) singleton. Mirrors `SkData::MakeEmpty()`. */
        public val EMPTY: SkData = SkData(ByteArray(0))

        /** Mirrors `SkData::MakeWithCopy(src, size)`. Defensive-copies. */
        public fun MakeWithCopy(src: ByteArray): SkData =
            if (src.isEmpty()) EMPTY else SkData(src.copyOf())

        /**
         * Mirrors `SkData::MakeUninitialized(size)`. Allocates a
         * zero-filled byte buffer of [size] bytes — used by the
         * runtime-effect Builder to set up a uniform block before
         * filling it via per-name accessors.
         */
        public fun MakeUninitialized(size: Int): SkData {
            require(size >= 0) { "SkData size must be non-negative ; got $size" }
            return if (size == 0) EMPTY else SkData(ByteArray(size))
        }
    }
}
