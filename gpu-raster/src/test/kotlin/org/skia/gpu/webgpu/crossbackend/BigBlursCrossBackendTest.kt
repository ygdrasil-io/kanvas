package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BigBlursGM

/**
 * MaskFilter blur styles cross-backend test : `BigBlursGM`.
 *
 * Ports `gm/bigblurs.cpp::BigBlursGM`. 5 x 8 grid of 64-px close-ups
 * (320 x 512 total) covering :
 *  - 2 shapes : `bigRect` (kFill axis-aligned 65536-px source rect)
 *               + `rectori` (outer rect with inner CCW rect ring),
 *  - 4 [org.skia.foundation.SkBlurStyle] variants per shape :
 *    kNormal / kSolid / kOuter / kInner,
 *  - 5 corners (UL, UR, LR, LL, centre) of the giant source primitive
 *    captured per blur configuration.
 *
 * Exercises all four blur-style branches in `blur_gaussian.wgsl` --
 * kSolid composites the sharp mask back over the blur, kOuter
 * subtracts the sharp mask from the blur, kInner clips the blur to
 * the sharp mask. Reference PNG is the upstream Skia output.
 *
 * Floors (observed) :
 *  - raster : 87.58 %
 *  - GPU    : 94.31 %
 * Both at tolerance 8. The 6.7 % raster-to-GPU gap is the CPU
 * raster's pre-existing kInner/kOuter contour mismatch (the
 * 65536-px nine-patching path) ; the GPU backend isn't aware of
 * nine-patching, so it just rasterises the giant source rect into
 * the offscreen mask (slow, but precise on close-up patches).
 *
 * Floors set to 85 / 92 -- a small headroom below the observed
 * similarities for blur-shader variance across hardware.
 */
class BigBlursCrossBackendTest {

    @Test
    fun `BigBlursGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BigBlursGM(),
            rasterFloor = 85.0,
            gpuFloor = 92.0,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
