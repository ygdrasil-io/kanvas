package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.GraphLimits

/** Shared W5c continuity and W5d readiness predicate for both admission consumers. */
internal fun DisplayOp.isW5dGradientCandidateV2(allowNonGradient: Boolean = false): Boolean {
    val paint = when (this) {
        is DisplayOp.DrawRect -> paint
        is DisplayOp.DrawRRect -> paint
        is DisplayOp.DrawPath -> paint
        else -> return false
    }
    if (paint.colorFilter != null) {
        if (this !is DisplayOp.DrawRect && this !is DisplayOp.DrawPath || paint.isStroke() ||
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.Matrix &&
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.Compose &&
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.Lerp &&
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.Table &&
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.Lighting &&
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.Blend &&
            paint.colorFilter != org.graphiks.kanvas.paint.ColorFilter.SRGBToLinear &&
            paint.colorFilter != org.graphiks.kanvas.paint.ColorFilter.LinearToSRGB &&
            paint.colorFilter !is org.graphiks.kanvas.paint.ColorFilter.HSLAMatrix &&
            paint.colorFilter != org.graphiks.kanvas.paint.ColorFilter.HighContrast &&
            paint.colorFilter != org.graphiks.kanvas.paint.ColorFilter.Luma &&
            paint.colorFilter != org.graphiks.kanvas.paint.ColorFilter.Overdraw) return false
        var source = paint.shader
        var countI32 = 0
        while (source is Shader.Opacity || source is Shader.WithColorFilter) {
            if (++countI32 > GraphLimits().maxDepth) return allowNonGradient
            source = when (source) { is Shader.Opacity -> source.shader; is Shader.WithColorFilter -> source.shader }
        }
        return source == null || source is Shader.SolidColor ||
            source.isW5dGradientCandidateV2(true,false,true)
    }
    val allowGradient = when (this) {
        is DisplayOp.DrawRect -> !paint.isStroke()
        is DisplayOp.DrawRRect -> !paint.isStroke() && paint.antiAlias
        is DisplayOp.DrawPath -> true
    }
    return paint.shader?.isW5dGradientCandidateV2(allowGradient,allowNonGradient,
        (this is DisplayOp.DrawRect || this is DisplayOp.DrawPath) && !paint.isStroke()) ?: allowNonGradient
}

private fun Shader.isW5dGradientCandidateV2(allowGradient: Boolean,allowNonGradient: Boolean,
    allowFilteredGradient: Boolean): Boolean {
    var source = this
    var localCountI32 = 0
    val limits = GraphLimits()
    var visitedI32 = 0
    var filtered = false
    while (true) {
        if (++visitedI32 > limits.maxDepth || visitedI32 > limits.maxNodes)
            return allowNonGradient || (allowGradient && localCountI32 > 0)
        when (val node = source) {
            is Shader.Opacity -> source = node.shader
            is Shader.WithColorFilter -> { filtered = true; source = node.shader }
            is Shader.WithLocalMatrix -> {
                if (!allowGradient) return false
                // Affine/projective and invalid coefficients belong to the same planner.
                localCountI32++
                source = node.shader
            }
            is Shader.LinearGradient, is Shader.RadialGradient, is Shader.SweepGradient, is Shader.ConicalGradient ->
                return allowGradient && (!filtered || allowFilteredGradient)
            is Shader.CoordClamp -> {
                if (!allowGradient) return false
                localCountI32++
                source = node.shader
            }
            is Shader.SolidColor -> return (allowNonGradient || filtered) && localCountI32 == 0
            is Shader.WithWorkingColorSpace,
            is Shader.Blend, is Shader.Image, is Shader.RuntimeEffect,
            is Shader.PerlinNoise, is Shader.FractalNoise -> return allowNonGradient && localCountI32 == 0
        }
    }
}
