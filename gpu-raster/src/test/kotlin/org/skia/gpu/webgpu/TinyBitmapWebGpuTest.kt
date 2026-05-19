package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.TinyBitmapGM

/**
 * G-suivi (round 14) cross-test : `TinyBitmapGM` -- 100 x 100 canvas
 * filled by `drawPaint` with a bitmap shader sourcing a 1 x 1
 * translucent-red bitmap (kRepeat / kMirror tile modes,
 * `paint.alphaf = 0.5`). G5.2 routes `drawPaint(paint with
 * SkBitmapShader)` through `drawPath(path.isRect)` so the
 * bitmap-shader gate fires. Identity CTM and a 1 x 1 source mean the
 * tile mode is essentially identity (every device pixel samples the
 * same single texel) -- the visual result is a uniform translucent
 * dark-red wash over the 0xFFDDDDDD background.
 *
 * Pure axis-aligned bitmap shader + drawPaint workout, all in-scope
 * after G5.2.
 */
class TinyBitmapWebGpuTest {

    @Test
    fun `TinyBitmapGM renders close to reference PNG on the GPU backend`() {
        // Landing score 100.00 % -- byte-exact match (1 x 1 source
        // means tile mode is identity, paint alpha modulation
        // collapses to a deterministic per-pixel multiply).
        runGpuCrossTest(TinyBitmapGM(), floor = 99.95, logTag = "TinyBitmapWebGpu")
    }
}
