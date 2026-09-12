package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop

/** Independent public fixture: inverse translation is x - 3, with nested shader and paint opacity. */
internal object W5dGradientAddressingCpuOracle {
    // Hand-derived endpoint colors: inverse x = 8.5 is blue; inverse x = .5 is red.
    @OptIn(ExperimentalUnsignedTypes::class)
    fun bluePixel(): UByteArray = ubyteArrayOf(0u, 0u, 255u, 255u)
    @OptIn(ExperimentalUnsignedTypes::class)
    fun redPixel(): UByteArray = ubyteArrayOf(255u, 0u, 0u, 255u)

    fun evaluate(pixelXI32: Int, stops: List<GradientStop>, ctmScaleXF32: Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded {
        // The fixture has an exact dy=0 axis and a power-of-two denominator. Samples
        // are inside constant spans, so every permitted coordinate schedule selects
        // the same color; the attachment/opacity envelope remains independently modeled.
        val tF32 = (((pixelXI32 + .5f) / ctmScaleXF32 - 3f) / 8f).coerceIn(0f, 1f)
        val left = stops.lastOrNull { it.position <= tF32 } ?: stops.first()
        val right = stops.firstOrNull { it.position > tF32 } ?: stops.last()
        require(left.color == right.color) { "This fixture must sample a constant-color span" }
        val color = left.color
        val result = WgslFloatEnvelopeV1Oracle.gradientThenBlend({
            listOf(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized)
                .map(WgslFloatEnvelopeV1Oracle.Interval::input).toTypedArray()
        }, (.75f * .5f) * (191f / 255f), WgslFloatEnvelopeV1Oracle.clearAttachment(), BlendMode.SRC_OVER)
        require(result is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { result.toString() }
        return result
    }
}
