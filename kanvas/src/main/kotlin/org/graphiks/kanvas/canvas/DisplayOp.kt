package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

sealed interface DisplayOp {
    data class DrawRect(
        val rect: Rect, val paint: Paint,
        val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp
    data class DrawRRect(
        val rrect: RRect, val paint: Paint,
        val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp
    data class DrawPath(
        val path: Path, val paint: Paint,
        val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp
    data class DrawImage(
        val image: Image, val src: Rect, val dst: Rect,
        val paint: Paint?, val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp
    data class DrawText(
        val blob: TextBlob, val x: Float, val y: Float,
        val paint: Paint, val transform: Matrix33, val clip: ClipStack,
    ) : DisplayOp
    data class SetTransform(val matrix: Matrix33) : DisplayOp
    data class SetClip(val clip: ClipStack) : DisplayOp
    data class BeginLayer(val bounds: Rect?, val paint: Paint?) : DisplayOp
    data object EndLayer : DisplayOp
}
