package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.foundation.SkTileMode
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ClippedBitmapShadersGM

/**
 * Cross-test : `ClippedBitmapShadersGM` (Clamp / Repeat / Mirror
 * variants) on the GPU backend.
 *
 * 300 x 300 canvas, 3 x 3 grid (centre cell omitted) of clipped
 * rectangles filled by a `SkBitmap.makeShader`-backed paint. The 2 x 2
 * source bitmap (R / G / black / B) is scaled 8x with a centring
 * translate via the shader's local matrix (`scale(8).postTranslate(150,
 * 150)`) — fully axis-aligned. Each cell `save() ; clipRect(rect) ;
 * drawRect(rect, paint) ; restore()` exercises the G5.2 dispatch :
 * `path.isRect() != null && ctm.isAxisAligned && shader.localMatrix.
 * isAxisAligned`. The clipRect+drawRect on the same rect is the
 * natural G5.2 fast path.
 *
 * The `-hq` (Mitchell bicubic) variants are out of scope (cubic
 * resampler not yet wired on the GPU side) — only the 3 default
 * (nearest sampling) variants are cross-tested here.
 *
 * All 3 variants land at 100.00 % byte-exact ; floor 99.95 % absorbs
 * scoring drift.
 */
class ClippedBitmapShadersWebGpuTest {

    @Test
    fun `ClippedBitmapShadersGM kClamp renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ClippedBitmapShadersGM(SkTileMode.kClamp), floor = 99.95)
    }

    @Test
    fun `ClippedBitmapShadersGM kRepeat renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ClippedBitmapShadersGM(SkTileMode.kRepeat), floor = 99.95)
    }

    @Test
    fun `ClippedBitmapShadersGM kMirror renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ClippedBitmapShadersGM(SkTileMode.kMirror), floor = 99.95)
    }
}
