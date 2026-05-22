package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/compressed_textures.cpp::CompressedTexturesGM`.
 *
 * Original uploads BC1 / ETC1 / ASTC compressed texture payloads,
 * draws them at native + scaled sizes, and verifies decode + GPU
 * sampling. Validates each compressed format end-to-end.
 *
 * TODO: missing API — `SkImage.MakeRasterFromCompressed` /
 * `SkTextureCompressionType.kBC1/kETC1/kASTC` decode + GPU upload of
 * compressed formats. Flag-planting stub.
 */
public class CompressedTexturesGM : GM() {
    override fun getName(): String = "compressed_textures"
    override fun getISize(): SkISize = SkISize.Make(1024, 768)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — compressed texture (BC1/ETC1/ASTC) decode + upload.
    }
}
