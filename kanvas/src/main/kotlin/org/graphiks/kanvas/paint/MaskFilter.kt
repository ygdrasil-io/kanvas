package org.graphiks.kanvas.paint

import org.graphiks.kanvas.pipeline.BlurStyle

sealed interface MaskFilter {
    data class Blur(val style: BlurStyle, val sigma: Float) : MaskFilter
}
