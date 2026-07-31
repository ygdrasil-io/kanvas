package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.atomic.AtomicLong

/** Handle-free count of frame-local snapshots allocated by the sealed prepared-surface route. */
internal class GPUPreparedSurfaceDestinationSnapshotCounter {
    private val creations = AtomicLong(0L)

    fun recordCreation() {
        creations.incrementAndGet()
    }

    fun snapshot(): Long = creations.get()
}
