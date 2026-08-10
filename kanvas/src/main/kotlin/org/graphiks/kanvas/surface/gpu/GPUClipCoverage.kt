package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan

/**
 * Shared renderer/prepass refusal contract: refused draws must never reserve a mask use.
 *
 * Prepared vertices and meshes never continue through the legacy immediate route. This code is
 * reached both by vertices/meshes nested inside a legacy composite (pictures/layers) and by
 * top-level vertices/meshes in legacy-gated frames (e.g. BGRA8 or composite frames); the
 * "nested" label reflects the composite case. Vertices/meshes inside composites remain
 * unsupported until FP-07.
 */
internal fun DisplayOp.coreRoutePreflightRefusalReason(): String? = when (this) {
    is DisplayOp.DrawVertices,
    is DisplayOp.DrawMesh,
    -> "unsupported.picture.nested_vertices"
    is DisplayOp.DrawPicture -> picturePreflightRefusalReason()
    else -> null
}

/**
 * Task 4 supplies an S/G adapter for every visual operation accepted by this
 * renderer. Kept as a named boundary for prepass callers: a future visual
 * operation must either install its adapter or return a stable refusal here.
 */
internal fun DisplayOp.coveragePlaneTask4RefusalOrNull(): String? = null

private fun DisplayOp.DrawPicture.picturePreflightRefusalReason(): String? {
    if (paint != null && SaveLayerRec(paint = paint).gpuCompositePreflightRefusalOrNull() != null) {
        return "unsupported.picture.paint"
    }
    if (transform != Matrix33.identity() && picture.containsLayer()) {
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
                    if (nested.transform != Matrix33.identity() && nested.picture.containsLayer()) {
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
