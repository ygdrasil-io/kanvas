package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class GPUImageFilterPlanTest {
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
    ): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = Image.fromPixels(
            width = 4,
            height = 4,
            pixels = ByteArray(4 * 4 * 4),
            sourceId = "image-filter-plan",
        ),
        src = Rect(0f, 0f, 4f, 4f),
        dst = Rect(10f, 10f, 14f, 14f),
        paint = paint,
        transform = Matrix33.identity(),
        clip = clip,
    )

    private fun target(width: Int, height: Int): GPUTargetFacts =
        GPUTargetFacts(width = width, height = height, colorFormat = "bgra8unorm")
}
