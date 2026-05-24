package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadAlphaTypeGM`.
 *
 * Original exercises the async pixel-read + rescale pipeline
 * (`SkSurface::asyncRescaleAndReadPixels`) across alpha types
 * (`kPremul`, `kUnpremul`). Designed to verify GPU readback round-trip
 * conformance.
 *
 * TODO: missing API — `SkSurface.asyncRescaleAndReadPixels` (and the
 * GPU readback async-callback machinery). Flag-planting stub.
 */
public class AsyncRescaleAndReadAlphaTypeGM : GM() {
    override fun getName(): String = "async_rescale_and_read_alpha_type"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.ASYNC_RESCALE_READ: SkSurface.asyncRescaleAndReadPixels not yet implemented")
    }
}
