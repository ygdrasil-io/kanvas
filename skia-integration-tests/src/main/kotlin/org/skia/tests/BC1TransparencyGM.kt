package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/bc1_transparency.cpp::BC1TransparencyGM`.
 *
 * Original tests BC1 (DXT1) compressed-texture transparency : decodes
 * a BC1-compressed RGBA payload and verifies single-bit alpha + black
 * fallback rendering on the GPU backend.
 *
 * TODO: missing API — `SkImage.MakeRasterFromCompressed` /
 * `SkTextureCompressionType.kBC1` decode + GPU upload of compressed
 * formats. Flag-planting stub.
 */
public class BC1TransparencyGM : GM() {
    override fun getName(): String = "bc1_transparency"
    override fun getISize(): SkISize = SkISize.Make(180, 540)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — BC1/DXT1 compressed texture decode + upload.
    }
}
