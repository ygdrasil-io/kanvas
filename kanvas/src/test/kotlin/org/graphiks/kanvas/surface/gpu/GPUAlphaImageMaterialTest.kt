package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GPUAlphaImageMaterialTest {
    private val halfAlpha = 0x80 / 255f
    private val alphaImage = Image.fromPixels(
        width = 2,
        height = 1,
        pixels = byteArrayOf(0x00, 0x80.toByte()),
        colorType = ColorType.ALPHA_8,
        sourceId = "alpha-mask",
    )

    @Test
    fun `alpha image shader uploads white mask pixels and carries paint tint`() {
        val paint = Paint(
            color = Color.fromRGBA(0f, 1f, 0f, 0.5f),
            shader = Shader.Image(alphaImage),
        )

        val material = paint.toMaterial() as GPUMaterialDescriptor.ImageDraw

        assertEquals(true, material.alphaOnly)
        assertEquals(0f, material.tintR, 0.001f)
        assertEquals(1f, material.tintG, 0.001f)
        assertEquals(0f, material.tintB, 0.001f)
        assertEquals(halfAlpha, material.tintA, 0.001f)
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x00,
                0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
            ),
            material.rgbaPixels,
        )
    }

    @Test
    fun `draw image command carries alpha image tint from paint`() {
        val paint = Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.5f))
        val op = DisplayOp.DrawImage(
            image = alphaImage,
            src = Rect(0f, 0f, 2f, 1f),
            dst = Rect(0f, 0f, 2f, 1f),
            paint = paint,
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )

        val command = op.toImageRectCommand(
            GPUDrawCommandID(7),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )
        val material = command.material as GPUMaterialDescriptor.ImageDraw

        assertEquals(true, material.alphaOnly)
        assertEquals(0f, material.tintR, 0.001f)
        assertEquals(1f, material.tintG, 0.001f)
        assertEquals(0f, material.tintB, 0.001f)
        assertEquals(halfAlpha, material.tintA, 0.001f)
    }

    @Test
    fun `image material with pixels is accepted for fill dispatch`() {
        val material = Paint(
            color = Color.WHITE,
            shader = Shader.Image(alphaImage),
        ).toMaterial()
        val op = DisplayOp.DrawRect(
            rect = Rect(0f, 0f, 2f, 1f),
            paint = Paint(shader = Shader.Image(alphaImage)),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )
        val command = op.toNormalizedCommand(
            GPUDrawCommandID(8),
            GPUTargetFacts(width = 16, height = 16, colorFormat = "bgra8unorm"),
        )

        assertEquals(GPUMaterialKind.ImageDraw, material.kind)
        assertEquals(null, command.fillGuardRefusalReasonOrNull())
    }
}
