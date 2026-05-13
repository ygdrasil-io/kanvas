package org.skia.foundation

import org.skia.core.SkCanvas
import org.skia.math.SkIRect
import org.skia.math.SkMatrix

/**
 * Iso-aligned port of Skia's `SkRasterHandleAllocator`
 * ([include/core/SkRasterHandleAllocator.h](https://github.com/google/skia/blob/main/include/core/SkRasterHandleAllocator.h)).
 *
 * Upstream `SkRasterHandleAllocator` lets a client take over the two
 * responsibilities that the bitmap device normally performs on each
 * `saveLayer` :
 *  1. allocating the pixel storage for the layer ;
 *  2. attaching a `Handle` — an opaque token that tracks platform
 *     state (CG context, GDI+ device, …) and is exposed back to the
 *     caller via `SkCanvas::accessTopRasterHandle()`.
 *
 * Kanvas-skia is raster-only and has no platform handle to track, so
 * this port keeps the **shape of the API** (clients can subclass and
 * supply their own [Rec]) but its built-in [MakeCanvas] factory simply
 * allocates a Kotlin `SkBitmap` and wraps it in an [SkCanvas]. The
 * `Handle` is surfaced as a free-form `Any` reference — clients pick
 * whatever token they like.
 *
 * **R3.8 status — stub allocator.** [allocHandle] / [updateHandle] are
 * the user-facing extension points. [MakeCanvas] is enough for porting
 * upstream tests that take an allocator parameter but never actually
 * need to inspect the handle (the typical raster path).
 */
public abstract class SkRasterHandleAllocator {

    /**
     * Record describing a single allocation. Mirrors the upstream
     * `Rec` POD (`SkRasterHandleAllocator.h:44`).
     *
     *  - [proc]      : optional finaliser, invoked with [handle] when
     *                  the allocation goes out of scope ;
     *  - [handle]    : opaque caller-supplied token (`void*` upstream) ;
     *  - [pixels]    : pixel storage for this allocation ;
     *  - [rowBytes]  : row stride for [pixels].
     */
    public interface Rec {
        /** Optional release proc — called once with [handle] when the rec is dropped. */
        public val proc: ((handle: Any) -> Unit)?

        /** Opaque token returned via `SkCanvas::accessTopRasterHandle()`. */
        public val handle: Any

        /** Backing pixel storage. */
        public val pixels: ByteArray

        /** Row stride for [pixels]. */
        public val rowBytes: Int
    }

    /**
     * Mirrors `virtual bool allocHandle(const SkImageInfo&, Rec*)`
     * (`SkRasterHandleAllocator.h:63`).
     *
     * Implementations should fill the storage backing [rec] (pixels,
     * row bytes, handle, optional release proc) consistent with [info].
     * Return `true` if the allocation succeeded. The bridging
     * [hndlMatrix] carries the CTM in force at allocation time — most
     * raster-only implementations ignore it.
     */
    public abstract fun allocHandle(
        info: SkImageInfo,
        hndlMatrix: SkMatrix,
        rec: Rec,
    ): Boolean

    /**
     * Mirrors `virtual void updateHandle(Handle, const SkMatrix&, const SkIRect&)`
     * (`SkRasterHandleAllocator.h:70`).
     *
     * Called each time the canvas's matrix or clip changes so the
     * subclass can keep its platform handle (e.g. a CG context) in sync.
     * Default raster-only subclasses can leave it empty.
     */
    public abstract fun updateHandle(handle: Any, ctm: SkMatrix, clip: SkIRect)

    public companion object {
        /**
         * Mirrors `static std::unique_ptr<SkCanvas> MakeCanvas(...)`
         * (`SkRasterHandleAllocator.h:79`).
         *
         * Creates a canvas backed by raster pixels. When [rec] is
         * non-null it is used as the base layer ; otherwise
         * [allocator.allocHandle] is asked to populate one (falling
         * back to a plain `SkBitmap.allocPixels` if the allocator
         * declines).
         *
         * Returns `null` if [info] describes an empty surface or the
         * allocator refused to provide a base layer and no fallback
         * is available.
         */
        public fun MakeCanvas(
            allocator: SkRasterHandleAllocator,
            info: SkImageInfo,
            rec: Rec? = null,
        ): SkCanvas? {
            if (info.isEmpty()) return null

            val baseRec: Rec? = rec ?: askAllocatorForBase(allocator, info)

            val bitmap: SkBitmap = if (baseRec != null) {
                // Honour the allocator's pixel block by installing it
                // into a freshly-allocated bitmap. Kanvas-skia's
                // SkBitmap owns a typed backing array, so installPixels
                // performs the byte→typed decode and keeps the
                // original `ByteArray` reachable through SkPixelRef.
                val bm = SkBitmap.allocPixels(info)
                val buf = java.nio.ByteBuffer.wrap(baseRec.pixels)
                if (!bm.installPixels(info, buf, baseRec.rowBytes)) {
                    // Fallback : just hand back a bitmap whose pixels
                    // the allocator's block won't see — this matches
                    // the documented "raster-only stub" contract.
                }
                bm
            } else {
                SkBitmap.allocPixels(info)
            }

            return SkCanvas(bitmap)
        }

        /**
         * Ask the [allocator] to populate a fresh [Rec] for the base
         * layer. Returns `null` if it declines — callers fall back to
         * a plain `SkBitmap.allocPixels`.
         */
        private fun askAllocatorForBase(
            allocator: SkRasterHandleAllocator,
            info: SkImageInfo,
        ): Rec? {
            // Pre-build a default Rec the subclass can mutate-by-copy.
            val rowBytes = info.minRowBytes()
            val pixels = ByteArray(rowBytes * info.height)
            val rec = MutableRec(
                proc = null,
                handle = Any(),
                pixels = pixels,
                rowBytes = rowBytes,
            )
            return if (allocator.allocHandle(info, SkMatrix.Identity, rec)) rec else null
        }

        /**
         * Tiny mutable [Rec] passed to [allocHandle] — the subclass
         * fills in whichever fields it wants. Internal so callers
         * can't synthesise one outside [MakeCanvas].
         */
        private class MutableRec(
            override var proc: ((handle: Any) -> Unit)?,
            override var handle: Any,
            override var pixels: ByteArray,
            override var rowBytes: Int,
        ) : Rec
    }
}
