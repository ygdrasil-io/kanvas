package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.types.Rect

/**
 * An immutable snapshot of recorded drawing commands.
 *
 * Created by [PictureRecorder] and can be drawn onto any [Canvas]
 * via [Canvas.drawPicture] or replayed in full via [playback].
 */
class Picture internal constructor(
    val cullRect: Rect,
    internal val ops: List<DisplayOp>,
) {
    /** Unique identifier for this picture instance. */
    val uniqueID: Int = nextId()

    /**
     * Replay this picture's drawing commands onto [canvas].
     *
     * The canvas's save/restore balance is preserved — each call
     * is wrapped in a save/restore pair.
     */
    fun playback(canvas: Canvas) {
        canvas.save()
        try {
            for (op in ops) {
                when (op) {
                    is DisplayOp.DrawRect -> canvas.drawRect(op.rect, op.paint)
                    is DisplayOp.DrawRRect -> canvas.drawRRect(op.rrect, op.paint)
                    is DisplayOp.DrawDRRect -> canvas.drawDRRect(op.outer, op.inner, op.paint)
                    is DisplayOp.DrawPath -> canvas.drawPath(op.path, op.paint)
                    is DisplayOp.DrawPoint -> canvas.drawPoint(op.x, op.y, op.paint)
                    is DisplayOp.DrawPoints -> canvas.drawPoints(op.mode, op.points, op.paint)
                    is DisplayOp.DrawImage -> canvas.drawImage(op.image, op.dst, op.paint)
                    is DisplayOp.DrawImageNine -> canvas.drawImageNine(op.image, op.center, op.dst, op.paint)
                    is DisplayOp.DrawImageLattice -> canvas.drawImageLattice(op.image, op.lattice, op.dst, op.paint)
                    is DisplayOp.DrawText -> canvas.drawText(op.blob, op.x, op.y, op.paint)
                    is DisplayOp.DrawPicture -> canvas.drawPicture(op.picture, op.paint)
                    is DisplayOp.DrawVertices -> canvas.drawVertices(op.vertices, op.paint)
                    is DisplayOp.DrawAtlas -> canvas.drawAtlas(op.atlas, op.transforms, op.texRects, op.colors, op.blendMode, op.paint)
                    is DisplayOp.DrawColor -> canvas.drawColor(op.color, op.mode)
                    is DisplayOp.Clear -> canvas.clear(op.color)
                    is DisplayOp.SetTransform -> canvas.setMatrix(op.matrix)
                    is DisplayOp.SetClip -> { /* clip is baked into draw ops; state tracked during recording */ }
                    is DisplayOp.BeginLayer -> canvas.saveLayer(op.bounds, op.paint)
                    is DisplayOp.EndLayer -> canvas.restore()
                    is DisplayOp.Annotation -> { /* no visual output */ }
                }
            }
        } finally {
            canvas.restore()
        }
    }

    /**
     * Approximate number of display operations in this picture.
     *
     * @param nested if true, recursively count ops in nested pictures
     */
    fun approximateOpCount(nested: Boolean = false): Int {
        if (!nested) return ops.size
        return ops.sumOf { op ->
            if (op is DisplayOp.DrawPicture) 1 + op.picture.approximateOpCount(true) else 1
        }
    }

    /**
     * Approximate memory footprint of this picture in bytes.
     * Does not include the memory of referenced objects owned externally.
     */
    fun approximateBytesUsed(): Int = ops.size * 128

    /** Serialize this picture to a compact binary representation. */
    fun toByteArray(): ByteArray {
        // Serialization deferred to Phase 3.3 — return minimal stub
        return ByteArray(0)
    }

    companion object {
        /** Deserialize a Picture from its binary representation. */
        fun fromByteArray(data: ByteArray): Picture? {
            // Deserialization deferred — return null stub
            return null
        }

        private var globalId = 0
        private fun nextId(): Int = synchronized(this) { ++globalId }
    }
}
