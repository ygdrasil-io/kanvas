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
    private val appendContext = GeometrySnapshotContext()

    override fun append(op: DisplayOp) {
        recorded += op.snapshotGeometry(appendContext)
    }

    override fun ops(): List<DisplayOp> = recorded.snapshotGeometry()
}

/** Applies the recording contract to caller-provided buffer implementations. */
internal class GeometrySnapshotDisplayListBuffer(
    private val delegate: DisplayListBuffer,
) : DisplayListBuffer {
    private val appendContext = GeometrySnapshotContext()

    override fun append(op: DisplayOp) {
        delegate.append(op.snapshotGeometry(appendContext))
    }

    override fun ops(): List<DisplayOp> = delegate.ops().snapshotGeometry()
}
