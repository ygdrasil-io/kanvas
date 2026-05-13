package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSurfaces
import org.skia.math.SkMatrix
import org.skia.math.SkRect

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
 * **Phase R-suivi.4 — real implementation.** [SkCanvasState] is now
 * a plain Kotlin data class carrying the CTM (3×3 [SkMatrix]), the
 * device-space clip bounds, the canvas save count, and the canvas
 * dimensions. [MakeFromCanvasState] reconstitutes a fresh raster
 * canvas from that snapshot — same dimensions, same CTM, same clip.
 *
 * The cross-process IPC use case isn't supported : upstream's opaque
 * pointer survives a `dlopen()`-boundary, but our [SkCanvasState] is
 * a plain JVM object and is only useful within a single process.
 * Every kanvas-skia GM that calls into this API does so
 * intra-process, so this restriction is moot in practice.
 */
public object SkCanvasStateUtils {

    /**
     * Snapshot of an [SkCanvas]'s portable state. Captures the CTM
     * (as a 3×3 affine matrix — perspective is dropped), the device-
     * space clip bounds, the save-stack depth, and the canvas
     * dimensions. The pixel buffer itself is **not** captured ;
     * [MakeFromCanvasState] always produces a fresh zero-initialised
     * raster surface (matches Skia's `MakeFromCanvasState` contract :
     * "the new canvas has no pixels of its own").
     */
    public data class SkCanvasState(
        val matrix: SkMatrix,
        val clipBounds: SkRect,
        val saveCount: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * Capture the current state of [canvas] into a portable snapshot.
     *
     * Returns `null` when the canvas's CTM carries perspective
     * components that don't round-trip to a 3×3 [SkMatrix] — matches
     * upstream's behaviour for unsupported device types (Skia returns
     * `nullptr` when the device can't be marshalled across the API
     * boundary).
     */
    public fun CaptureCanvasState(canvas: SkCanvas): SkCanvasState? {
        val matrix = canvas.getLocalToDeviceAsMatrix() ?: return null
        return SkCanvasState(
            matrix = matrix,
            clipBounds = SkRect.Make(canvas.getDeviceClipBounds()),
            saveCount = canvas.getSaveCount(),
            width = canvas.width,
            height = canvas.height,
        )
    }

    /**
     * Reconstruct an [SkCanvas] from a state captured by
     * [CaptureCanvasState]. The new canvas is backed by a fresh
     * `kRGBA_8888 / kPremul` raster surface sized to match the
     * source canvas. The CTM and clip rectangle are reapplied so
     * subsequent draws land in the same coordinate space as the
     * original canvas (modulo perspective, which is intentionally
     * not captured).
     *
     * Returns `null` when [state] is `null` or its dimensions are
     * non-positive (matches upstream's null-on-invalid contract).
     * The caller owns the new canvas — there is no shared backing
     * with the source.
     */
    public fun MakeFromCanvasState(state: SkCanvasState?): SkCanvas? {
        if (state == null) return null
        if (state.width <= 0 || state.height <= 0) return null
        val info = SkImageInfo.MakeN32Premul(state.width, state.height)
        val surface = SkSurfaces.Raster(info) ?: return null
        val canvas = surface.canvas
        canvas.setMatrix(state.matrix)
        canvas.clipRect(state.clipBounds)
        return canvas
    }

    /**
     * Release the memory associated with a captured state.
     * No-op — the JVM's garbage collector reclaims [SkCanvasState]
     * automatically. Retained for API parity with the upstream C++
     * `Release` step, where the opaque `SkCanvasState*` is heap-
     * allocated by the security-handler-style ownership model.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun ReleaseCanvasState(state: SkCanvasState?) {
        // No-op : the JVM GC reclaims the captured state when its
        // last reference drops.
    }
}
