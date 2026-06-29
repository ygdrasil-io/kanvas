package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class GPUWideGamutPrimaries { DisplayP3, AdobeRGB, Rec2020 }

enum class GPUWideGamutIntermediateFormat(val descriptor: String) {
    rgba16float("rgba16float"),
    rgba32float("rgba32float"),
}

data class GPUWideGamutWorkingSpacePlan(
    val primaries: GPUWideGamutPrimaries,
    val transferFunction: GPUHdrTransferFunction? = null,
    val intermediateFormat: GPUWideGamutIntermediateFormat = GPUWideGamutIntermediateFormat.rgba16float,
) {
    companion object {
        fun forPrimaries(primaries: GPUWideGamutPrimaries): GPUWideGamutWorkingSpacePlan =
            GPUWideGamutWorkingSpacePlan(primaries = primaries)

        private val supportedPrimaries: Set<GPUWideGamutPrimaries> =
            GPUWideGamutPrimaries.entries.toSet()

        fun conversionMatrixToSrgb(primaries: GPUWideGamutPrimaries): FloatArray = when (primaries) {
            GPUWideGamutPrimaries.DisplayP3 -> floatArrayOf(
                1.2249f, -0.2247f, 0.0000f,
                -0.0421f, 1.0421f, 0.0000f,
                -0.0196f, -0.0786f, 1.0983f,
            )
            GPUWideGamutPrimaries.AdobeRGB -> floatArrayOf(
                0.7151f, 0.2849f, 0.0000f,
                0.0000f, 1.0000f, 0.0000f,
                0.0000f, 0.0412f, 0.9588f,
            )
            GPUWideGamutPrimaries.Rec2020 -> floatArrayOf(
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

    fun srgbConversion(): GPUWideGamutConversionPlan {
        val matrix = conversionMatrixToSrgb(primaries)
        return GPUWideGamutConversionPlan(
            source = this,
            destination = GPUWideGamutWorkingSpacePlan.forPrimaries(primaries),
            matrix = matrix,
            transferConversion = null,
            wgslSource = generateConversionShader(matrix),
        )
    }

    fun analyze(): GPUWideGamutRoute {
        if (primaries !in supportedPrimaries) {
            return GPUWideGamutRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.wide_gamut_working_space",
                    message = "unsupported wide-gamut primaries: $primaries",
                    stage = "color.analysis",
                    terminal = true,
                )
            )
        }
        if (transferFunction != null) {
            return GPUWideGamutRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.wide_gamut_working_space",
                    message = "unsupported transfer function for wide-gamut: $transferFunction",
                    stage = "color.analysis",
                    terminal = true,
                )
            )
        }
        val conversion = srgbConversion()
        return GPUWideGamutRoute.Accepted(
            workingSpace = this,
            conversion = conversion,
            intermediateFormat = intermediateFormat,
        )
    }
}

data class GPUWideGamutConversionPlan(
    val source: GPUWideGamutWorkingSpacePlan,
    val destination: GPUWideGamutWorkingSpacePlan,
    val matrix: FloatArray,
    val transferConversion: GPUHdrTransferFunctionPlan?,
    val wgslSource: String,
)

sealed interface GPUWideGamutRoute {
    data class Accepted(
        val workingSpace: GPUWideGamutWorkingSpacePlan,
        val conversion: GPUWideGamutConversionPlan,
        val intermediateFormat: GPUWideGamutIntermediateFormat,
    ) : GPUWideGamutRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUWideGamutRoute
}
