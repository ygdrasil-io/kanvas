package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter

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
    if (clip.perspectiveCaptureRefusal) {
        return "unsupported_transform:Perspective"
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
    if (blend.kind == GPUBlendKind.Unsupported) {
        return "unsupported_blend:${blend.modeLabel}"
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
