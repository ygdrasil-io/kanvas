package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/lattice.cpp` — `LatticeGM2`.
 * Tests nine-patch / lattice image drawing (drawImageLattice) with fixed-color
 * and 1x1 rectangle code paths.
 * @see https://github.com/google/skia/blob/main/gm/lattice.cpp
 */
class Lattice2Gm : SkiaGm {
    override val name = "lattice2"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.LATTICE2 — requires drawImageLattice API")
    }
}
