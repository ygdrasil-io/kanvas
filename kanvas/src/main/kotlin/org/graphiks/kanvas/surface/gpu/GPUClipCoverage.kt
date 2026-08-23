package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan

/**
 * Shared composite terminal-refusal contract: refused draws must never reserve a mask use.
 *
 * This guard has no production callers at HEAD; it survives because
 * GPUPreparedSurfaceProductRouterTest pins the exact `unsupported.picture.nested_vertices`
 * code for vertices/meshes and the null preflight for other families — a unit pin only.
 * The composite capture does not rely on this refusal: it refuses vertices/meshes children
 * with its own `unsupported.composite.operation` code (GPUPreparedSurfaceFrameBuilder.kt).
 */
internal fun DisplayOp.coreRoutePreflightRefusalReason(): String? = when (this) {
    is DisplayOp.DrawVertices,
    is DisplayOp.DrawMesh,
    -> "unsupported.picture.nested_vertices"
    is DisplayOp.DrawPicture -> picturePreflightRefusalReason()
    else -> null
}

/**
 * Plan-mandated placeholder for future visual operations (named boundary), not
 * currently pinned: Task 4 supplies an S/G adapter for every visual operation
 * accepted by this renderer. A future visual operation must either install its
 * adapter or return a stable refusal here.
 */
internal fun DisplayOp.coveragePlaneTask4RefusalOrNull(): String? = null

private fun DisplayOp.DrawPicture.picturePreflightRefusalReason(): String? {
    if (paint != null && SaveLayerRec(paint = paint).gpuCompositePreflightRefusalOrNull() != null) {
        return "unsupported.picture.paint"
    }
    if (transform != Matrix3x3F32.Identity && picture.containsLayer()) {
        // Fill dispatch intentionally accepts identity transforms only. Refuse before
        // expanding the picture so no partially transformed layer/clip is encoded.
        return "unsupported.picture.transformed_layer"
    }

    fun validatePicture(picture: Picture): String? {
        for (nested in picture.ops) {
            when (nested) {
                is DisplayOp.DrawPicture -> {
                    if (nested.paint != null &&
                        SaveLayerRec(paint = nested.paint).gpuCompositePreflightRefusalOrNull() != null
                    ) {
                        return "unsupported.picture.nested_paint"
                    }
                    if (nested.transform != Matrix3x3F32.Identity && nested.picture.containsLayer()) {
                        return "unsupported.picture.transformed_layer"
                    }
                    val nestedRefusal = validatePicture(nested.picture)
                    if (nestedRefusal != null) return nestedRefusal
                }
                else -> Unit
            }
        }
        return null
    }

    return validatePicture(picture)
}

private fun Picture.containsLayer(): Boolean = ops.any { operation ->
    operation is DisplayOp.BeginLayer ||
        (operation as? DisplayOp.DrawPicture)?.picture?.containsLayer() == true
}

/** The layer compositor accepts only alpha and BlendMode from its optional paint. */
internal fun SaveLayerRec.gpuCompositePreflightRefusalOrNull(): String? {
    if (backdrop != null) return "unsupported.layer.backdrop_filter"
    val layerPaint = paint ?: return null
    if (
        layerPaint.shader != null ||
        layerPaint.colorFilter != null ||
        layerPaint.maskFilter != null ||
        layerPaint.pathEffect != null ||
        layerPaint.imageFilter != null ||
        layerPaint.blender != null ||
        layerPaint.style != Paint().style ||
        layerPaint.strokeWidth != 0f ||
        layerPaint.strokeCap != Paint().strokeCap ||
        layerPaint.strokeJoin != Paint().strokeJoin ||
        layerPaint.strokeMiter != Paint().strokeMiter ||
        layerPaint.antiAlias != Paint().antiAlias
    ) {
        return "unsupported.layer.paint"
    }
    return (layerPaint.blendMode.toGpuBlendFacts().canonicalBlendPlan() as? GPUBlendPlan.UnsupportedBlend)
        ?.let { "unsupported.layer.blend:${it.mode.gpuLabel}" }
}
