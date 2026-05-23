package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(save_behind, canvas, 830, 670)`.
 *
 * Tests `SkCanvasPriv::SaveBehind` / `SkCanvasPriv::DrawBehind` — private
 * Skia API that saves a snapshot of a partial canvas region before drawing
 * foreground content, then restores and composites it. The GM draws two
 * columns of eight coloured rows; each row demonstrates a "fade-out" effect
 * at the right edge by:
 *  1. Calling `SkCanvasPriv::SaveBehind(&r)` to snapshot the right margin.
 *  2. Drawing foreground text over the full row.
 *  3. Blending a linear gradient (`DstIn`) over the snapshotted region to
 *     make the text fade into transparency.
 *  4. Restoring the "behind" snapshot, compositing the gradient-blended
 *     result on top.
 *
 * The left column uses `drawRect` to apply the gradient treatment;
 * the right column uses `SkCanvasPriv::DrawBehind` (draws directly into
 * the saved-behind layer without clipping the current draw).
 *
 * **Missing API** — `SkCanvasPriv::SaveBehind` and `SkCanvasPriv::DrawBehind`
 * have no equivalent in `:kanvas-skia`. The C++ versions are internal
 * (`src/core/SkCanvasPriv.h`) and expose a "save-behind" layer mechanism
 * separate from the standard saveLayer stack. Implementing them requires
 * adding `onDoSaveBehind` / `onDrawBehind` virtual dispatch to [SkCanvas]
 * and a partial-bitmap snapshot slot to the save stack.
 *
 * TODO: missing API — `SkCanvasPriv::SaveBehind` / `SkCanvasPriv::DrawBehind`
 * (`src/core/SkCanvasPriv.h`). Neither appears in the public
 * `include/core/SkCanvas.h`; exposing them requires a new virtual pair
 * `onDoSaveBehind(SkRect?)` / `onDrawBehind(SkPaint)` on [SkCanvas].
 */
public class SaveBehindGM : GM() {

    override fun getName(): String = "save_behind"
    override fun getISize(): SkISize = SkISize.Make(830, 670)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO("STUB.SAVE_BEHIND: SkCanvasPriv::SaveBehind / DrawBehind not implemented")
    }
}
