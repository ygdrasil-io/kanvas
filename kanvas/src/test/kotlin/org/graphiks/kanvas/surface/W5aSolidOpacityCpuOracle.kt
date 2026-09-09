@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.toEncoded

/** Independent straight-alpha source fixture for the first public W5a Rect proof. */
internal object W5aSolidOpacityCpuOracle {
    fun source(
        color: ColorARGB,
        shaderOpacityOuterF32: Float,
        shaderOpacityInnerF32: Float,
        paintAlphaF32: Float,
    ): FloatArray {
        val opacity = shaderOpacityOuterF32 * shaderOpacityInnerF32 * paintAlphaF32
        val alpha = color.alphaNormalized * opacity
        return floatArrayOf(
            ColorTransferFunction.sRgb.toEncoded(ColorTransferFunction.sRgb.toLinear(color.redNormalized) * color.alphaNormalized * opacity),
            ColorTransferFunction.sRgb.toEncoded(ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * color.alphaNormalized * opacity),
            ColorTransferFunction.sRgb.toEncoded(ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * color.alphaNormalized * opacity),
            alpha,
        )
    }
}
