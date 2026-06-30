package org.graphiks.kanvas.canvas

/**
 * A buffer that records [DisplayOp] entries for deferred rendering.
 *
 * Implementations may store ops in memory, serialize them for transfer to a
 * GPU thread, or persist them for playback analysis.
 */
interface DisplayListBuffer {
    /** Append a single [DisplayOp] to the buffer. */
    fun append(op: DisplayOp)

    /** Return an immutable snapshot of all recorded display operations. */
    fun ops(): List<DisplayOp>
}
