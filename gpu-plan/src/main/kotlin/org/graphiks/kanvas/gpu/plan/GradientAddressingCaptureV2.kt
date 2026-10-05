package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.GraphLimits
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

internal sealed interface CoordinateNodeV2 {
    class LocalMatrix(matrixF32: Matrix3x3F32) : CoordinateNodeV2 {
        private val matrixF32 = matrixF32.copy()
        fun copyMatrixF32(): Matrix3x3F32 = matrixF32.copy()
    }
    class CoordClamp(subsetF32: RectF32) : CoordinateNodeV2 {
        private val subsetF32 = subsetF32.copy()
        fun copySubsetF32(): RectF32 = subsetF32.copy()
    }
}

internal sealed interface GradientAddressingCaptureV2 {
    data class Ready(val leaf: MaterialNode, val opacityF32: Float,
        val coordinateNodes: List<CoordinateNodeV2>) : GradientAddressingCaptureV2
    data class PreAdmission(val diagnosticCode: String) : GradientAddressingCaptureV2
    data class Refused(val diagnosticCode: String) : GradientAddressingCaptureV2

    companion object {
        fun capture(root: MaterialNode, limits: GraphLimits = GraphLimits()): GradientAddressingCaptureV2 {
            var current = root
            val opacity = mutableListOf<Float>()
            val coordinates = mutableListOf<CoordinateNodeV2>()
            var visitedI32 = 0
            while (true) {
                if (++visitedI32 > limits.maxDepth || visitedI32 > limits.maxNodes)
                    return Refused("graph-depth-limit")
                when (val node = current) {
                    is MaterialNode.Opacity -> { opacity += node.alpha; current = node.material }
                    is MaterialNode.WithLocalMatrix -> { coordinates += CoordinateNodeV2.LocalMatrix(node.matrix); current = node.material }
                    is MaterialNode.CoordClamp -> { coordinates += CoordinateNodeV2.CoordClamp(node.copySubset()); current = node.material }
                    is MaterialNode.LinearGradient, is MaterialNode.RadialGradient,
                    is MaterialNode.SweepGradient, is MaterialNode.ConicalGradient -> return Ready(node,
                        opacity.asReversed().fold(1f) { accumulatedF32, alphaF32 -> accumulatedF32 * alphaF32 }, immutableList(coordinates))
                    MaterialNode.Transparent, is MaterialNode.Solid, is MaterialNode.ImageSample,
                    is MaterialNode.Blend, is MaterialNode.RuntimeEffect, is MaterialNode.WithColorFilter,
                    is MaterialNode.WithWorkingColorSpace, is MaterialNode.PerlinNoise, is MaterialNode.FractalNoise ->
                        return PreAdmission(W5aPlanDiagnostics.UnsupportedMaterial)
                }
            }
        }
    }
}
