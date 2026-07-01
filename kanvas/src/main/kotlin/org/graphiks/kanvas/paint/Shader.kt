package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Matrix33

sealed interface Shader {
    data class SolidColor(val color: Color) : Shader
    data class LinearGradient(
        val start: Point, val end: Point,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
    ) : Shader
    data class RadialGradient(
        val center: Point, val radius: Float,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
    ) : Shader
    data class SweepGradient(
        val center: Point,
        val startAngle: Float = 0f,
        val endAngle: Float = 360f,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
    ) : Shader
    data class ConicalGradient(
        val start: Point, val startRadius: Float,
        val end: Point, val endRadius: Float,
        val stops: List<GradientStop>,
        val tileMode: TileMode = TileMode.CLAMP,
    ) : Shader
    data class Image(
        val image: org.graphiks.kanvas.image.Image,
        val tileModeX: TileMode = TileMode.CLAMP,
        val tileModeY: TileMode = TileMode.CLAMP,
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
}
