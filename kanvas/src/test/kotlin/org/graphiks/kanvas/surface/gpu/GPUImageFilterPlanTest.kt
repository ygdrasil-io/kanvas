package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertIs

class GPUImageFilterPlanTest {
    @ParameterizedTest
    @MethodSource("unsupportedImageFilters")
    fun `draw image maps unsupported filters to stable refusal`(
        paint: Paint,
        expectedCode: String,
        transform: Matrix33,
        dst: Rect,
        clip: ClipStack,
        targetSize: Int,
    ) {
        val command = imageOp(paint, clip = clip, transform = transform, dst = dst).toImageRectCommand(
            GPUDrawCommandID(2), target(targetSize, targetSize),
        )

        assertEquals(expectedCode, assertIs<GPUImageFilterPlan.Refused>(command.imageFilterPlan).code)
    }

    @Test
    fun `zero sigma blur maps to identity without a blur plan`() {
        val command = imageOp(Paint(imageFilter = ImageFilter.Blur(0f, 0f))).toImageRectCommand(
            GPUDrawCommandID(3), target(64, 64),
        )

        assertEquals(GPUImageFilterPlan.Identity, command.imageFilterPlan)
    }

    @Test
    fun `draw image maps bounded clamp blur into a blur plan`() {
        val command = imageOp(
            paint = Paint(imageFilter = ImageFilter.Blur(2f, 3f, TileMode.CLAMP)),
        ).toImageRectCommand(GPUDrawCommandID(1), target(64, 64))

        val plan = assertIs<GPUImageFilterPlan.Blur>(command.imageFilterPlan)
        assertEquals(2f, plan.sigmaX)
        assertEquals(3f, plan.sigmaY)
        assertEquals(6, plan.haloX)
        assertEquals(9, plan.haloY)
        assertEquals(GPURect(4f, 1f, 20f, 23f), plan.outputBounds)
    }

    @Test
    fun `draw image refuses blur sigma beyond the bounded route`() {
        val command = imageOp(
            paint = Paint(imageFilter = ImageFilter.Blur(13f, 3f, TileMode.CLAMP)),
        ).toImageRectCommand(GPUDrawCommandID(2), target(64, 64))

        assertIs<GPUImageFilterPlan.Refused>(command.imageFilterPlan)
    }

    @Test
    fun `draw image clamps blur bounds to the device rect clip`() {
        val command = imageOp(
            paint = Paint(imageFilter = ImageFilter.Blur(2f, 3f, TileMode.CLAMP)),
            clip = ClipStack.DeviceRect(Rect(8f, 6f, 16f, 18f)),
        ).toImageRectCommand(GPUDrawCommandID(3), target(64, 64))

        val plan = assertIs<GPUImageFilterPlan.Blur>(command.imageFilterPlan)
        assertEquals(GPURect(8f, 6f, 16f, 18f), plan.outputBounds)
    }

    private fun imageOp(
        paint: Paint,
        clip: ClipStack = ClipStack.WideOpen,
        transform: Matrix33 = Matrix33.identity(),
        dst: Rect = Rect(10f, 10f, 14f, 14f),
    ): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = Image.fromPixels(
            width = 4,
            height = 4,
            pixels = ByteArray(4 * 4 * 4),
            sourceId = "image-filter-plan",
        ),
        src = Rect(0f, 0f, 4f, 4f),
        dst = dst,
        paint = paint,
        transform = transform,
        clip = clip,
    )

    private fun target(width: Int, height: Int): GPUTargetFacts =
        GPUTargetFacts(width = width, height = height, colorFormat = "bgra8unorm")

    companion object {
        @JvmStatic
        fun unsupportedImageFilters(): Stream<Arguments> {
            val identity = Matrix33.identity()
            val defaultDst = Rect(10f, 10f, 14f, 14f)
            fun case(
                paint: Paint,
                code: String,
                transform: Matrix33 = identity,
                dst: Rect = defaultDst,
                clip: ClipStack = ClipStack.WideOpen,
                targetSize: Int = 64,
            ) = Arguments.of(paint, code, transform, dst, clip, targetSize)

            val nestedBlur = ImageFilter.Blur(1f, 1f)
            val pictureRecorder = PictureRecorder()
            pictureRecorder.beginRecording(Rect(0f, 0f, 1f, 1f))
            val picture = pictureRecorder.finishRecordingAsPicture()
            val runtimeEffect = RuntimeEffect(
                id = "unsupported-image-filter",
                module = ShaderModule.fromSource("@fragment fn main() {}"),
                uniformLayout = UniformLayout(emptyList()),
                children = emptyList(),
            )
            val nonBlurFilters = listOf<ImageFilter>(
                ImageFilter.DropShadow(1f, 1f, 1f, 1f, Color.BLACK),
                ImageFilter.ColorFilter(ColorFilter.Matrix(FloatArray(20))),
                ImageFilter.Compose(nestedBlur, nestedBlur),
                ImageFilter.Blend(org.graphiks.kanvas.paint.BlendMode.SRC_OVER, nestedBlur, nestedBlur),
                ImageFilter.Dilate(1f, 1f),
                ImageFilter.Erode(1f, 1f),
                ImageFilter.DistantLitDiffuse(Point(1f, 1f), Color.WHITE, 1f, 1f),
                ImageFilter.PointLitDiffuse(Point(1f, 1f), Color.WHITE, 1f, 1f),
                ImageFilter.SpotLitDiffuse(Point(1f, 1f), Point(0f, 0f), 1f, 1f, Color.WHITE, 1f, 1f),
                ImageFilter.DistantLitSpecular(Point(1f, 1f), Color.WHITE, 1f, 1f, 1f),
                ImageFilter.PointLitSpecular(Point(1f, 1f), Color.WHITE, 1f, 1f, 1f),
                ImageFilter.SpotLitSpecular(Point(1f, 1f), Point(0f, 0f), 1f, 1f, Color.WHITE, 1f, 1f, 1f),
                ImageFilter.Offset(1f, 1f),
                ImageFilter.Tile(defaultDst, defaultDst),
                ImageFilter.Merge(listOf(nestedBlur)),
                ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 1f, nestedBlur),
                ImageFilter.Picture(picture),
                ImageFilter.Magnifier(defaultDst, 2f, 1f),
                ImageFilter.MatrixConvolution(Size(1f, 1f), floatArrayOf(1f), 1f, 0f, Point(0f, 0f), TileMode.CLAMP, true),
                ImageFilter.RuntimeEffect(runtimeEffect, UniformBlock.EMPTY),
            )

            return Stream.concat(
                Stream.of(
                    case(
                        Paint(
                            imageFilter = ImageFilter.Blur(1f, 1f, TileMode.REPEAT),
                            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                        ),
                        "unsupported.mask-filter.image",
                    ),
                    case(
                        Paint(imageFilter = ImageFilter.Blur(1f, 1f, input = nestedBlur)),
                        "unsupported.image-filter.blur.input",
                    ),
                    case(
                        Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.REPEAT)),
                        "unsupported.image-filter.blur.tile-mode",
                        transform = Matrix33.translate(1f, 1f),
                    ),
                    case(
                        Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.MIRROR)),
                        "unsupported.image-filter.blur.tile-mode",
                    ),
                    case(Paint(imageFilter = ImageFilter.Blur(-1f, 1f)), "unsupported.image-filter.blur.sigma"),
                    case(Paint(imageFilter = ImageFilter.Blur(Float.NaN, 1f)), "unsupported.image-filter.blur.sigma"),
                    case(Paint(imageFilter = ImageFilter.Blur(Float.POSITIVE_INFINITY, 1f)), "unsupported.image-filter.blur.sigma"),
                    case(Paint(imageFilter = ImageFilter.Blur(13f, 1f)), "unsupported.image-filter.blur.sigma"),
                    case(
                        Paint(imageFilter = ImageFilter.Blur(12f, 12f)),
                        "unsupported.image-filter.blur.intermediate-size",
                        dst = Rect(36f, 36f, 2013f, 2013f),
                        clip = ClipStack.DeviceRect(Rect(0f, 0f, 4096f, 4096f)),
                        targetSize = 4096,
                    ),
                    case(
                        Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
                        "unsupported.image-filter.blur.transform",
                        transform = Matrix33.translate(1f, 1f),
                    ),
                ),
                nonBlurFilters.stream().map { filter ->
                    case(Paint(imageFilter = filter), "unsupported.image-filter.image.kind")
                },
            )
        }
    }
}
