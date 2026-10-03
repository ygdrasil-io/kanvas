package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.drawLine
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.toImage

/** Port of Skia's child_sampling_rt runtime-shader scene through the existing registered child API. */
class ChildSamplingRTGm : SkiaGm {
    override val name = "child_sampling_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeNativeSourceImage()
        val effect = requireNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)) {
            "registered child-opacity runtime effect v1 is unavailable"
        }
        val child = Shader.WithLocalMatrix(
            Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR),
            Matrix3x3F32.scaling(10f, 10f),
        )
        val shader = effect.makeShader(
            UniformBlock { float1("alpha", 1f) },
            mapOf("child" to child),
        )
        canvas.drawRect(
            RectF32(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(shader = shader, antiAlias = false),
        )
    }

    private fun makeNativeSourceImage(): Image {
        val source = Surface(
            width = 100,
            height = 100,
            config = RenderConfig.DEFAULT.copy(compositionDomain = CompositionDomain.SRGB_ENCODED),
        )
        source.canvas().drawLine(
            0f,
            0f,
            100f,
            100f,
            Paint(
                color = ColorARGB.Red,
                antiAlias = true,
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            ),
        )
        val rendered = source.render()
        check(rendered.isClean && rendered.diagnostics.isEmpty && rendered.stats.opsRefused == 0) {
            "child_sampling_rt native source Surface failed: ${rendered.diagnostics.summary()} " +
                "stats=${rendered.stats}"
        }
        check(rendered.format == PixelFormat.RGBA8 && rendered.width == 100 && rendered.height == 100) {
            "child_sampling_rt native source Surface had unexpected output: " +
                "${rendered.format} ${rendered.width}x${rendered.height}"
        }
        return rendered.toImage("child_sampling_rt_native_source")
    }
}
