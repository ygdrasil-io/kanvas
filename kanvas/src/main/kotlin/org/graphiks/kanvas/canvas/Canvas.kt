package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.*

class Canvas internal constructor(private val buffer: DisplayListBuffer) {
    private var currentTransform = Matrix33.identity()
    private var currentClip: ClipStack = ClipStack.WideOpen
    private var saveStack = mutableListOf<CanvasState>()

    val matrix: Matrix33 get() = currentTransform
    val saveCount: Int get() = saveStack.size
    val localClipBounds: Rect
        get() = when (val clip = currentClip) {
            ClipStack.WideOpen -> Rect.fromLTRB(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            is ClipStack.DeviceRect -> clip.rect
            is ClipStack.Complex -> Rect.EMPTY
        }

    fun drawRect(rect: Rect, paint: Paint) {
        buffer.append(DisplayOp.DrawRect(rect, paint, currentTransform, currentClip))
    }
    fun drawRRect(rrect: RRect, paint: Paint) {
        buffer.append(DisplayOp.DrawRRect(rrect, paint, currentTransform, currentClip))
    }
    fun drawPath(path: Path, paint: Paint) {
        buffer.append(DisplayOp.DrawPath(path, paint, currentTransform, currentClip))
    }
    fun drawImage(image: Image, dst: Rect, paint: Paint? = null) {
        val src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentClip))
    }
    fun drawImageRect(image: Image, src: Rect, dst: Rect, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentClip))
    }
    fun drawText(blob: TextBlob, x: Float, y: Float, paint: Paint) {
        buffer.append(DisplayOp.DrawText(blob, x, y, paint, currentTransform, currentClip))
    }
    fun save(): Int {
        saveStack.add(CanvasState(currentTransform, currentClip))
        return saveStack.size
    }
    fun saveLayer(bounds: Rect? = null, paint: Paint? = null): Int {
        buffer.append(DisplayOp.BeginLayer(bounds, paint))
        saveStack.add(CanvasState(currentTransform, currentClip))
        return saveStack.size
    }
    fun restore() {
        if (saveStack.isNotEmpty()) {
            val state = saveStack.removeLast()
            currentTransform = state.transform
            currentClip = state.clip
        }
        buffer.append(DisplayOp.EndLayer)
    }
    fun restoreToCount(count: Int) {
        while (saveStack.size > count) restore()
    }
    fun translate(x: Float, y: Float) { concat(Matrix33.translate(x, y)) }
    fun scale(sx: Float, sy: Float) { concat(Matrix33.scale(sx, sy)) }
    fun rotate(degrees: Float, px: Float = 0f, py: Float = 0f) {
        if (px == 0f && py == 0f) { concat(Matrix33.rotate(degrees)) }
        else { translate(px, py); concat(Matrix33.rotate(degrees)); translate(-px, -py) }
    }
    fun skew(sx: Float, sy: Float) { concat(Matrix33.skew(sx, sy)) }
    fun concat(matrix: Matrix33) {
        currentTransform = currentTransform * matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }
    fun setMatrix(matrix: Matrix33) {
        currentTransform = matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }
    fun resetMatrix() { setMatrix(Matrix33.identity()) }
    fun clipRect(rect: Rect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        currentClip = ClipStack.DeviceRect(rect)
        buffer.append(DisplayOp.SetClip(currentClip))
    }
    fun clipRRect(rrect: RRect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        currentClip = ClipStack.Complex(listOf(ClipStackOp.RRect(rrect, op)))
        buffer.append(DisplayOp.SetClip(currentClip))
    }
    fun clipPath(path: Path, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        currentClip = ClipStack.Complex(listOf(ClipStackOp.Path(path, op)))
        buffer.append(DisplayOp.SetClip(currentClip))
    }
    private data class CanvasState(val transform: Matrix33, val clip: ClipStack)
}
