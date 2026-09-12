package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.render.ir.GraphLimits

/** Shared W5c continuity and W5d readiness predicate for both admission consumers. */
internal fun Shader.isW5dGradientCandidateV2(allowLocalMatrix: Boolean = true, allowNonGradient: Boolean = false): Boolean {
    var source = this
    var localCountI32 = 0
    val limits = GraphLimits()
    var visitedI32 = 0
    while (true) {
        if (++visitedI32 > limits.maxDepth || visitedI32 > limits.maxNodes)
            return allowNonGradient || (allowLocalMatrix && localCountI32 > 0)
        when (val node = source) {
            is Shader.Opacity -> source = node.shader
            is Shader.WithLocalMatrix -> {
                if (!allowLocalMatrix) return false
                // Affine/projective and invalid coefficients belong to the same planner.
                localCountI32++
                source = node.shader
            }
            is Shader.LinearGradient -> return node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.RadialGradient -> return localCountI32 == 0 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.SweepGradient -> return localCountI32 == 0 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.ConicalGradient -> return localCountI32 == 0 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.CoordClamp -> {
                if (!allowLocalMatrix) return false
                localCountI32++
                source = node.shader
            }
            is Shader.WithColorFilter, is Shader.WithWorkingColorSpace,
            is Shader.Blend, is Shader.Image, is Shader.RuntimeEffect, is Shader.SolidColor,
            is Shader.PerlinNoise, is Shader.FractalNoise -> return allowNonGradient && localCountI32 == 0
        }
    }
}
