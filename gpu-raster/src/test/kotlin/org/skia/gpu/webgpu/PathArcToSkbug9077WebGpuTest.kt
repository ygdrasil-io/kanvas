package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PathArcToSkbug9077GM

/**
 * Cross-test : `PathArcToSkbug9077GM` on the GPU backend.
 *
 * 200 × 200 single AA-stroked path : 3 lineTo + close, then tangent
 * `arcTo(p1, p2, radius=60)` after the close — regression for the
 * implicit moveTo after close emitting a stale starting point.
 * Exercises G3.4.1 SkStroker on a multi-contour cubic-flattened arc.
 */
class PathArcToSkbug9077WebGpuTest {

    @Test
    fun `PathArcToSkbug9077GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(PathArcToSkbug9077GM(), floor = 98.95)
    }
}
