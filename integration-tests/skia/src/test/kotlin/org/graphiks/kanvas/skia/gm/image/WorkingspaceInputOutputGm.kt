package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/workingspace.cpp::workingspace_input_output` (256x256).
 *
 * STUB: Requires the four-argument `SkWorkingColorSpaceShader::Make(child, inputCS, outputCS,
 * workInUnpremul)` — only the two-argument `makeWithWorkingColorSpace(workingCS)` is exposed.
 * @see https://github.com/google/skia/blob/main/gm/workingspace.cpp
 */
class WorkingspaceInputOutputGm : SkiaGm {
    override val name = "workingspace_input_output"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: four-argument SkWorkingColorSpaceShader not exposed
    }
}
