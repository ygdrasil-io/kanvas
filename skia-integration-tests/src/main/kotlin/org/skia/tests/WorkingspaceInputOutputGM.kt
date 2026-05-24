package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/workingspace.cpp::workingspace_input_output`
 * (`DEF_SIMPLE_GM(workingspace_input_output, canvas, 256, 256)`).
 *
 * The GM renders a 2×3 grid of 32×32 cells verifying that colour
 * conversion and alpha-type handling are consistent across three input
 * types (solid colour, managed-uniform shader, and bitmap-backed image
 * shader) when the rasterisation is performed inside a custom
 * working colour space.
 *
 * The implementation wraps each child shader via the *four-argument*
 * `SkWorkingColorSpaceShader::Make(child, inputCS, outputCS, workInUnpremul)`.
 * This variant is an internal Skia API (`src/shaders/SkWorkingColorSpaceShader.h`)
 * and is not exposed through the kanvas-skia public surface — only the
 * two-argument form (child + workingCS) is available as
 * [org.skia.foundation.SkShader.makeWithWorkingColorSpace], which does not
 * accept separate input/output colour spaces or the `workInUnpremul` flag.
 *
 * **Missing API** : `STUB.WORKING_COLOR_SPACE_IO` — a four-argument
 * `SkWorkingColorSpaceShader::Make(child, inputCS, outputCS, workInUnpremul)`
 * is needed. Exposing it requires adding a new factory to the public
 * [SkWorkingColorSpaceShader] wrapper class and plumbing the extra two
 * parameters (separate output CS + unpremul flag) through the
 * [org.skia.foundation.SkWorkingColorSpaceShader] internal class.
 *
 * TODO("STUB.WORKING_COLOR_SPACE_IO")
 */
public class WorkingspaceInputOutputGM : GM() {

    override fun getName(): String = "workingspace_input_output"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        TODO(
            "STUB.WORKING_COLOR_SPACE_IO: workingspace_input_output requires the four-argument" +
                " SkWorkingColorSpaceShader::Make(child, inputCS, outputCS, workInUnpremul)" +
                " which is an internal Skia API not yet exposed in kanvas-skia." +
                " Only the two-argument makeWithWorkingColorSpace(workingCS) is currently available.",
        )
    }
}
