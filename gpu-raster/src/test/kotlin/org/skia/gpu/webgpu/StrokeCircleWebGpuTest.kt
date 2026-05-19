package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.StrokeCircleGM

/**
 * G-suivi (round 18) cross-test : `StrokeCircleGM` -- port of upstream
 * `gm/addarc.cpp::StrokeCircleGM`.
 *
 * 520 x 520, concentric AA-stroked ovals under `scale(20, 20) +
 * translate(13, 13)` CTM. Each iteration insets the rect by
 * `delta = 1.5 (device-px)` and re-strokes at `strokeWidth = 0.5`
 * source-units (= 10 device-px). The `rotate(0)` call inside the loop
 * is a no-op but exercises the [org.skia.core.SkCanvas.rotate] dispatch
 * end-to-end.
 *
 * Pure stroker workout : G3.4.1 SkStroker integration is exercised on
 * conic curves (oval = 4 quarter-conics) at 20x CTM scale -- the inverse
 * of `Strokes4GM`'s circle stroke. Per-pixel drift on the stroked oval
 * boundaries amplified by the 20x scale and the random 565-quantised
 * colours.
 *
 * Score on master HEAD : ~92 % (well above the round-17 borderline
 * report of 91.81 %; the stroker / colorspace improvements landed
 * since lifted this cleanly above floor).
 */
class StrokeCircleWebGpuTest {

    @Test
    fun `StrokeCircleGM renders close to reference PNG on the GPU backend`() {
        // Landing score 91.81 %. Floor set 0.05 % below for scoring
        // drift headroom. Residual drift on AA stroke edges of the
        // concentric ovals at 20x CTM scale where the stroker's conic
        // flattening differs sub-LSB from the upstream reference.
        runGpuCrossTest(StrokeCircleGM(), floor = 91.76)
    }
}
