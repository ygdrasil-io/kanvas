package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter

private const val PERSPECTIVE_CAPTURE_REFUSAL = "unsupported_transform:Perspective"

/** Returns the stable refusal for any command whose clip was captured under perspective. */
internal fun GPUClipFacts.perspectiveCaptureRefusalReasonOrNull(): String? =
    perspectiveCaptureRefusal.takeIf { it }?.let { PERSPECTIVE_CAPTURE_REFUSAL }

/** Returns the same stable refusal before a [DisplayOp] reaches any GPU encoding route. */
internal fun DisplayOp.perspectiveCaptureRefusalReasonOrNull(): String? =
    clipOrNull()?.perspectiveCaptureRefusal?.takeIf { it }?.let { PERSPECTIVE_CAPTURE_REFUSAL }

private fun DisplayOp.clipOrNull(): ClipStack? = when (this) {
    is DisplayOp.DrawRect -> clip
    is DisplayOp.DrawRRect -> clip
    is DisplayOp.DrawPath -> clip
    is DisplayOp.DrawImage -> clip
    is DisplayOp.DrawText -> clip
    is DisplayOp.DrawColor -> clip
    is DisplayOp.DrawPoint -> clip
    is DisplayOp.DrawPoints -> clip
    is DisplayOp.DrawDRRect -> clip
    is DisplayOp.DrawImageNine -> clip
    is DisplayOp.DrawImageLattice -> clip
    is DisplayOp.DrawPicture -> clip
    is DisplayOp.DrawVertices -> clip
    is DisplayOp.DrawMesh -> clip
    is DisplayOp.DrawAtlas -> clip
    is DisplayOp.SetTransform,
    is DisplayOp.SetClip,
    is DisplayOp.BeginLayer,
    DisplayOp.EndLayer,
    is DisplayOp.Clear,
    is DisplayOp.Annotation,
    is DisplayOp.FlushAndSnapshot -> null
}

internal fun GPUBackendRenderRecorder.textureDimensionsRefusalReasonOrNull(
    width: Int,
    height: Int,
): String? {
    val limit = maxTextureDimension2D
    return if (width > limit || height > limit) {
        "texture_dimensions_exceed_max_texture_dimension:${width}x${height}>$limit"
    } else {
        null
    }
}

internal fun NormalizedDrawCommand.strokeRefusalReasonOrNull(): String? {
    return null // stroke is now handled via geometry conversion
}

internal fun NormalizedDrawCommand.fillGuardRefusalReasonOrNull(): String? {
    clip.perspectiveCaptureRefusalReasonOrNull()?.let { return it }
    strokeRefusalReasonOrNull()?.let { return it }
    if (this is NormalizedDrawCommand.DrawTextRun) return null
    val material = this.material
    val maskBlur = when (this) {
        is NormalizedDrawCommand.FillRect -> maskFilter as? NormalizedMaskFilter.Blur
        is NormalizedDrawCommand.FillRRect -> maskFilter as? NormalizedMaskFilter.Blur
        is NormalizedDrawCommand.FillPath -> maskFilter as? NormalizedMaskFilter.Blur
        else -> null
    }
    if (maskBlur != null && maskBlur.sigma != 0f) {
        return if (material is GPUMaterialDescriptor.SolidColor) {
            "unsupported.mask-filter.blur.executor_unavailable"
        } else {
            "unsupported.mask-filter.blur.material.${material.kind.name}"
        }
    }
    val acceptedByDispatch = this is NormalizedDrawCommand.FillRect ||
        this is NormalizedDrawCommand.FillPath
    if (material !is GPUMaterialDescriptor.SolidColor &&
        material.kind != GPUMaterialKind.RuntimeEffect &&
        (!acceptedByDispatch || 
         (material !is GPUMaterialDescriptor.LinearGradient &&
          material !is GPUMaterialDescriptor.RadialGradient &&
          material !is GPUMaterialDescriptor.SweepGradient &&
          material !is GPUMaterialDescriptor.ConicalGradient &&
          !(this is NormalizedDrawCommand.FillRect && material is GPUMaterialDescriptor.ImageDraw) &&
          !(this is NormalizedDrawCommand.FillPath && material is GPUMaterialDescriptor.ImageDraw)))
    ) {
        return "unsupported_material:${material.kind.name}"
    }
    if (transform.type != GPUTransformType.Identity) {
        return "unsupported_transform:${transform.type.name}"
    }
    if (clip.kind !in listOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect, GPUClipKind.ComplexStack)) {
        return "unsupported_clip:${clip.kind.name}"
    }
    if (layer.scopeKind != GPULayerScopeKind.Root) {
        return "unsupported_layer:${layer.scopeKind.name}"
    }
    return null
}

internal fun NormalizedDrawCommand.FillRRect.nonUniformRadiiRefusalReasonOrNull(): String? {
    val rrect = this.rrect
    val rx = rrect.topLeft.x
    val ry = rrect.topLeft.y
    return if (rrect.topRight.x != rx || rrect.topRight.y != ry ||
        rrect.bottomRight.x != rx || rrect.bottomRight.y != ry ||
        rrect.bottomLeft.x != rx || rrect.bottomLeft.y != ry
    ) {
        "non_uniform_radii"
    } else {
        null
    }
}
