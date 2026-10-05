package org.graphiks.kanvas.render.ir

import java.util.ArrayDeque
import java.util.IdentityHashMap
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader

/** Metadata only. Even runtime/merge child collections are traversed without copying. */
@OptIn(ExperimentalUnsignedTypes::class)
internal object ColorFilterCapturePreflight {
    fun validatePaint(paint: Paint, limits: SceneCaptureLimits, reserveRuntimeUniformBytes: (Long)->Unit = {}): RenderDiagnostic? {
        data class Frame(val children: Iterator<Any>, val depthI32: Int, val parent: Any?)
        fun diagnostic(code: String, message: String) = RenderDiagnostic(RenderDiagnosticCode(code),
            RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message)
        val active = IdentityHashMap<Any, Unit>()
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(listOfNotNull(paint.shader, paint.colorFilter, paint.maskFilter,
            paint.pathEffect, paint.imageFilter, paint.blender).iterator(), 1, null))
        var countI32 = 0
        var runtimeUniformBytesI64 = 0L
        var runtimeSourceSeen = false
        while (stack.isNotEmpty()) {
            val frame = stack.peekLast()
            if (!frame.children.hasNext()) {
                frame.parent?.let(active::remove)
                stack.removeLast()
                continue
            }
            val value = frame.children.next()
            if(value is Shader.RuntimeEffect) runtimeSourceSeen=true
            if (active.put(value, Unit) != null)
                return diagnostic("cyclic-effect-graph", "Paint, effect, or material graph contains an identity cycle")
            if (frame.depthI32 > minOf(limits.maxDepth, limits.graphLimits.maxDepth))
                return diagnostic(if(runtimeSourceSeen) org.graphiks.kanvas.gpu.plan.W5hPlanDiagnostics.Budget else "graph-depth-limit",
                    "Paint, effect, or material graph exceeds configured depth")
            if (++countI32 > limits.graphLimits.maxNodes)
                return diagnostic(if(runtimeSourceSeen) org.graphiks.kanvas.gpu.plan.W5hPlanDiagnostics.Budget else "graph-node-limit",
                    "Paint, effect, or material graph exceeds configured nodes")
            val runtimeUniforms = when (value) {
                is Shader.RuntimeEffect -> value.uniforms
                is ColorFilter.RuntimeEffect -> value.uniforms
                is ImageFilter.RuntimeEffect -> value.uniforms
                else -> null
            }
            runtimeUniforms?.entries?.values?.forEach { uniform ->
                val sizeI64 = when (uniform) {
                    is org.graphiks.kanvas.pipeline.UniformValue.F1, is org.graphiks.kanvas.pipeline.UniformValue.I1 -> 4L
                    is org.graphiks.kanvas.pipeline.UniformValue.F2 -> 8L
                    is org.graphiks.kanvas.pipeline.UniformValue.F3 -> 12L
                    is org.graphiks.kanvas.pipeline.UniformValue.F4 -> 16L
                    is org.graphiks.kanvas.pipeline.UniformValue.M3 -> 48L
                    is org.graphiks.kanvas.pipeline.UniformValue.M4 -> 64L
                }
                runtimeUniformBytesI64 = try { Math.addExact(runtimeUniformBytesI64, sizeI64) }
                    catch (_: ArithmeticException) {
                        return diagnostic("unsupported.material.runtime_effect.budget", "Runtime uniform byte count overflows I64")
                    }
                if (runtimeUniformBytesI64 > limits.maxRuntimeUniformBytesI64)
                    return diagnostic(org.graphiks.kanvas.gpu.plan.W5hPlanDiagnostics.Budget, "Runtime uniforms exceed the capture byte budget")
                reserveRuntimeUniformBytes(sizeI64)
                if(value is Shader.RuntimeEffect && value.effect.semanticVersionI32 > 0 &&
                    uniform is org.graphiks.kanvas.pipeline.UniformValue.F1 && !uniform.v.isFinite())
                    return diagnostic(org.graphiks.kanvas.gpu.plan.W5hPlanDiagnostics.CpuUniforms,"Runtime scalar uniform must be finite")
            }
            if (value is ColorFilter.Matrix) for (indexI32 in 0 until 20) {
                if (!value.matrix[indexI32].isFinite())
                    return diagnostic("non-finite-value", "color-filter.matrix must be finite")
            }
            if (value is ColorFilter.Lerp) {
                if (!value.t.isFinite()) return diagnostic("non-finite-value", "color-filter.lerp must be finite")
                if (value.t !in 0f..1f) return diagnostic("invalid.material.filter.lerp", "color-filter.lerp must be in [0,1]")
            }
            if (value is ColorFilter.Table && value.table.size != 256)
                return diagnostic("invalid.material.filter.table", "color-filter.table must have exactly 256 entries")
            val noise = when (value) {
                is Shader.PerlinNoise -> Triple(value.baseX, value.baseY, value.numOctaves)
                is Shader.FractalNoise -> Triple(value.baseX, value.baseY, value.numOctaves)
                else -> null
            }
            if (noise != null) {
                if (!noise.first.isFinite() || !noise.second.isFinite() || noise.first < 0f ||
                    noise.second < 0f || noise.third !in 0..255)
                    return diagnostic("invalid.material.noise.parameters", "Noise frequencies must be finite and nonnegative; octaves must be in 0..255")
                val tile = when (value) {
                    is Shader.PerlinNoise -> value.tileSize
                    is Shader.FractalNoise -> value.tileSize
                    else -> null
                }
                if (tile != null && (tile.width < 0 || tile.height < 0))
                    return diagnostic("invalid.material.noise.tile", "Noise tile dimensions must be nonnegative integral I32 values")
            }
            if (value is ColorFilter.HSLAMatrix) {
                if (value.values.size != 20)
                    return diagnostic("invalid.material.filter.hsla", "color-filter.hsla must have exactly 20 coefficients")
                if (value.values.any { !it.isFinite() })
                    return diagnostic("non-finite-value", "color-filter.hsla must be finite")
            }
            stack.addLast(Frame(children(value).iterator(), frame.depthI32 + 1, value))
        }
        return null
    }

    private fun children(value: Any): Sequence<Any> = sequence {
        when (value) {
            is Shader.Blend -> { yield(value.dst); yield(value.src) }
            is Shader.RuntimeEffect -> yieldAll(value.children.values)
            is Shader.WithLocalMatrix -> yield(value.shader)
            is Shader.WithColorFilter -> { yield(value.shader); yield(value.filter) }
            is Shader.WithWorkingColorSpace -> yield(value.shader)
            is Shader.CoordClamp -> yield(value.shader)
            is Shader.Opacity -> yield(value.shader)
            is ColorFilter.Compose -> { yield(value.outer); yield(value.inner) }
            is ColorFilter.Lerp -> { yield(value.dst); yield(value.src) }
            is ColorFilter.RuntimeEffect -> yieldAll(value.children.values)
            is MaskFilter.Shader -> yield(value.shader)
            is ImageFilter.ColorFilter -> { yield(value.filter); value.input?.let { yield(it) } }
            is ImageFilter.Compose -> { yield(value.outer); yield(value.inner) }
            is ImageFilter.Blend -> { yield(value.background); yield(value.foreground) }
            is ImageFilter.Merge -> yieldAll(value.inputs)
            is ImageFilter.RuntimeEffect -> value.childImageFilters.values.forEach { it?.let { yield(it) } }
            is ImageFilter.DisplacementMap -> { yield(value.displacement); value.input?.let { yield(it) } }
            else -> when (value) {
                is ImageFilter.Crop -> value.input
                is ImageFilter.Blur -> value.input
                is ImageFilter.DropShadow -> value.input
                is ImageFilter.Dilate -> value.input
                is ImageFilter.Erode -> value.input
                is ImageFilter.DistantLitDiffuse -> value.input
                is ImageFilter.PointLitDiffuse -> value.input
                is ImageFilter.SpotLitDiffuse -> value.input
                is ImageFilter.DistantLitSpecular -> value.input
                is ImageFilter.PointLitSpecular -> value.input
                is ImageFilter.SpotLitSpecular -> value.input
                is ImageFilter.Offset -> value.input
                is ImageFilter.Tile -> value.input
                is ImageFilter.Magnifier -> value.input
                is ImageFilter.MatrixConvolution -> value.input
                else -> null
            }?.let { yield(it) }
        }
    }
}
