package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/pathmeasure.cpp`.
 *
 * The C++ file defines a single GM — `PathMeasure_explosion` — that is
 * **unconditionally disabled** by the upstream authors via `#if 0`.  The
 * comment in the source file explains why:
 *
 * > Repro case for skbug.com/40038934.  Requires lots of RAM to run, and
 * > currently triggers UB:
 * > //include/private/base/SkTDArray.h:382:26:
 * >   runtime error: signed integer overflow: 2147483644 + 4 cannot be
 * >   represented in type 'int'
 *
 * The GM builds a path with up to `2 147 483 647` points (INT_MAX) while
 * applying a `{0, 10e9f}` dash path-effect — a deliberate stress test
 * that deliberately triggers an integer-overflow bug in Skia's array
 * implementation.  Running it on any JVM equivalent would require tens of
 * gigabytes of heap and would itself cause undefined / platform-dependent
 * behaviour.  There is no meaningful reference image to compare against.
 *
 * The [SkPathMeasure] API itself *is* already implemented in
 * `:kanvas-skia` (`org.skia.foundation.SkPathMeasure`).  This stub is
 * only a flag-plant — it records that `pathmeasure.cpp` was reviewed and
 * intentionally omitted.
 *
 * TODO("STUB.PATH_MEASURE_EXPLOSION: upstream GM unconditionally disabled
 *   via #if 0 — intentional INT_MAX path stress-test that triggers UB /
 *   requires multi-GB heap; unportable by design")
 */
public class PathMeasureExplosionGM : GM() {

    override fun getName(): String = "PathMeasure_explosion"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        TODO(
            "STUB.PATH_MEASURE_EXPLOSION: upstream GM unconditionally disabled via #if 0 — " +
                "intentional INT_MAX path stress-test that triggers UB / requires multi-GB heap; " +
                "unportable by design (skbug.com/40038934)"
        )
    }
}
