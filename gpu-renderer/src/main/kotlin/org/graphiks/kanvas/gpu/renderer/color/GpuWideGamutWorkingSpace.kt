package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class GpuWideGamutPrimaries { DisplayP3, AdobeRGB, Rec2020 }

enum class GpuWideGamutIntermediateFormat(val descriptor: String) {
    rgba16float("rgba16float"),
    rgba32float("rgba32float"),
}

data class GpuWideGamutWorkingSpacePlan(
    val primaries: GpuWideGamutPrimaries,
    val transferFunction: String = "sRGB",
    val intermediateFormat: GpuWideGamutIntermediateFormat = GpuWideGamutIntermediateFormat.rgba16float,
) {
    companion object {
        fun forPrimaries(primaries: GpuWideGamutPrimaries): GpuWideGamutWorkingSpacePlan =
            GpuWideGamutWorkingSpacePlan(primaries = primaries)

        fun conversionMatrixToSrgb(primaries: GpuWideGamutPrimaries): FloatArray = when (primaries) {
            GpuWideGamutPrimaries.DisplayP3 -> floatArrayOf(
                1.2249f, -0.2247f, 0.0000f,
                -0.0421f, 1.0421f, 0.0000f,
                -0.0196f, -0.0786f, 1.0983f,
            )
            GpuWideGamutPrimaries.AdobeRGB -> floatArrayOf(
                0.7151f, 0.2849f, 0.0000f,
                0.0000f, 1.0000f, 0.0000f,
                0.0000f, 0.0412f, 0.9588f,
            )
            GpuWideGamutPrimaries.Rec2020 -> floatArrayOf(
                1.6605f, -0.5876f, -0.0728f,
                -0.1246f, 1.1329f, -0.0083f,
                -0.0182f, -0.1006f, 1.1187f,
            )
        }

        fun generateConversionShader(matrix: FloatArray): String = """
            fn wide_gamut_convert(color: vec3<f32>) -> vec3<f32> {
                let m = mat3x3<f32>(
                    vec3<f32>(${matrix[0]}, ${matrix[1]}, ${matrix[2]}),
                    vec3<f32>(${matrix[3]}, ${matrix[4]}, ${matrix[5]}),
                    vec3<f32>(${matrix[6]}, ${matrix[7]}, ${matrix[8]}),
                );
                return m * color;
            }
        """.trimIndent()
    }

    private val supportedTransferFunctions = setOf("sRGB", "Linear")

    fun srgbConversion(): GpuWideGamutConversionPlan {
        val matrix = conversionMatrixToSrgb(primaries)
        return GpuWideGamutConversionPlan(
            source = this,
            destination = GpuWideGamutWorkingSpacePlan.forPrimaries(primaries),
            matrix = matrix,
            transferConversion = "srgb_to_linear",
            wgslSource = generateConversionShader(matrix),
        )
    }

    fun analyze(): GpuWideGamutRoute {
        if (transferFunction !in supportedTransferFunctions) {
            return GpuWideGamutRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.wide_gamut_transfer",
                    message = "unsupported transfer function: $transferFunction",
                    stage = "color.analysis",
                    terminal = true,
                )
            )
        }
        val conversion = srgbConversion()
        return GpuWideGamutRoute.Accepted(
            GpuWideGamutRoute.Accepted.AcceptedData(
                workingSpace = this,
                conversion = conversion,
                intermediateFormat = intermediateFormat,
            )
        )
    }
}

data class GpuWideGamutConversionPlan(
    val source: GpuWideGamutWorkingSpacePlan,
    val destination: GpuWideGamutWorkingSpacePlan,
    val matrix: FloatArray,
    val transferConversion: String,
    val wgslSource: String,
)

sealed interface GpuWideGamutRoute {
    data class Accepted(
        val workingSpace: GpuWideGamutWorkingSpacePlan,
        val conversion: GpuWideGamutConversionPlan,
        val intermediateFormat: GpuWideGamutIntermediateFormat,
    ) : GpuWideGamutRoute {
        data class AcceptedData(
            val workingSpace: GpuWideGamutWorkingSpacePlan,
            val conversion: GpuWideGamutConversionPlan,
            val intermediateFormat: GpuWideGamutIntermediateFormat,
        )
        constructor(data: AcceptedData) : this(data.workingSpace, data.conversion, data.intermediateFormat)
    }
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuWideGamutRoute
}
