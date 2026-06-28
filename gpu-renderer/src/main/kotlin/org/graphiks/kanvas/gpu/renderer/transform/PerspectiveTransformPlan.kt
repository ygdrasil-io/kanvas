package org.graphiks.kanvas.gpu.renderer.transform

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class GeometryKind { Rect, RRect, Path, Text }
enum class MaterialKind { SolidColor, Gradient }

sealed interface PerspectiveTransformRoute {
    data class Accepted(val transformKind: String) : PerspectiveTransformRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : PerspectiveTransformRoute
}

data class PerspectiveTransformPlan(
    val geometry: GeometryKind,
    val material: MaterialKind,
) {
    companion object {
        fun forGeometry(geometry: GeometryKind, material: MaterialKind): PerspectiveTransformPlan =
            PerspectiveTransformPlan(geometry, material)
    }

    fun analyze(): PerspectiveTransformRoute {
        val acceptedGeometries = setOf(GeometryKind.Rect, GeometryKind.RRect)
        if (geometry !in acceptedGeometries) {
            return PerspectiveTransformRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.perspective_transform.${geometry.name.lowercase()}",
                    message = "perspective transform not supported for ${geometry.name.lowercase()} geometry",
                    stage = "perspective.analysis",
                    terminal = true,
                )
            )
        }
        if (material != MaterialKind.SolidColor) {
            return PerspectiveTransformRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.perspective_transform.${material.name.lowercase()}",
                    message = "perspective transform not supported for ${material.name.lowercase()} material",
                    stage = "perspective.analysis",
                    terminal = true,
                )
            )
        }
        return PerspectiveTransformRoute.Accepted("perspective-${geometry.name.lowercase()}")
    }
}
