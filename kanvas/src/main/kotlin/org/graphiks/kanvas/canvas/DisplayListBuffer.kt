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

/** In-memory buffer that owns a defensive geometry snapshot at both boundaries. */
internal class SnapshotDisplayListBuffer : DisplayListBuffer {
    private val recorded = mutableListOf<DisplayOp>()

    override fun append(op: DisplayOp) {
        recorded += op.snapshotGeometry()
    }

    override fun ops(): List<DisplayOp> = recorded.map(DisplayOp::snapshotGeometry)
}

/** Applies the recording contract to caller-provided buffer implementations. */
internal class GeometrySnapshotDisplayListBuffer(
    private val delegate: DisplayListBuffer,
) : DisplayListBuffer {
    override fun append(op: DisplayOp) {
        delegate.append(op.snapshotGeometry())
    }

    override fun ops(): List<DisplayOp> = delegate.ops().map(DisplayOp::snapshotGeometry)
}
