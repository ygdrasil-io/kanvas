package org.graphiks.kanvas.gpu.renderer.geometry

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

/** Closed first-slice input for one centered, axis-aligned rectangular stroke. */
data class GPUAxisAlignedStrokeRectLoweringRequest(
    val targetBounds: GPUPixelBounds,
    val pathBounds: GPUPixelBounds,
    val strokeWidth: Float,
    val pathKey: String,
    val provenance: String,
    val cap: String = "Butt",
    val join: String = "Miter",
    val miter: Float = 4f,
    val transformClass: String = "identity",
)

sealed interface GPUAxisAlignedStrokeRectLoweringResult {
    data class Lowered(
        val geometryPlan: GPUGeometryPlan,
        val outerBounds: GPUPixelBounds,
        val innerBounds: GPUPixelBounds,
        val coverageBands: List<GPUPixelBounds>,
    ) : GPUAxisAlignedStrokeRectLoweringResult

    data class Refused(val diagnostic: GPUGeometryDiagnostic) : GPUAxisAlignedStrokeRectLoweringResult
}

/**
 * Lowers an exact integral rectangular stroke into four disjoint analytic coverage bands.
 *
 * This is geometry/coverage work: the WebGPU consumer receives filled bands and never
 * reinterprets raw stroke width, center, cap, or join fields in a fragment shader.
 */
class GPUAxisAlignedStrokeRectLowerer {
    fun lower(
        request: GPUAxisAlignedStrokeRectLoweringRequest,
    ): GPUAxisAlignedStrokeRectLoweringResult {
        if (!request.strokeWidth.isFinite() || request.strokeWidth <= 0f) {
            return refused("unsupported.stroke.width_invalid", "Stroke width must be finite and positive.")
        }
        val width = request.strokeWidth.toInt()
        if (width.toFloat() != request.strokeWidth || width % 2 != 0) {
            return refused(
                "unsupported.stroke.rect_subpixel_first_slice",
                "The first analytic stroke-rect slice requires an even integral width.",
            )
        }
        if (request.cap != "Butt") {
            return refused("unsupported.stroke.cap", "The first analytic stroke-rect slice requires Butt cap facts.")
        }
        if (request.join != "Miter") {
            return refused("unsupported.stroke.join", "The first analytic stroke-rect slice requires Miter joins.")
        }
        if (!request.miter.isFinite() || request.miter < MIN_RECT_MITER_LIMIT) {
            return refused(
                "unsupported.stroke.rect_miter_limit",
                "The miter limit is too small for exact rectangular corners.",
            )
        }
        if (request.transformClass !in setOf("identity", "translate")) {
            return refused(
                "unsupported.stroke.rect_transform",
                "The first analytic stroke-rect slice accepts identity or translate transforms.",
            )
        }
        if (!request.pathKey.startsWith("path:") || request.pathKey.contains("handle", ignoreCase = true) ||
            request.pathKey.contains("pointer", ignoreCase = true) || request.pathKey.contains("0x", ignoreCase = true)
        ) {
            return refused(
                "unsupported.geometry.path_key_nondeterministic",
                "The stroke path key must be deterministic and handle-free.",
            )
        }
        if (request.provenance.isBlank()) {
            return refused("unsupported.geometry.provenance", "Stroke geometry provenance must not be blank.")
        }

        val halfWidth = width.toLong() / 2L
        val outerLeft = request.pathBounds.left.toLong() - halfWidth
        val outerTop = request.pathBounds.top.toLong() - halfWidth
        val outerRight = request.pathBounds.right.toLong() + halfWidth
        val outerBottom = request.pathBounds.bottom.toLong() + halfWidth
        if (outerLeft < request.targetBounds.left.toLong() || outerTop < request.targetBounds.top.toLong() ||
            outerRight > request.targetBounds.right.toLong() || outerBottom > request.targetBounds.bottom.toLong()
        ) {
            return refused(
                "unsupported.stroke.rect_target_overflow",
                "The centered stroke outer bounds must stay inside the target.",
            )
        }
        val outer = GPUPixelBounds(
            outerLeft.toInt(),
            outerTop.toInt(),
            outerRight.toInt(),
            outerBottom.toInt(),
        )
        val innerLeft = request.pathBounds.left.toLong() + halfWidth
        val innerTop = request.pathBounds.top.toLong() + halfWidth
        val innerRight = request.pathBounds.right.toLong() - halfWidth
        val innerBottom = request.pathBounds.bottom.toLong() - halfWidth
        if (innerRight <= innerLeft || innerBottom <= innerTop) {
            return refused(
                "unsupported.stroke.rect_inner_degenerate",
                "The centered stroke must retain a non-empty inner rectangle.",
            )
        }
        val inner = GPUPixelBounds(
            innerLeft.toInt(),
            innerTop.toInt(),
            innerRight.toInt(),
            innerBottom.toInt(),
        )
        val bands = listOf(
            GPUPixelBounds(outer.left, outer.top, outer.right, inner.top),
            GPUPixelBounds(outer.left, inner.bottom, outer.right, outer.bottom),
            GPUPixelBounds(outer.left, inner.top, inner.left, inner.bottom),
            GPUPixelBounds(inner.right, inner.top, outer.right, inner.bottom),
        )
        val stroke = GPUStrokeDescriptor(
            width = request.strokeWidth,
            cap = request.cap,
            join = request.join,
            miter = request.miter,
            transformClass = request.transformClass,
            finiteWidth = true,
            hairline = false,
            edgeCount = RECT_EDGE_COUNT,
        )
        val descriptor = GPUShapeDescriptor(
            shapeKind = "rect-stroke",
            boundsLabel = outer.stableLabel(),
            antiAliasMode = "none",
            provenance = request.provenance,
        )
        val diagnostic = GPUGeometryDiagnostic(
            code = "geometry:stroke-rect.analytic",
            geometryLabel = "rect-stroke",
            message = "Axis-aligned rectangular stroke lowered to four analytic coverage bands.",
            terminal = false,
            facts = mapOf(
                "route" to ANALYTIC_RECT_ROUTE,
                "coverageBands" to bands.size.toString(),
                "strokeWidth" to request.strokeWidth.toString(),
                "cap" to request.cap,
                "join" to request.join,
                "miter" to request.miter.toString(),
                "transform" to request.transformClass,
            ),
        )
        return GPUAxisAlignedStrokeRectLoweringResult.Lowered(
            geometryPlan = GPUGeometryPlan(
                descriptor = descriptor,
                path = GPUPathDescriptor(
                    pathKey = request.pathKey,
                    verbCount = RECT_VERB_COUNT,
                    pointCount = RECT_POINT_COUNT,
                    fillRule = "NonZero",
                    inverseFill = false,
                    finiteProof = "finite",
                    volatility = "immutable",
                    transformClass = request.transformClass,
                    edgeCount = RECT_EDGE_COUNT,
                ),
                stroke = stroke,
                route = GPUGeometryRoute.Analytic(ANALYTIC_RECT_ROUTE),
                diagnostics = listOf(diagnostic),
            ),
            outerBounds = outer,
            innerBounds = inner,
            coverageBands = bands,
        )
    }

    private fun refused(code: String, message: String) =
        GPUAxisAlignedStrokeRectLoweringResult.Refused(
            GPUGeometryDiagnostic(code, "rect-stroke", message, terminal = true),
        )

    private companion object {
        const val ANALYTIC_RECT_ROUTE = "analytic-annular-rect.coverage"
        const val RECT_VERB_COUNT = 5
        const val RECT_POINT_COUNT = 4
        const val RECT_EDGE_COUNT = 4
        const val MIN_RECT_MITER_LIMIT = 1.414214f
    }
}

private fun GPUPixelBounds.stableLabel(): String = "device[$left,$top,$right,$bottom]"
