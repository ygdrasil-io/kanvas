package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * A tagged union of every display operation a [Canvas] can record.
 *
 * Each drawing op carries the [Matrix33] transform and [ClipStack] that were
 * active at the time the op was recorded, enabling deferred rendering without
 * reference back to the originating [Canvas] state.
 */
sealed interface DisplayOp {
    /** Draw an axis-aligned rectangle. */
    data class DrawRect(
        val rect: Rect, val paint: Paint,
        val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp

    /** Draw a rounded rectangle. */
    data class DrawRRect(
        val rrect: RRect, val paint: Paint,
        val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp

    /** Draw an arbitrary path. */
    data class DrawPath(
        val path: Path, val paint: Paint,
        val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp

    /** Draw a sub-region of an image, optionally modulated by [paint]. */
    data class DrawImage(
        val image: Image, val src: Rect, val dst: Rect,
        val paint: Paint?, val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp

    /** Draw a text blob at the given position. */
    data class DrawText(
        val blob: TextBlob, val x: Float, val y: Float,
        val paint: Paint, val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp

    /** Replace the current transform matrix. */
    data class SetTransform(val matrix: Matrix33) : DisplayOp

    /** Replace the current clip state. */
    data class SetClip(val clip: ClipStack) : DisplayOp

    /** Begin an offscreen layer with optional bounds and compositing [Paint]. */
    data class BeginLayer(val bounds: Rect?, val paint: Paint?) : DisplayOp

    /** End the most recently begun offscreen layer, compositing it back. */
    data object EndLayer : DisplayOp
}
