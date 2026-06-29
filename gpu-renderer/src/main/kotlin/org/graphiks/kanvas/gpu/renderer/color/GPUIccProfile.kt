package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import java.security.MessageDigest

enum class GPUIccVersion { v2, v4, v5 }

data class GPUIccHeader(
    val profileSize: UInt,
    val preferredCmm: String,
    val profileVersion: String,
    val deviceClass: String,
    val colorSpace: String,
    val pcs: String,
)

data class GPUIccTag(
    val signature: String,
    val offset: UInt,
    val size: UInt,
)

data class GPUIccMatrixTrc(
    val matrix: FloatArray,
    val trc: List<FloatArray>,
)

data class GPUIccProfileParsePlan(
    val version: GPUIccVersion,
    val header: GPUIccHeader,
    val tagTable: List<GPUIccTag>,
    val matrixTrc: GPUIccMatrixTrc?,
) {
    companion object {
        fun parse(
            version: GPUIccVersion,
            header: GPUIccHeader,
            tagTable: List<GPUIccTag>,
            hasMatrixTrc: Boolean,
        ): GPUIccProfileParsePlan {
            val matrixTrc = if (hasMatrixTrc) {
                GPUIccMatrixTrc(
                    matrix = FloatArray(9) { 1f },
                    trc = listOf(FloatArray(256) { it.toFloat() / 255f }),
                )
            } else null
            return GPUIccProfileParsePlan(version, header, tagTable, matrixTrc)
        }

        fun generateTransformShader(trc: GPUIccMatrixTrc): String = """
            fn icc_matrix_trc_transform(color: vec3<f32>) -> vec3<f32> {
                return color;
            }
        """.trimIndent()
    }

    fun analyze(): GPUIccProfileRoute {
        if (version == GPUIccVersion.v5) {
            return GPUIccProfileRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.icc_profile_version",
                    message = "ICC v5 profiles are not supported",
                    stage = "icc.parse",
                    terminal = true,
                )
            )
        }
        if (matrixTrc == null) {
            return GPUIccProfileRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.icc_lut_profile",
                    message = "ICC LUT profiles (A2B0/B2A0) are not supported",
                    stage = "icc.parse",
                    terminal = true,
                )
            )
        }
        if (tagTable.isEmpty()) {
            return GPUIccProfileRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.color.icc_parse_failure",
                    message = "ICC profile parse failure: empty tag table",
                    stage = "icc.parse",
                    terminal = true,
                )
            )
        }
        val transform = GPUIccProfileTransformPlan(
            parsePlan = this,
            matrixTrc = matrixTrc,
            wgslSource = generateTransformShader(matrixTrc),
        )
        val cache = GPUIccProfileCachePlan(
            cacheKey = GPUIccProfileCachePlan.computeCacheKey(header.toString().encodeToByteArray()),
            parsedPlan = this,
            transformPlan = transform,
        )
        return GPUIccProfileRoute.Accepted(
            parse = this,
            transform = transform,
            cache = cache,
        )
    }
}

data class GPUIccProfileTransformPlan(
    val parsePlan: GPUIccProfileParsePlan,
    val matrixTrc: GPUIccMatrixTrc,
    val wgslSource: String,
)

data class GPUIccProfileCachePlan(
    val cacheKey: String,
    val parsedPlan: GPUIccProfileParsePlan?,
    val transformPlan: GPUIccProfileTransformPlan?,
) {
    companion object {
        fun computeCacheKey(profileBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(profileBytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

sealed interface GPUIccProfileRoute {
    data class Accepted(
        val parse: GPUIccProfileParsePlan,
        val transform: GPUIccProfileTransformPlan,
        val cache: GPUIccProfileCachePlan,
    ) : GPUIccProfileRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUIccProfileRoute
}
