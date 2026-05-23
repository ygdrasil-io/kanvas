package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Test stub for the `fpcoordinateoverride` GM from
 * `gm/fpcoordinateoverride.cpp`.
 *
 * This GM is permanently disabled because it requires Ganesh-internal
 * GPU APIs that have no equivalent in `:kanvas-skia`'s CPU/WebGPU
 * pipeline:
 *
 *  - `GrFragmentProcessor` (with `ProgramImpl` / `emitCode` / GLSL
 *    fragment shader builder)
 *  - `skgpu::ganesh::TopDeviceSurfaceFillContext` + `fillWithFP`
 *  - `GrTextureEffect` + `GrMakeCachedBitmapProxyView`
 *
 * See [FpCoordinateOverrideGM] for a detailed rationale.
 */
@Disabled("STUB.GR_FRAGMENT_PROCESSOR: fpcoordinateoverride requires GrFragmentProcessor + SurfaceFillContext (Ganesh GPU-only) — INTRACTABLE")
class FpCoordinateOverrideTest {

    @Test
    fun `fpcoordinateoverride GM stub`() {
        TestUtils.runGmTest(FpCoordinateOverrideGM())
    }
}
