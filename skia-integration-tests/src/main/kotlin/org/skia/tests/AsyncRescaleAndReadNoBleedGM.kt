package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadNoBleedGM`.
 *
 * Original verifies that the async rescale+readback pipeline does not
 * bleed pixels across rescale tile boundaries.
 *
 * TODO: missing API — `SkSurface.asyncRescaleAndReadPixels` with bleed
 * guard. Flag-planting stub.
 */
public class AsyncRescaleAndReadNoBleedGM : GM() {
    override fun getName(): String = "async_rescale_and_read_no_bleed"
    override fun getISize(): SkISize = SkISize.Make(120, 60)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.ASYNC_RESCALE_READ: SkSurface.asyncRescaleAndReadPixels not yet implemented")
    }
}
