package org.graphiks.kanvas.paint

import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32

enum class ColorSpaceInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }

sealed interface Shader {
    data class SolidColor(val color: ColorARGB) : Shader
    data class LinearGradient(
        val start: Point2F32, val end: Point2F32,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class RadialGradient(
        val center: Point2F32, val radius: Float,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class SweepGradient(
        val center: Point2F32,
        val startAngle: Float = 0f,
        val endAngle: Float = 360f,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class ConicalGradient(
        val start: Point2F32, val startRadius: Float,
        val end: Point2F32, val endRadius: Float,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class Image(
        val image: org.graphiks.kanvas.image.Image,
        val tileModeX: TileMode = TileMode.CLAMP,
        val tileModeY: TileMode = TileMode.CLAMP,
        val sampling: SamplingOptions = SamplingOptions.NEAREST,
    ) : Shader
    data class Blend(
        val mode: BlendMode, val dst: Shader, val src: Shader,
    ) : Shader
    data class RuntimeEffect(
        val effect: org.graphiks.kanvas.pipeline.RuntimeEffect,
        val uniforms: org.graphiks.kanvas.pipeline.UniformBlock,
        val children: Map<String, Shader> = emptyMap(),
    ) : Shader
    data class WithLocalMatrix(val shader: Shader, val matrix: Matrix3x3F32) : Shader
    data class WithColorFilter(val shader: Shader, val filter: ColorFilter) : Shader
    data class PerlinNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: SizeF32?) : Shader
    data class FractalNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: SizeF32?) : Shader
    data class WithWorkingColorSpace(val shader: Shader, val interpolation: ColorSpaceInterpolation) : Shader
    data class CoordClamp(val shader: Shader, val subset: RectF32) : Shader
}
