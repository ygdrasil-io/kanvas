package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/composeshader.cpp::ComposeShaderAlphaGM`.
 *
 * Original composes two shaders (gradient + bitmap) through
 * `SkShaders::Blend(mode)` with varying paint alpha, to verify that
 * the compose-shader honours the outer paint alpha consistently
 * across blend modes.
 *
 * TODO: missing API — `SkShaders.Blend(mode, dst, src)` compose-shader
 * factory. Flag-planting stub.
 */
public class ComposeShaderAlphaGM : GM() {
    override fun getName(): String = "composeshader_alpha"
    override fun getISize(): SkISize = SkISize.Make(750, 220)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — SkShaders.Blend compose-shader factory.
    }
}
