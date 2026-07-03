package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size

enum class ColorChannel { R, G, B, A }

sealed interface ImageFilter {
    data class Blur(
        val sigmaX: Float, val sigmaY: Float,
        val tileMode: TileMode = TileMode.CLAMP,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class DropShadow(
        val dx: Float, val dy: Float,
        val sigmaX: Float, val sigmaY: Float,
        val color: Color,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class ColorFilter(val filter: org.graphiks.kanvas.paint.ColorFilter, val input: ImageFilter? = null) : ImageFilter
    data class Compose(val outer: ImageFilter, val inner: ImageFilter) : ImageFilter
    data class Blend(
        val mode: BlendMode,
        val background: ImageFilter, val foreground: ImageFilter,
    ) : ImageFilter
    data class Dilate(val radiusX: Float, val radiusY: Float, val input: ImageFilter? = null) : ImageFilter
    data class Erode(val radiusX: Float, val radiusY: Float, val input: ImageFilter? = null) : ImageFilter
    data class DistantLitDiffuse(
        val direction: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class PointLitDiffuse(
        val location: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class SpotLitDiffuse(
        val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float,
        val lightColor: Color, val surfaceScale: Float, val kd: Float,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class DistantLitSpecular(
        val direction: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class PointLitSpecular(
        val location: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class SpotLitSpecular(
        val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float,
        val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float,
        val input: ImageFilter? = null,
    ) : ImageFilter
    data class Offset(val dx: Float, val dy: Float, val input: ImageFilter? = null) : ImageFilter
    data class Tile(val src: Rect, val dst: Rect, val input: ImageFilter? = null) : ImageFilter
    data class Merge(val inputs: List<ImageFilter>) : ImageFilter
    data class DisplacementMap(
        val xChannelSelector: ColorChannel, val yChannelSelector: ColorChannel,
        val scale: Float, val displacement: ImageFilter, val input: ImageFilter? = null,
    ) : ImageFilter
    data class Picture(val picture: org.graphiks.kanvas.picture.Picture, val src: Rect? = null) : ImageFilter
    data class Magnifier(val src: Rect, val zoom: Float, val inset: Float, val input: ImageFilter? = null) : ImageFilter
    data class MatrixConvolution(
        val kernelSize: Size, val kernel: FloatArray, val gain: Float, val bias: Float,
        val kernelOffset: Point, val tileMode: TileMode, val convolveAlpha: Boolean,
        val input: ImageFilter? = null,
    ) : ImageFilter {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MatrixConvolution) return false
            return kernelSize == other.kernelSize && kernel.contentEquals(other.kernel) &&
                gain == other.gain && bias == other.bias && kernelOffset == other.kernelOffset &&
                tileMode == other.tileMode && convolveAlpha == other.convolveAlpha && input == other.input
        }
        override fun hashCode(): Int {
            var result = kernelSize.hashCode()
            result = 31 * result + kernel.contentHashCode()
            result = 31 * result + gain.hashCode()
            result = 31 * result + bias.hashCode()
            result = 31 * result + kernelOffset.hashCode()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + convolveAlpha.hashCode()
            result = 31 * result + (input?.hashCode() ?: 0)
            return result
        }
    }
    data class RuntimeEffect(
        val effect: org.graphiks.kanvas.pipeline.RuntimeEffect,
        val uniforms: org.graphiks.kanvas.pipeline.UniformBlock,
        val childShaderName: String? = null,
        val childImageFilters: Map<String, ImageFilter?> = emptyMap(),
    ) : ImageFilter
}
