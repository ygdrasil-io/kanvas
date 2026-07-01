package org.graphiks.kanvas.surface.gpu

import kotlin.math.pow

internal fun srgbToLinear(c: Float): Float {
    return if (c <= 0.04045f) c / 12.92f
    else ((c + 0.055f) / 1.055f).pow(2.4f)
}
