package org.skia.core

/**
 * Mirrors Skia's
 * [`GrDeferredDisplayList`](https://github.com/google/skia/blob/main/include/private/chromium/GrDeferredDisplayList.h)
 * — an immutable, replay-able list of canvas operations bound to a
 * specific [SkSurfaceCharacterization].
 *
 * **Why DDL when [SkPicture] already exists** — the difference is
 * the **characterization-locked playback** : a DDL can only replay
 * onto a surface whose `imageInfo()` exactly matches the
 * characterization the DDL was recorded against. A picture is
 * surface-agnostic. The lock is what lets clients tile a scene,
 * record per-tile DDLs in parallel ahead of time, and feed each
 * DDL into its dedicated surface confident no
 * pixel-format / colour-space mismatch will sneak in.
 *
 * **Raster-only scope** — upstream Skia's DDL is a Ganesh-GPU
 * artifact ; we faithfully port the API surface but the
 * implementation is single-threaded raster (the "thread-safe
 * handoff" advertised by upstream is nominal in our pipeline,
 * since there is no concurrent GPU state to synchronise). The
 * recording is delegated to [SkPicture] verbatim — DDL is
 * essentially `(characterization, picture)`. Playback runs the
 * picture against the canvas after the characterization gate.
 *
 * **Lifecycle** :
 * 1. Caller owns a [characterization] (typically from
 *    [SkSurfaceCharacterization.From]).
 * 2. Caller constructs an [SkDeferredDisplayListRecorder], drives
 *    its canvas, and calls `detach()` to snap the DDL.
 * 3. Caller passes the DDL to [SkSurface.draw] — returns `false`
 *    if the surface's signature drifted from the characterization
 *    (and the DDL is left untouched).
 *
 * Mirrors Skia's `skgpu::ganesh::DrawDDL(SkSurface*, sk_sp<DDL>)`.
 */
public class SkDeferredDisplayList internal constructor(
    public val characterization: SkSurfaceCharacterization,
    private val picture: SkPicture,
) {
    /** Number of recorded ops — useful for diagnostics and tests. */
    public val opCount: Int get() = picture.opCount

    /**
     * Internal playback hook used by [SkSurface.draw]. Replays
     * the recorded picture against [canvas]. The caller is
     * responsible for the characterization compatibility gate.
     */
    internal fun playbackInto(canvas: SkCanvas) {
        picture.playback(canvas)
    }
}
