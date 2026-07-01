package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.paint.BlendMode

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

    /** Fill the entire canvas with a color and blend mode. */
    data class DrawColor(val color: Color, val mode: BlendMode, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Overwrite the entire canvas with a color (ignores blend mode). */
    data class Clear(val color: Color) : DisplayOp

    /** Draw a single point primitive. */
    data class DrawPoint(val x: Float, val y: Float, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Draw multiple points with a point mode. */
    data class DrawPoints(val mode: PointMode, val points: List<Point>, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Draw a double rounded rectangle (outer + inner). */
    data class DrawDRRect(val outer: RRect, val inner: RRect, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Draw a 9-patch image. */
    data class DrawImageNine(val image: Image, val center: Rect, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Draw a lattice image. */
    data class DrawImageLattice(val image: Image, val lattice: Lattice, val dst: Rect, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Draw a pre-recorded Picture. */
    data class DrawPicture(val picture: Picture, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Draw a triangle mesh. */
    data class DrawVertices(val vertices: Vertices, val paint: Paint, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Batch-draw sprites from an atlas texture. */
    data class DrawAtlas(val atlas: Image, val transforms: List<Matrix33>, val texRects: List<Rect>, val colors: List<Color>?, val blendMode: BlendMode, val paint: Paint?, val transform: Matrix33, val clip: ClipStack) : DisplayOp

    /** Metadata annotation (no visual output). */
    data class Annotation(val rect: Rect, val key: String, val value: String) : DisplayOp
}
