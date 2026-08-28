package org.graphiks.kanvas.gpu.renderer.commands

import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

/**
 * Shared admission contract for the deliberately narrow native path hairline route.
 *
 * The route is a one-pixel direct device quad, so all consumers must agree on the
 * same immutable shape, material, transform, clip and layer restrictions.
 */
fun NormalizedDrawCommand.FillPath.isBoundedNativePathHairline(): Boolean =
    stroke &&
        contourStarts == listOf(0) &&
        tessellatedVertices.size == 4 &&
        strokeWidth == 0f &&
        !antiAlias &&
        (dashIntervals == null || dashIntervals.isEmpty()) &&
        pathEffectKind == null &&
        strokeCap == "butt" &&
        strokeJoin == "miter" &&
        strokeMiterLimit.isFinite() && strokeMiterLimit >= 1f &&
        transform.type in setOf(GPUTransformType.Identity, GPUTransformType.Translate) &&
        (clip.executionPlan == GPUClipExecutionPlan.NoClip ||
            clip.executionPlan is GPUClipExecutionPlan.ScissorOnly) &&
        material is GPUMaterialDescriptor.SolidColor &&
        blend.mode == GPUBlendMode.SRC_OVER &&
        layer.scopeKind == GPULayerScopeKind.Root &&
        tessellatedVertices.all(Float::isFinite) &&
        transform.translateX.isFinite() && transform.translateY.isFinite() &&
        (tessellatedVertices[0] == tessellatedVertices[2] ||
            tessellatedVertices[1] == tessellatedVertices[3])
