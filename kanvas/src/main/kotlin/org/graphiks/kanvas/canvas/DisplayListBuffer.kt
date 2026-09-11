package org.graphiks.kanvas.canvas

import java.util.Collections
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.SceneCaptureLimits

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
internal class SnapshotDisplayListBuffer(
    captureLimits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT,
) : SnapshotOwningDisplayListBuffer {
    private val recorded = mutableListOf<DisplayOp>()
    private val gradientStops = RecordingGradientStopBudget(captureLimits.maxGradientStopsI32)
    private val appendContext = GeometrySnapshotContext(gradientStops)

    override fun append(op: DisplayOp) {
        gradientStops.append { appendContext.append(op) { recorded += it } }
    }

    override fun ops(): List<DisplayOp> = recorded.snapshotGeometry()

    override fun sealedOps(): List<DisplayOp> = Collections.unmodifiableList(recorded.toList())
}

/** Internal consumers may read owned snapshots without copying their payloads. */
internal interface SnapshotOwningDisplayListBuffer : DisplayListBuffer {
    fun sealedOps(): List<DisplayOp>
}

/** Applies the recording contract to caller-provided buffer implementations. */
internal class GeometrySnapshotDisplayListBuffer(
    private val delegate: DisplayListBuffer,
) : DisplayListBuffer {
    private val gradientStops = RecordingGradientStopBudget(SceneCaptureLimits.DEFAULT.maxGradientStopsI32)
    private val appendContext = GeometrySnapshotContext(gradientStops)

    override fun append(op: DisplayOp) {
        gradientStops.append { appendContext.append(op, delegate::append) }
    }

    override fun ops(): List<DisplayOp> = delegate.ops().snapshotGeometry()
}

/** A pending operation consumes budget only after its complete append succeeds. */
internal class RecordingGradientStopBudget(private val maxGradientStopsI32: Int) {
    private var committedStopsI64 = 0L
    private var pendingStopsI64 = 0L

    fun reserveGradientStops(stopsCountI32: Int) {
        val requestedI64 = Math.addExact(
            Math.addExact(committedStopsI64, pendingStopsI64),
            stopsCountI32.toLong(),
        )
        if (requestedI64 > maxGradientStopsI32.toLong()) {
            throw SceneRecordingLimitException(
                diagnostic = RenderDiagnostic(
                    RenderDiagnosticCode("scene-recording-gradient-stops-exceeded"),
                    RenderDiagnosticDomain.SCENE,
                    RenderDiagnosticSeverity.ERROR,
                    "Recording requests $requestedI64 gradient stops, exceeding limit $maxGradientStopsI32",
                ),
                limitI32 = maxGradientStopsI32,
                requestedI64 = requestedI64,
            )
        }
        pendingStopsI64 = Math.addExact(pendingStopsI64, stopsCountI32.toLong())
    }

    fun append(block: () -> Unit) {
        try {
            block()
            committedStopsI64 = Math.addExact(committedStopsI64, pendingStopsI64)
        } finally {
            pendingStopsI64 = 0L
        }
    }
}
