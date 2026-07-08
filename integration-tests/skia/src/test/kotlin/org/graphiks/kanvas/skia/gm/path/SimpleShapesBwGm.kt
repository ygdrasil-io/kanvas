package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/** Non-anti-aliased variant of SimpleShapesGm for black-and-white shape rendering. */
class SimpleShapesBwGm : SimpleShapesGm(antialias = false) {
    override val minSimilarity = 80.0
}
