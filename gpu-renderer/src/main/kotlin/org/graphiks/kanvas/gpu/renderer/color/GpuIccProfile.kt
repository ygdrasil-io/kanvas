package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import java.security.MessageDigest

enum class GpuIccVersion { v2, v4, v5 }

data class GpuIccHeader(
    val profileSize: UInt,
    val preferredCmm: String,
    val profileVersion: String,
    val deviceClass: String,
    val colorSpace: String,
    val pcs: String,
)

data class GpuIccTag(
    val signature: String,
    val offset: UInt,
    val size: UInt,
)

data class GpuIccMatrixTrc(
    val matrix: FloatArray,
    val trc: List<FloatArray>,
)

data class GpuIccProfileParsePlan(
    val version: GpuIccVersion,
    val header: GpuIccHeader,
    val tagTable: List<GpuIccTag>,
    val matrixTrc: GpuIccMatrixTrc?,
) {
    companion object {
        fun parse(
            version: GpuIccVersion,
            header: GpuIccHeader,
            tagTable: List<GpuIccTag>,
            hasMatrixTrc: Boolean,
        ): GpuIccProfileParsePlan {
            val matrixTrc = if (hasMatrixTrc) {
                GpuIccMatrixTrc(
                    matrix = FloatArray(9) { 1f },
                    trc = listOf(FloatArray(256) { it.toFloat() / 255f }),
                )
            } else null
            return GpuIccProfileParsePlan(version, header, tagTable, matrixTrc)
        }

        fun generateTransformShader(trc: GpuIccMatrixTrc): String = """
            fn icc_matrix_trc_transform(color: vec3<f32>) -> vec3<f32> {
                return color;
            }
        """.trimIndent()
    }

    fun analyze(): GpuIccProfileRoute {
        if (version == GpuIccVersion.v5) {
            return GpuIccProfileRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.icc_profile_version",
                    message = "ICC v5 profiles are not supported",
                    stage = "icc.parse",
                    terminal = true,
                )
            )
        }
        if (matrixTrc == null) {
            return GpuIccProfileRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.icc_lut_profile",
                    message = "ICC LUT profiles (A2B0/B2A0) are not supported",
                    stage = "icc.parse",
                    terminal = true,
                )
            )
        }
        if (tagTable.isEmpty()) {
            return GpuIccProfileRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.icc_parse_failure",
                    message = "ICC profile parse failure: empty tag table",
                    stage = "icc.parse",
                    terminal = true,
                )
            )
        }
        val transform = GpuIccProfileTransformPlan(
            parsePlan = this,
            matrixTrc = matrixTrc,
            wgslSource = generateTransformShader(matrixTrc),
        )
        val cache = GpuIccProfileCachePlan(
            cacheKey = GpuIccProfileCachePlan.computeCacheKey(header.toString().encodeToByteArray()),
            parsedPlan = this,
            transformPlan = transform,
        )
        return GpuIccProfileRoute.Accepted(
            parse = this,
            transform = transform,
            cache = cache,
        )
    }
}

data class GpuIccProfileTransformPlan(
    val parsePlan: GpuIccProfileParsePlan,
    val matrixTrc: GpuIccMatrixTrc,
    val wgslSource: String,
)

data class GpuIccProfileCachePlan(
    val cacheKey: String,
    val parsedPlan: GpuIccProfileParsePlan?,
    val transformPlan: GpuIccProfileTransformPlan?,
) {
    companion object {
        fun computeCacheKey(profileBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(profileBytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

sealed interface GpuIccProfileRoute {
    data class Accepted(
        val parse: GpuIccProfileParsePlan,
        val transform: GpuIccProfileTransformPlan,
        val cache: GpuIccProfileCachePlan,
    ) : GpuIccProfileRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuIccProfileRoute
}
