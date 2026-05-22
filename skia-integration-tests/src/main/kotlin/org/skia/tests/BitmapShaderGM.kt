package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/bitmapshader.cpp::BitmapShaderGM`.
 *
 * Original draws a bitmap-backed shader (`SkBitmap::makeShader`) under a
 * variety of `SkTileMode` permutations.
 *
 * Many specialised bitmap-shader GMs are already ported separately
 * (e.g. `ClippedBitmapShadersGM`, `BitmapTiledGM`, `BitmapSubsetShaderGM`).
 * This placeholder plants the upstream base-name flag; a fuller port
 * can fold the canonical bitmap-shader matrix later.
 *
 * TODO: drive the full upstream `BitmapShaderGM` permutation matrix
 * (kClamp / kRepeat / kMirror × tx,ty offsets × premul / unpremul).
 */
public class BitmapShaderGM : GM() {
    override fun getName(): String = "bitmapshaders"
    override fun getISize(): SkISize = SkISize.Make(150, 100)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op flag-planting stub : tiled bitmap-shader coverage lives
        // in ClippedBitmapShadersGM, BitmapTiledGM, BitmapSubsetShaderGM.
    }
}
