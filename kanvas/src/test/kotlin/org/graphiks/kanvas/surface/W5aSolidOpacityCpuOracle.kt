package org.graphiks.kanvas.surface

import kotlin.math.pow
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32

/** Independent public-pixel fixture: it feeds the sealed W5a DAG, never renderer helpers. */
internal object W5aSolidOpacityCpuOracle {
    fun draw(
        color: ColorARGB,
        shaderOpacityOuterF32: Float,
        shaderOpacityInnerF32: Float = 1f,
        paintAlphaF32: Float = 1f,
        destination: WgslFloatEnvelopeV1Oracle.AttachmentState = WgslFloatEnvelopeV1Oracle.clearAttachment(),
        coverageF32: Float = 1f,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        val shaderAlpha = shaderOpacityInnerF32 * shaderOpacityOuterF32
        if (shaderAlpha == 0f || paintAlphaF32 == 0f) {
            return WgslFloatEnvelopeV1Oracle.draw(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0),
                destination,
                coverageF32,
            )
        }
        val entries = mutableListOf(
            MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(
                    ColorF32.of(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized),
                ),
            ),
        )
        if (shaderAlpha != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(entries.last().program),
                MaterialBindingPlan.OpacityF32V1.of(shaderAlpha),
            )
        }
        if (paintAlphaF32 != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(entries.last().program),
                MaterialBindingPlan.OpacityF32V1.of(paintAlphaF32),
            )
        }
        return WgslFloatEnvelopeV1Oracle.draw(
            MaterialPlanTable.of(entries),
            MaterialPlanRef(entries.lastIndex),
            destination,
            coverageF32,
        )
    }

    /**
     * Independent vertices oracle for the documented fragment order:
     * `materialLinearPremul * decodedVertexColor`. The public color is first converted to the
     * canonical premultiplied RGBA8 vertex representation, then decoded by the fragment.
     */
    fun drawVertexColorModulated(
        color: ColorARGB,
        vertexColor: ColorARGB,
        shaderOpacityOuterF32: Float,
        shaderOpacityInnerF32: Float = 1f,
        paintAlphaF32: Float = 1f,
        destination: WgslFloatEnvelopeV1Oracle.AttachmentState = WgslFloatEnvelopeV1Oracle.clearAttachment(),
        coverageF32: Float = 1f,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        val packedVertex = canonicalPremultipliedVertexColor(vertexColor)
        val materialTimesVertex = ColorF32.of(
            linearToSrgb(srgbToLinear(color.redNormalized) * srgbToLinear(packedVertex.red)),
            linearToSrgb(srgbToLinear(color.greenNormalized) * srgbToLinear(packedVertex.green)),
            linearToSrgb(srgbToLinear(color.blueNormalized) * srgbToLinear(packedVertex.blue)),
            color.alphaNormalized * packedVertex.alpha,
        )
        val shaderAlpha = shaderOpacityInnerF32 * shaderOpacityOuterF32
        val entries = mutableListOf(
            MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(materialTimesVertex),
            ),
        )
        if (shaderAlpha != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(entries.last().program),
                MaterialBindingPlan.OpacityF32V1.of(shaderAlpha),
            )
        }
        if (paintAlphaF32 != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(entries.last().program),
                MaterialBindingPlan.OpacityF32V1.of(paintAlphaF32),
            )
        }
        return WgslFloatEnvelopeV1Oracle.draw(
            MaterialPlanTable.of(entries),
            MaterialPlanRef(entries.lastIndex),
            destination,
            coverageF32,
        )
    }

    private fun srgbToLinear(value: Float): Float = if (value <= 0.04045f) {
        value / 12.92f
    } else {
        ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    private fun linearToSrgb(value: Float): Float = if (value <= 0.0031308f) {
        value * 12.92f
    } else {
        (1.055 * value.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
    }

    private fun canonicalPremultipliedVertexColor(color: ColorARGB): ColorF32 {
        val alpha = (color.alphaNormalized * 255f).toInt()
        fun channel(value: Float): Float {
            val unorm = (value * 255f).toInt()
            return ((unorm * alpha + 127) / 255) / 255f
        }
        return ColorF32.of(channel(color.redNormalized), channel(color.greenNormalized), channel(color.blueNormalized), alpha / 255f)
    }
}
