package org.graphiks.kanvas.gpu.renderer.transform

import kotlin.math.abs
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class GeometryKind { Rect, RRect, Path, Text }
enum class MaterialKind { SolidColor, Gradient }

/** Local rectangle in source coordinates used for corner projection and projected bounds. */
data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Coarse perspective route acceptance state from the coordinate-transform-bounds policy. */
enum class GPUPerspectiveRouteAcceptance {
    Accepted,
    RefusedAffineOnly,
    RefusedDegenerate,
}

/**
 * 4x4 perspective matrix classification facts.
 *
 * [matrix] is a 16-element row-major homogeneous transform. [finiteDeterminant] is true only when
 * the determinant is finite and not near-zero (a usable, non-degenerate transform). [wDivideSign]
 * is `+1` when every projected corner is in front of the camera, `-1` when every corner is behind,
 * and `0` when the geometry crosses the `w = 0` plane.
 */
data class GPUPerspectiveTransformPlan(
    val matrix: FloatArray,
    val finiteDeterminant: Boolean,
    val wDivideSign: Float,
) {
    fun dump(): String =
        "GPUPerspectiveTransformPlan(matrix=${matrix.joinToString(prefix = "[", postfix = "]")}, " +
            "finiteDeterminant=$finiteDeterminant, wDivideSign=$wDivideSign)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUPerspectiveTransformPlan) return false
        return matrix.contentEquals(other.matrix) &&
            finiteDeterminant == other.finiteDeterminant &&
            wDivideSign == other.wDivideSign
    }

    override fun hashCode(): Int {
        var result = matrix.contentHashCode()
        result = 31 * result + finiteDeterminant.hashCode()
        result = 31 * result + wDivideSign.hashCode()
        return result
    }
}

/**
 * Conservative 2D projected device bounds for a perspective-transformed planar geometry payload.
 *
 * [projectedBounds] is `null` when projection is not trustworthy (behind-camera or non-finite).
 * [allCornersFinite] is true only when every projected corner is finite. [behindCamera] is true
 * when any corner has `w <= 0`.
 */
data class GPUPerspectiveBoundsProof(
    val projectedBounds: Rect?,
    val allCornersFinite: Boolean,
    val behindCamera: Boolean,
) {
    fun dump(): String =
        "GPUPerspectiveBoundsProof(projectedBounds=$projectedBounds, " +
            "allCornersFinite=$allCornersFinite, behindCamera=$behindCamera)"
}

sealed interface PerspectiveTransformRoute {
    data class Accepted(
        val transformKind: String,
        val transformPlan: GPUPerspectiveTransformPlan,
        val boundsProof: GPUPerspectiveBoundsProof,
        val acceptance: GPUPerspectiveRouteAcceptance = GPUPerspectiveRouteAcceptance.Accepted,
    ) : PerspectiveTransformRoute

    data class Refused(
        val diagnostic: RefuseDiagnostic,
        val acceptance: GPUPerspectiveRouteAcceptance,
    ) : PerspectiveTransformRoute
}

data class PerspectiveTransformPlan(
    val geometry: GeometryKind,
    val material: MaterialKind,
    val matrix: FloatArray,
    val sourceBounds: Rect,
) {
    companion object {
        private const val DETERMINANT_EPSILON = 1e-6f

        private val IDENTITY_4X4 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )

        private val UNIT_RECT = Rect(0f, 0f, 1f, 1f)

        fun forGeometry(
            geometry: GeometryKind,
            material: MaterialKind,
            matrix: FloatArray = IDENTITY_4X4,
            sourceBounds: Rect = UNIT_RECT,
        ): PerspectiveTransformPlan =
            PerspectiveTransformPlan(geometry, material, matrix.copyOf(), sourceBounds)

        private fun determinant4x4(m: FloatArray): Float {
            val s0 = m[0] * m[5] - m[1] * m[4]
            val s1 = m[0] * m[6] - m[2] * m[4]
            val s2 = m[0] * m[7] - m[3] * m[4]
            val s3 = m[1] * m[6] - m[2] * m[5]
            val s4 = m[1] * m[7] - m[3] * m[5]
            val s5 = m[2] * m[7] - m[3] * m[6]

            val c5 = m[10] * m[15] - m[11] * m[14]
            val c4 = m[9] * m[15] - m[11] * m[13]
            val c3 = m[9] * m[14] - m[10] * m[13]
            val c2 = m[8] * m[15] - m[11] * m[12]
            val c1 = m[8] * m[14] - m[10] * m[12]
            val c0 = m[8] * m[13] - m[9] * m[12]

            return s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0
        }
    }

    private fun corners(): List<Pair<Float, Float>> = listOf(
        sourceBounds.left to sourceBounds.top,
        sourceBounds.right to sourceBounds.top,
        sourceBounds.right to sourceBounds.bottom,
        sourceBounds.left to sourceBounds.bottom,
    )

    /** Build the 4x4 classification facts: usable determinant and homogeneous-divide sign. */
    fun transformPlan(): GPUPerspectiveTransformPlan {
        val det = determinant4x4(matrix)
        val finiteDeterminant = det.isFinite() && abs(det) >= DETERMINANT_EPSILON

        var positive = 0
        var nonPositive = 0
        for ((x, y) in corners()) {
            val w = matrix[12] * x + matrix[13] * y + matrix[15]
            if (w > 0f) positive++ else nonPositive++
        }
        val wDivideSign = when {
            nonPositive == 0 -> 1f
            positive == 0 -> -1f
            else -> 0f
        }
        return GPUPerspectiveTransformPlan(matrix.copyOf(), finiteDeterminant, wDivideSign)
    }

    /** Project the four source-rect corners through the matrix to conservative device bounds. */
    fun boundsProof(): GPUPerspectiveBoundsProof {
        var behindCamera = false
        var allCornersFinite = true
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for ((x, y) in corners()) {
            val px = matrix[0] * x + matrix[1] * y + matrix[3]
            val py = matrix[4] * x + matrix[5] * y + matrix[7]
            val pw = matrix[12] * x + matrix[13] * y + matrix[15]
            if (pw <= 0f) behindCamera = true
            val dx = px / pw
            val dy = py / pw
            if (!dx.isFinite() || !dy.isFinite()) {
                allCornersFinite = false
            } else {
                if (dx < minX) minX = dx
                if (dy < minY) minY = dy
                if (dx > maxX) maxX = dx
                if (dy > maxY) maxY = dy
            }
        }

        val projectedBounds = if (allCornersFinite && !behindCamera) {
            Rect(minX, minY, maxX, maxY)
        } else {
            null
        }
        return GPUPerspectiveBoundsProof(projectedBounds, allCornersFinite, behindCamera)
    }

    fun analyze(): PerspectiveTransformRoute {
        when (geometry) {
            GeometryKind.Path -> return refuseAffineOnly("path", "path geometry requires curve reprojection")
            GeometryKind.Text -> return refuseAffineOnly("text", "text geometry requires affine-only coordinates")
            else -> Unit
        }
        if (material != MaterialKind.SolidColor) {
            return refuseAffineOnly("gradient", "non-solid material requires affine-only coordinates")
        }

        val plan = transformPlan()
        if (!plan.finiteDeterminant) {
            return refuseDegenerate("near-zero or non-finite perspective determinant")
        }

        val proof = boundsProof()
        if (proof.behindCamera) {
            return refuseDegenerate("behind-camera geometry (w <= 0 for a projected corner)")
        }
        if (!proof.allCornersFinite || proof.projectedBounds == null) {
            return refuseDegenerate("unbounded perspective projection")
        }

        return PerspectiveTransformRoute.Accepted(
            transformKind = "perspective-${geometry.name.lowercase()}",
            transformPlan = plan,
            boundsProof = proof,
        )
    }

    private fun refuseAffineOnly(name: String, reason: String): PerspectiveTransformRoute.Refused =
        PerspectiveTransformRoute.Refused(
            RefuseDiagnostic(
                code = "unsupported.transform.perspective_route_rejected.$name",
                message = "perspective route rejected: $reason",
                stage = "perspective.analysis",
                terminal = true,
            ),
            acceptance = GPUPerspectiveRouteAcceptance.RefusedAffineOnly,
        )

    private fun refuseDegenerate(reason: String): PerspectiveTransformRoute.Refused =
        PerspectiveTransformRoute.Refused(
            RefuseDiagnostic(
                code = "unsupported.transform.perspective_degenerate",
                message = "perspective transform degenerate: $reason",
                stage = "perspective.analysis",
                terminal = true,
            ),
            acceptance = GPUPerspectiveRouteAcceptance.RefusedDegenerate,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PerspectiveTransformPlan) return false
        return geometry == other.geometry &&
            material == other.material &&
            matrix.contentEquals(other.matrix) &&
            sourceBounds == other.sourceBounds
    }

    override fun hashCode(): Int {
        var result = geometry.hashCode()
        result = 31 * result + material.hashCode()
        result = 31 * result + matrix.contentHashCode()
        result = 31 * result + sourceBounds.hashCode()
        return result
    }
}
