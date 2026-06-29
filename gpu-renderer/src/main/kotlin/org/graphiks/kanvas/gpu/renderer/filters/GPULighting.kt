package org.graphiks.kanvas.gpu.renderer.filters

/** RGBA color with normalized [0,1] float components. */
data class GPUColor(val r: Float, val g: Float, val b: Float, val a: Float)

enum class GPULightType { Directional, Point, Spot, Specular }

enum class GPULightingNormalSource { BumpAlpha, NormalMap }

data class GPUAttenuation(val constant: Float, val linear: Float, val quadratic: Float)

data class GPULightingPlan(
    val type: GPULightType,
    val direction: FloatArray?, // directional light direction (normalized) [x, y, z]
    val position: FloatArray?, // point/spot light position in surface space [x, y, z]
    val surfaceScale: Float,
    val lightColor: GPUColor,
    val ambientColor: GPUColor,
    val specularExponent: Float,
    val attenuation: GPUAttenuation?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPULightingPlan) return false
        return type == other.type &&
            direction.contentEquals(other.direction) &&
            position.contentEquals(other.position) &&
            surfaceScale == other.surfaceScale &&
            lightColor == other.lightColor &&
            ambientColor == other.ambientColor &&
            specularExponent == other.specularExponent &&
            attenuation == other.attenuation
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (direction?.contentHashCode() ?: 0)
        result = 31 * result + (position?.contentHashCode() ?: 0)
        result = 31 * result + surfaceScale.hashCode()
        result = 31 * result + lightColor.hashCode()
        result = 31 * result + ambientColor.hashCode()
        result = 31 * result + specularExponent.hashCode()
        result = 31 * result + (attenuation?.hashCode() ?: 0)
        return result
    }
}

data class GPULightingNormalMapPlan(
    val normalSource: GPULightingNormalSource,
    val sourceBinding: String,
    val normalMapBinding: String?,
)

data class GPULightingResult(
    val accepted: Boolean,
    val diagnosticCode: String?,
    val diagnosticMessage: String?,
)

class GPULightingFilter {
    fun execute(
        plan: GPULightingPlan,
        normalMapPlan: GPULightingNormalMapPlan,
        sourceFormat: String? = null,
    ): GPULightingResult {
        if (plan.specularExponent < 1f || plan.specularExponent > 128f) {
            return GPULightingResult(
                accepted = false,
                diagnosticCode = "unsupported.filter.lighting_specular_exponent_out_of_range",
                diagnosticMessage = "Specular exponent must be in [1, 128], got ${plan.specularExponent}",
            )
        }

        if (plan.surfaceScale < 0f) {
            return GPULightingResult(
                accepted = false,
                diagnosticCode = "unsupported.filter.lighting_surface_scale_negative",
                diagnosticMessage = "Surface scale must be non-negative, got ${plan.surfaceScale}",
            )
        }

        if (sourceFormat != null && sourceFormat.isNotBlank() && sourceFormat !in setOf("rgba8", "bgra8")) {
            return GPULightingResult(
                accepted = false,
                diagnosticCode = "unsupported.filter.lighting_source_format",
                diagnosticMessage = "Unsupported source texture format: $sourceFormat",
            )
        }

        val refusal = unsupportedTypeRefusal(plan.type)
        if (refusal != null) return refusal

        val normalResult = GPULightingNormalSourceValidator.validate(normalMapPlan)
        if (!normalResult.accepted) return normalResult

        val acceptedType = aceptableType(plan.type)
        if (acceptedType != null) return acceptedType

        return GPULightingResult(
            accepted = false,
            diagnosticCode = "unsupported.filter.lighting_type_unknown",
            diagnosticMessage = "Unknown light type: ${plan.type}",
        )
    }

    private fun unsupportedTypeRefusal(type: GPULightType): GPULightingResult? = when (type) {
        GPULightType.Point -> GPULightingResult(
            accepted = false,
            diagnosticCode = "unsupported.filter.lighting_type_unsupported",
            diagnosticMessage = "Point light type is not yet supported; deferred with stable refusal.",
        )
        GPULightType.Spot -> GPULightingResult(
            accepted = false,
            diagnosticCode = "unsupported.filter.lighting_type_unsupported",
            diagnosticMessage = "Spot light type is not yet supported; deferred with stable refusal.",
        )
        else -> null
    }

    private fun aceptableType(type: GPULightType): GPULightingResult? = when (type) {
        GPULightType.Directional -> GPULightingResult(
            accepted = true,
            diagnosticCode = "accepted.filter.lighting_directional",
            diagnosticMessage = "Directional lighting plan accepted.",
        )
        GPULightType.Specular -> GPULightingResult(
            accepted = true,
            diagnosticCode = "accepted.filter.lighting_specular",
            diagnosticMessage = "Specular lighting plan accepted.",
        )
        else -> null
    }
}

object GPULightingNormalSourceValidator {
    fun validate(
        normalMapPlan: GPULightingNormalMapPlan,
    ): GPULightingResult {
        val missingRefusal = missingNormalSourceRefusal(normalMapPlan)
        if (missingRefusal != null) return missingRefusal

        return GPULightingResult(
            accepted = true,
            diagnosticCode = "accepted.filter.lighting_normal_source_valid",
            diagnosticMessage = "Normal source ${normalMapPlan.normalSource} is valid.",
        )
    }

    private fun missingNormalSourceRefusal(
        normalMapPlan: GPULightingNormalMapPlan,
    ): GPULightingResult? {
        if (normalMapPlan.sourceBinding.isBlank()) {
            return GPULightingResult(
                accepted = false,
                diagnosticCode = "unsupported.filter.lighting_normal_source_missing",
                diagnosticMessage = "Source binding is missing or blank; normal source unavailable.",
            )
        }
        if (normalMapPlan.normalSource == GPULightingNormalSource.NormalMap &&
            normalMapPlan.normalMapBinding == null
        ) {
            return GPULightingResult(
                accepted = false,
                diagnosticCode = "unsupported.filter.lighting_normal_source_missing",
                diagnosticMessage = "NormalMap source requires a non-null normalMapBinding.",
            )
        }
        return null
    }
}
