package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.math.pow

/** Converts one encoded sRGB paint channel to linear light; alpha stays linear and separate. */
internal fun preparedMaterialSrgbToLinear(channel: Float): Float =
    if (channel <= 0.04045f) {
        channel / 12.92f
    } else {
        ((channel + 0.055f) / 1.055f).pow(2.4f)
    }
