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
        if (++visitedI32 > limits.maxDepth || visitedI32 > limits.maxNodes) return allowNonGradient
        when (val node = source) {
            is Shader.Opacity -> source = node.shader
            is Shader.WithLocalMatrix -> {
                if (!allowLocalMatrix) return false
                if (node.matrix.persp0 != 0f || node.matrix.persp1 != 0f || node.matrix.persp2 != 1f) return false
                localCountI32++
                source = node.shader
            }
            is Shader.LinearGradient -> return localCountI32 <= 1 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.RadialGradient -> return localCountI32 == 0 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.SweepGradient -> return localCountI32 == 0 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.ConicalGradient -> return localCountI32 == 0 && node.tileMode == TileMode.CLAMP &&
                node.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.CoordClamp -> return false
            is Shader.WithColorFilter, is Shader.WithWorkingColorSpace,
            is Shader.Blend, is Shader.Image, is Shader.RuntimeEffect, is Shader.SolidColor,
            is Shader.PerlinNoise, is Shader.FractalNoise -> return allowNonGradient && localCountI32 == 0
        }
    }
}
