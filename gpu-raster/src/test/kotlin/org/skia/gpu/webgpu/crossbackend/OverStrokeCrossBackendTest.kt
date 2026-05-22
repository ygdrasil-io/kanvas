package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.OverStrokeGM

/**
 * Cross-backend test : `OverStrokeGM` on raster + GPU.
 *
 * K8 RCA — the 15-pt similarity gap vs the upstream `original-888/`
 * reference is **not** raster ↔ GPU drift. Initial K8 measurement
 * shows GPU 84.81 % vs raster 84.87 % (Δ ≈ 0.06 pt, well inside the
 * 2 % cross-backend warning band) — both backends produce visually
 * identical output. The gap vs reference is intrinsic to the kanvas
 * `SkStroker` algorithm.
 *
 * Root cause : when `strokeWidth >> path curvature radius` the
 * "outer offset / inner offset" approach used by `SkStroker` emits
 * self-intersecting inner offsets (the classic overstroke
 * butterfly / bowtie artifact). The original 2016 Skia C++ shipped
 * with the **same** bug (see `gm/overstroke.cpp` header comment :
 * "we offset each part of the curve the request amount even if it
 * makes the offsets overlap and create holes. There is not a really
 * great algorithm for this and several other 2D graphics engines
 * have the same bug.").
 *
 * The `original-888/OverStroke.png` reference was rendered with a
 * later upstream version that adds a `fCusper` band-aid for cubic
 * cusps (`SkPathStroker::cubicTo` → `fCusper.addCircle(cuspLoc,
 * fRadius)` in `src/core/SkStroke.cpp`). The kanvas port does not
 * implement that band-aid yet ; it would only help the cubic cell,
 * not the closed-quad / oval rows where the artifact also appears.
 *
 * **Update (post inner-offset orientation heuristic)** : the SkStroker
 * now applies a contour-level "engulfed offset" heuristic for closed
 * contours where `halfW > 1.5 × bboxMaxDim` AND the two offset
 * polylines share the same signed orientation with a high area ratio
 * (> 0.7). In that case both offsets are emitted as same-direction
 * filled regions (winding UNION) instead of "outer minus reversed
 * inner". On OverStrokeGM the heuristic fires on the quad cell
 * (40-unit arch stroked at halfW = 250), unlocking +2.4 pt on the
 * raster score (83.48 % → 85.87 %). See
 * `kanvas-skia/src/main/kotlin/org/skia/foundation/SkStroker.kt`
 * (the `engulfed` branch in `strokeContour`).
 *
 * Floors : raster 85.5 % / GPU 85.5 %.
 */
class OverStrokeCrossBackendTest {

    @Test
    fun `OverStrokeGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = OverStrokeGM(),
            rasterFloor = 85.5,
            gpuFloor = 85.5,
        )
    }
}
