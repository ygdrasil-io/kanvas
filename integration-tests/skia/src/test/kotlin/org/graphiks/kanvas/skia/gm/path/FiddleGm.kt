package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/fiddle.cpp`.
 * Deliberately empty stub used by `fiddle.skia.org` — contributors paste
 * their fiddle source into the body when reproducing a bug locally.
 * Acts as a background-fill sanity check in CI.
 * @see https://github.com/google/skia/blob/main/gm/fiddle.cpp
 */
class FiddleGm : SkiaGm {
    override val name = "fiddle"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Intentionally empty — matches upstream's stub draw body verbatim.
    }
}
