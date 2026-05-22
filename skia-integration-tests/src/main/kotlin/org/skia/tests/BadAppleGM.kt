package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/bad_apple.cpp::BadAppleGM`
 * (regression test for a path-tessellation crash).
 *
 * Original drew a specific torture-test pathfile that historically
 * crashed the GPU tessellator. The path data ships as binary asset in
 * upstream `gm/bad_apple/`.
 *
 * TODO: bundle the torture-test path asset + verify the GPU
 * tessellator handles it without crash. Flag-planting stub.
 */
public class BadAppleGM : GM() {
    override fun getName(): String = "bad_apple"
    override fun getISize(): SkISize = SkISize.Make(480, 360)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: load + draw torture-test path asset from gm/bad_apple/.
    }
}
