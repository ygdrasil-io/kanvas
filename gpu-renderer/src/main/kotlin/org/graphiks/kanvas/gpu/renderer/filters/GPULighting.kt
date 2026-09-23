package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.plan.W6dSamplingProgramV1
import org.graphiks.kanvas.gpu.plan.W6dSobelEdgeModeV1
import org.graphiks.kanvas.gpu.plan.LightingFamilyV1
import org.graphiks.kanvas.gpu.plan.LightingParametersV1

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

/** Native translation of the one frozen distant-diffuse recipe; it never receives a public filter node. */
internal object GPUW6dDistantDiffusePass {
    fun fragment(program: W6dSamplingProgramV1.DistantDiffuse): String {
        val sampling = program.sobelSampling
        val offset = sampling.copyOutputToInputOffsetTargetLocalI32()
        fun clamp(mode: W6dSobelEdgeModeV1): String = if (mode == W6dSobelEdgeModeV1.CLAMP) "true" else "false"
        return """
            @group(0) @binding(0) var w6d_distant_diffuse_source: texture_2d<f32>;
            fn w6d_distant_diffuse_alpha(coord: vec2<i32>) -> f32 {
                let extent = vec2<i32>(textureDimensions(w6d_distant_diffuse_source));
                var sampled = coord;
                if (sampled.x < 0) {
                    if (${clamp(sampling.leftMode)}) { sampled.x = 0; } else { return 0.0; }
                } else if (sampled.x >= extent.x) {
                    if (${clamp(sampling.rightMode)}) { sampled.x = extent.x - 1; } else { return 0.0; }
                }
                if (sampled.y < 0) {
                    if (${clamp(sampling.topMode)}) { sampled.y = 0; } else { return 0.0; }
                } else if (sampled.y >= extent.y) {
                    if (${clamp(sampling.bottomMode)}) { sampled.y = extent.y - 1; } else { return 0.0; }
                }
                return textureLoad(w6d_distant_diffuse_source, sampled, 0).a;
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let base = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});
                let dx = 0.25 * ((w6d_distant_diffuse_alpha(base + vec2<i32>(1, -1)) +
                    2.0 * w6d_distant_diffuse_alpha(base + vec2<i32>(1, 0)) +
                    w6d_distant_diffuse_alpha(base + vec2<i32>(1, 1))) -
                    (w6d_distant_diffuse_alpha(base + vec2<i32>(-1, -1)) +
                    2.0 * w6d_distant_diffuse_alpha(base + vec2<i32>(-1, 0)) +
                    w6d_distant_diffuse_alpha(base + vec2<i32>(-1, 1))));
                let dy = 0.25 * ((w6d_distant_diffuse_alpha(base + vec2<i32>(-1, 1)) +
                    2.0 * w6d_distant_diffuse_alpha(base + vec2<i32>(0, 1)) +
                    w6d_distant_diffuse_alpha(base + vec2<i32>(1, 1))) -
                    (w6d_distant_diffuse_alpha(base + vec2<i32>(-1, -1)) +
                    2.0 * w6d_distant_diffuse_alpha(base + vec2<i32>(0, -1)) +
                    w6d_distant_diffuse_alpha(base + vec2<i32>(1, -1))));
                let rawNormal = vec3<f32>(-${program.mappedSurfaceDepthF32}f * dx,
                    -${program.mappedSurfaceDepthF32}f * dy, 1.0);
                let normalScale = max(max(abs(rawNormal.x), abs(rawNormal.y)), abs(rawNormal.z));
                let scaledNormal = rawNormal / normalScale;
                let normal = scaledNormal / length(scaledNormal);
                let light = vec3<f32>(${program.copyDirection3F32().x}f, ${program.copyDirection3F32().y}f,
                    ${program.copyDirection3F32().z}f);
                let lightScale = max(max(abs(light.x), abs(light.y)), abs(light.z));
                var contribution = 0.0;
                if (lightScale > 0.0) {
                    let scaledLight = light / lightScale;
                    contribution = max(0.0, dot(normal, scaledLight / length(scaledLight)));
                }
                let rgb = clamp(vec3<f32>(${program.lightColor.r}f, ${program.lightColor.g}f, ${program.lightColor.b}f) *
                    ${program.kdF32}f * contribution, vec3<f32>(0.0), vec3<f32>(1.0));
                return vec4<f32>(rgb, 1.0);
            }
        """
    }
}

/** Materializes only an already-frozen point/spot/specular recipe; it never selects a family. */
internal object GPUW6dLightingPass {
    fun fragment(program: W6dSamplingProgramV1.Lighting): String {
        val sampling = program.sobelSampling
        val offset = sampling.copyOutputToInputOffsetTargetLocalI32()
        fun clamp(mode: W6dSobelEdgeModeV1): String = if (mode == W6dSobelEdgeModeV1.CLAMP) "true" else "false"
        val parameters = program.copyParameters()
        val isSpecular = program.family in setOf(LightingFamilyV1.DISTANT_SPECULAR, LightingFamilyV1.POINT_SPECULAR, LightingFamilyV1.SPOT_SPECULAR)
        val isSpot = program.family in setOf(LightingFamilyV1.SPOT_DIFFUSE, LightingFamilyV1.SPOT_SPECULAR)
        val lightVector = when (parameters) {
            is LightingParametersV1.Distant -> parameters.copyDirection3F32().let { "vec3<f32>(${it.x}f, ${it.y}f, ${it.z}f)" }
            is LightingParametersV1.Point -> parameters.copyLocation3F32().let { "vec3<f32>(${it.x}f, ${it.y}f, ${it.z}f) - surface" }
            is LightingParametersV1.Spot -> parameters.copyLocation3F32().let { "vec3<f32>(${it.x}f, ${it.y}f, ${it.z}f) - surface" }
        }
        val surfaceDepth = when (parameters) {
            is LightingParametersV1.Distant -> parameters.surfaceDepthF32
            is LightingParametersV1.Point -> parameters.surfaceScaleF32
            is LightingParametersV1.Spot -> parameters.surfaceScaleF32
        }
        val coefficient = when (parameters) {
            is LightingParametersV1.Distant -> parameters.coefficientF32
            is LightingParametersV1.Point -> parameters.coefficientF32
            is LightingParametersV1.Spot -> parameters.coefficientF32
        }
        val color = when (parameters) {
            is LightingParametersV1.Distant -> parameters.lightColor
            is LightingParametersV1.Point -> parameters.lightColor
            is LightingParametersV1.Spot -> parameters.lightColor
        }
        val shininess = when (parameters) {
            is LightingParametersV1.Distant -> parameters.shininessF32 ?: 1f
            is LightingParametersV1.Point -> parameters.shininessF32 ?: 1f
            is LightingParametersV1.Spot -> parameters.shininessF32 ?: 1f
        }
        val cone = if (isSpot) {
            val spot = parameters as LightingParametersV1.Spot
            val direction = spot.copyDirection3F32()
            """
                let spotDirection = vec3<f32>(${direction.x}f, ${direction.y}f, ${direction.z}f);
                let spotCosine = dot(-surfaceToLight, spotDirection);
                var cone = 0.0;
                if (spotCosine >= 0.0 && spotCosine >= ${spot.cutoffCosineF32}f) {
                    let powered = pow(spotCosine, ${spot.specularExponentF32}f);
                    if (powered == powered && abs(powered) <= 3.402823e38) {
                        cone = powered * clamp((spotCosine - ${spot.cutoffCosineF32}f) / 0.016, 0.0, 1.0);
                    }
                }
            """
        } else "let cone = 1.0;"
        val contribution = if (isSpecular) """
            let halfVector = w6d_normalize_or_zero(surfaceToLight + vec3<f32>(0.0, 0.0, 1.0));
            let halfLength = length(halfVector);
            let halfDot = dot(normal, halfVector);
            var contribution = 0.0;
            if (halfLength > 0.0 && halfDot >= 0.0 && !(halfDot == 0.0 && ${shininess}f < 0.0)) {
                let powered = pow(halfDot, ${shininess}f);
                if (powered == powered) { contribution = powered * cone; }
            }
        """ else """
            var contribution = max(0.0, dot(normal, surfaceToLight)) * cone;
        """
        val alpha = if (isSpecular) "clamp(max(max(${color.r}f, ${color.g}f), ${color.b}f) * ${coefficient}f * contribution, 0.0, 1.0)" else "1.0"
        return """
            @group(0) @binding(0) var w6d_lighting_source: texture_2d<f32>;
            fn w6d_lighting_alpha(coord: vec2<i32>) -> f32 {
                let extent = vec2<i32>(textureDimensions(w6d_lighting_source));
                var sampled = coord;
                if (sampled.x < 0) { if (${clamp(sampling.leftMode)}) { sampled.x = 0; } else { return 0.0; } }
                else if (sampled.x >= extent.x) { if (${clamp(sampling.rightMode)}) { sampled.x = extent.x - 1; } else { return 0.0; } }
                if (sampled.y < 0) { if (${clamp(sampling.topMode)}) { sampled.y = 0; } else { return 0.0; } }
                else if (sampled.y >= extent.y) { if (${clamp(sampling.bottomMode)}) { sampled.y = extent.y - 1; } else { return 0.0; } }
                return textureLoad(w6d_lighting_source, sampled, 0).a;
            }
            fn w6d_normalize_or_zero(value: vec3<f32>) -> vec3<f32> {
                let scale = max(max(abs(value.x), abs(value.y)), abs(value.z));
                if (scale == 0.0 || scale != scale) { return vec3<f32>(0.0); }
                let scaled = value / scale;
                return scaled / length(scaled);
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let base = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});
                let dx = 0.25 * ((w6d_lighting_alpha(base + vec2<i32>(1, -1)) + 2.0 * w6d_lighting_alpha(base + vec2<i32>(1, 0)) + w6d_lighting_alpha(base + vec2<i32>(1, 1))) - (w6d_lighting_alpha(base + vec2<i32>(-1, -1)) + 2.0 * w6d_lighting_alpha(base + vec2<i32>(-1, 0)) + w6d_lighting_alpha(base + vec2<i32>(-1, 1))));
                let dy = 0.25 * ((w6d_lighting_alpha(base + vec2<i32>(-1, 1)) + 2.0 * w6d_lighting_alpha(base + vec2<i32>(0, 1)) + w6d_lighting_alpha(base + vec2<i32>(1, 1))) - (w6d_lighting_alpha(base + vec2<i32>(-1, -1)) + 2.0 * w6d_lighting_alpha(base + vec2<i32>(0, -1)) + w6d_lighting_alpha(base + vec2<i32>(1, -1))));
                let normal = w6d_normalize_or_zero(vec3<f32>(-${surfaceDepth}f * dx, -${surfaceDepth}f * dy, 1.0));
                let surface = vec3<f32>(position.xy, w6d_lighting_alpha(base) * ${surfaceDepth}f);
                let surfaceToLight = w6d_normalize_or_zero($lightVector);
                $cone
                $contribution
                let rgb = clamp(vec3<f32>(${color.r}f, ${color.g}f, ${color.b}f) * ${coefficient}f * contribution, vec3<f32>(0.0), vec3<f32>(1.0));
                return vec4<f32>(rgb, $alpha);
            }
        """
    }
}

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
