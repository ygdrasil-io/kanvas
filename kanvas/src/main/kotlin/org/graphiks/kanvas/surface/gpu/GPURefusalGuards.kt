package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand

internal fun NormalizedDrawCommand.strokeRefusalReasonOrNull(): String? {
    return null // stroke is now handled via geometry conversion
}

internal fun NormalizedDrawCommand.fillGuardRefusalReasonOrNull(): String? {
    strokeRefusalReasonOrNull()?.let { return it }
    if (this is NormalizedDrawCommand.DrawTextRun) return null
    val material = this.material
    val acceptedByDispatch = this is NormalizedDrawCommand.FillRect ||
        this is NormalizedDrawCommand.FillPath
    if (material !is GPUMaterialDescriptor.SolidColor &&
        material.kind != GPUMaterialKind.RuntimeEffect &&
        (!acceptedByDispatch || 
         (material !is GPUMaterialDescriptor.LinearGradient &&
          material !is GPUMaterialDescriptor.RadialGradient &&
          material !is GPUMaterialDescriptor.SweepGradient))
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
