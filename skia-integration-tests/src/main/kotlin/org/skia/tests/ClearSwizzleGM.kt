package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's
 * [`gm/clear_swizzle.cpp::DEF_SIMPLE_GPU_GM_CAN_FAIL(clear_swizzle)`](https://github.com/google/skia/blob/main/gm/clear_swizzle.cpp).
 *
 * The upstream GM exercises GPU swizzle-aware clears via the Ganesh
 * internal `SurfaceFillContext` API. It creates offscreen surfaces with
 * bgra-swizzled read/write swizzles using
 * `GrRecordingContextPriv::makeSFC` and `skgpu::Swizzle::Concat`, then
 * performs both partial and full clear operations — verifying that
 * `sfc->clear(rect, color)` correctly applies the swizzle transform so
 * that the cleared colour values are stored in BGRA order.
 *
 * Canvas layout (6×kSize × 2×kSize, kSize = 64 → 384 × 128):
 *  - Columns 0–1 : on-screen partial clears (four quadrants: R, G, B, magenta)
 *  - Columns 2–3 : partial offscreen clears blitted back (swizzle applied → BGRA swap)
 *  - Columns 4–5 : four separate full-surface offscreen clears blitted back
 *
 * Ganesh-internal types used (no raster equivalent):
 *  - `skgpu::ganesh::TopDeviceSurfaceFillContext(canvas)` — retrieves the
 *    Ganesh `SurfaceFillContext` for the canvas's top device.
 *  - `skgpu::Swizzle` / `skgpu::Swizzle::Concat` — GPU-level channel
 *    swizzle descriptors (src/gpu/Swizzle.h, not exposed in SkCanvas API).
 *  - `GrRecordingContextPriv::makeSFC` — private factory for off-screen
 *    `SurfaceFillContext` (src/gpu/ganesh/GrRecordingContextPriv.h).
 *  - `SurfaceFillContext::clear` / `blitTexture` — GPU-only operations.
 *
 * **Classification: INTRACTABLE.GPU_ONLY** — all rendering paths require
 * a live Ganesh GPU context; none of these APIs exist in the
 * `:kanvas-skia` CPU raster backend. The test is `@Disabled`.
 *
 * Tracked as TODO: STUB.CLEAR_SWIZZLE_GPU
 */
public class ClearSwizzleGM : GM() {

    override fun getName(): String = "clear_swizzle"
    override fun getISize(): SkISize = SkISize.Make(6 * K_SIZE, 2 * K_SIZE)

    override fun onDraw(canvas: SkCanvas?) {
        TODO(
            "STUB.CLEAR_SWIZZLE_GPU: clear_swizzle uses skgpu::Swizzle, " +
                "GrRecordingContextPriv::makeSFC, and SurfaceFillContext::clear — " +
                "all Ganesh GPU-only internals with no raster equivalent in kanvas-skia.",
        )
    }

    private companion object {
        /** Size of each coloured clear rectangle, matching upstream `kSize = 64`. */
        const val K_SIZE: Int = 64
    }
}
