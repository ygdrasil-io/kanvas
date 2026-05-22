package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/anisotropic.cpp::AnisotropicGM`.
 *
 * Original draws a radiating-line bitmap squashed at several scales
 * comparing kLinear / kMip / kAniso sampling. The kAniso variant
 * (`SkSamplingOptions::Aniso(16)`) is the GPU-only path that drives
 * mip / aniso-filter selection in the shader.
 *
 * Existing variants already ported separately:
 *  - `AnisotropicImageScaleLinearGM` (kLinear)
 *  - `AnisotropicImageScaleMipGM`    (kMip)
 *  - `AnisotropicImageScaleAnisoGM`  (kAniso)
 *  - `AnisoMipsGM`
 *
 * This placeholder exposes the base name for the upstream GM, but the
 * shared body has already been ported under the three Mode-specialised
 * GMs. We keep this class to plant the flag on the upstream name; the
 * draw is a no-op.
 *
 * TODO: optional — fold the three variants under one parameterised GM
 * matching the upstream `AnisotropicGM(Mode)` ctor.
 */
public class AnisotropicGM : GM() {
    override fun getName(): String = "anisotropic_image_scale"
    override fun getISize(): SkISize = SkISize.Make(522, 1330)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : variants already ported as AnisotropicImageScale{Linear,Mip,Aniso}GM.
    }
}
