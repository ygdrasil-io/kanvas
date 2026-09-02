package org.graphiks.kanvas.surface

import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring

/**
 * Delimits synchronous recording that must not materialize pixel snapshots.
 *
 * Within [recordingOnly], [Surface.makeImageSnapshot] captures the recorded
 * scene and returns an external image reference. Direct renderer submission is
 * rejected, so callers can safely record nested off-screen surfaces for a
 * backend-neutral scene capture.
 */
public object SceneRecordingScope {
    private val depth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    public fun <T> recordingOnly(block: () -> T): T {
        RuntimeEffectWgsl4kWiring.install()
        depth.set(depth.get() + 1)
        return try {
            block()
        } finally {
            val remaining = depth.get() - 1
            if (remaining == 0) depth.remove() else depth.set(remaining)
        }
    }

    internal fun isRecordingOnly(): Boolean = depth.get() > 0
}
