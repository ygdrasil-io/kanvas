package org.skia.utils

import org.skia.core.SkCanvas

/**
 * Iso-aligned port of Skia's `SkCanvasStateUtils`
 * ([include/utils/SkCanvasStateUtils.h](https://github.com/google/skia/blob/main/include/utils/SkCanvasStateUtils.h)).
 *
 * Upstream this class moves an [SkCanvas] across a library boundary
 * (e.g. host process → in-process plug-in) by serialising the CTM,
 * clip, and pixel buffer into an opaque `SkCanvasState*`. Modern
 * Skia treats this as a niche API — `MakeFromCanvasState` is only
 * implemented when `SK_SUPPORT_LEGACY_DRAWFILTER` is on.
 *
 * **R1 status — stub only.** The public surface is exposed so GM
 * ports and downstream callers compile, but every method is a
 * no-op:
 *  * [CaptureCanvasState] returns `null`
 *  * [MakeFromCanvasState] returns `null`
 *  * [ReleaseCanvasState] is a no-op
 *
 * If a future port needs the actual round-trip (unlikely — none of
 * the GMs exercise it) this can be reimplemented on top of
 * [org.skia.core.SkBitmapDevice].
 */
public object SkCanvasStateUtils {

    /** Opaque token representing a captured canvas state. R1: unused stub. */
    public class SkCanvasState internal constructor()

    /**
     * Capture the current state of [canvas] into an opaque token.
     *
     * R1 stub — always returns `null`. Matches upstream's behaviour
     * for unsupported device types.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun CaptureCanvasState(canvas: SkCanvas): SkCanvasState? = null

    /**
     * Reconstruct an [SkCanvas] from a state captured by
     * [CaptureCanvasState]. R1 stub — always returns `null`.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun MakeFromCanvasState(state: SkCanvasState?): SkCanvas? = null

    /**
     * Release the memory associated with a captured state.
     * R1 stub — no-op (no native memory is allocated).
     */
    @Suppress("UNUSED_PARAMETER")
    public fun ReleaseCanvasState(state: SkCanvasState?) {
        // No-op. The R1 stub never allocates anything.
    }
}
