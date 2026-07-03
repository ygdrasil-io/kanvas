package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size

enum class ColorSpaceInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }

sealed interface Shader {
    data class SolidColor(val color: Color) : Shader
    data class LinearGradient(
        val start: Point, val end: Point,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class RadialGradient(
        val center: Point, val radius: Float,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class SweepGradient(
        val center: Point,
        val startAngle: Float = 0f,
        val endAngle: Float = 360f,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
        val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB,
    ) : Shader
    data class ConicalGradient(
        val start: Point, val startRadius: Float,
        val end: Point, val endRadius: Float,
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
    ) : Shader
    data class WithLocalMatrix(val shader: Shader, val matrix: Matrix33) : Shader
    data class WithColorFilter(val shader: Shader, val filter: ColorFilter) : Shader
    data class PerlinNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
    data class FractalNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
    data class WithWorkingColorSpace(val shader: Shader, val interpolation: ColorSpaceInterpolation) : Shader
    data class CoordClamp(val shader: Shader, val subset: Rect) : Shader
}
